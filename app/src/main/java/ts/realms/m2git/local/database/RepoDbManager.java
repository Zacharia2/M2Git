package ts.realms.m2git.local.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import timber.log.Timber;
import ts.realms.m2git.MainApplication;
import ts.realms.m2git.core.models.Credential;
import ts.realms.m2git.utils.BasicFunctions;

/**
 * Manage entries in the persisted database tracking local repo metadata.
 * 管理持久化数据库中 跟踪本地仓库元数据 的条目，以及仓库关联的凭证。
 * <p>
 * 并发说明：
 * - DB_LOCK 保护“凭证 / 仓库”的复合读-改-写序列（查重+插入、读关联+合并+写回），
 * 避免多线程丢失更新。SQLiteDatabase 自身线程安全，但复合序列不原子。
 * - 观察者注册表使用 ConcurrentHashMap + CopyOnWriteArraySet。
 */
public class RepoDbManager {

    private static final Map<String, Set<RepoDbObserver>> mObservers = new ConcurrentHashMap<>();
    /**
     * 保护复合读-改-写序列；可重入，所有加锁路径只使用这一把锁，无死锁风险。
     */
    private static final Object DB_LOCK = new Object();
    private static RepoDbManager mInstance;
    private final SQLiteDatabase mWritableDB;
    private final SQLiteDatabase mReadableDB;

    private RepoDbManager(Context context) {
        RepoDbHelper mDbHelper = new RepoDbHelper(context);
        mWritableDB = mDbHelper.getWritableDatabase();
        mReadableDB = mDbHelper.getReadableDatabase();
    }

    private static synchronized RepoDbManager getInstance() {
        if (mInstance == null) {
            Context ctx = BasicFunctions.getActiveActivity();
            if (ctx == null) {
                // 后台服务（WebDAV / Clone Task）首次调用时 active activity 可能为空，
                // 用 Application context 兜底
                ctx = MainApplication.getContext();
            }
            mInstance = new RepoDbManager(ctx);
        }
        return mInstance;
    }

    // ============================================================ 观察者

    public static void registerDbObserver(String table, RepoDbObserver observer) {
        Set<RepoDbObserver> set = mObservers.computeIfAbsent(
            table, k -> new CopyOnWriteArraySet<>());
        set.add(observer);
    }

    public static void unregisterDbObserver(String table, RepoDbObserver observer) {
        Set<RepoDbObserver> set = mObservers.get(table);
        if (set == null) return;
        set.remove(observer);
    }

    public static void notifyObservers(String table) {
        Set<RepoDbObserver> set = mObservers.get(table);
        if (set == null) return;
        // 可能从后台线程触发，观察者需自行切线程
        for (RepoDbObserver observer : set) {
            try {
                observer.notifyChanged();
            } catch (Exception e) {
                Timber.tag("DB").e(e, "observer notify failed: %s", observer.getClass());
            }
        }
    }

    // ============================================================ 仓库 + 凭证

    /**
     * 将 repo 关联到指定凭证（替换语义）。空凭证 = 解除关联。
     * 唯一持久数据源为 credentials 表，不写 repo 表冗余列。
     */
    public static void persistCredentials(long repoId, String username, String password) {
        synchronized (DB_LOCK) {
            _unrelateRepoFromAllCredentials(String.valueOf(repoId));
            if (username == null || password == null
                || username.trim().isEmpty() || password.trim().isEmpty()) {
                return;
            }
            long credentialId = createCredential(username, password);
            if (credentialId != -1) {
                overwriteCredentialSecret(credentialId, password);
                relateRepoWithCredential(credentialId, String.valueOf(repoId));
            }
        }
    }

    /**
     * 清理凭证 rel_repo 中指向已不存在仓库的悬空 id（防御性维护，打开凭证管理器时调用）。
     */
    public static void pruneOrphanCredentialRelations() {
        synchronized (DB_LOCK) {
            // 收集现存仓库 id
            Set<String> validIds = new HashSet<>();
            try (Cursor c = getInstance()._queryAllRepo()) {
                if (c == null) return;
                int idIndex = c.getColumnIndex(RepoContract.RepoEntry._ID);
                while (c.moveToNext()) {
                    validIds.add(String.valueOf(c.getLong(idIndex)));
                }
            }
            // 逐条过滤 rel_repo（先收集后批量写，与 _unrelate 同套路）
            Map<Long, String> updatesMap = new LinkedHashMap<>();
            try (Cursor c = getInstance()._queryAllCredential()) {
                if (c == null) return;
                int idIndex = c.getColumnIndex(RepoContract.RepoCredential._ID);
                int relIndex = c.getColumnIndex(RepoContract.RepoCredential.COLUMN_REL_REPO);
                while (c.moveToNext()) {
                    String rel = c.getString(relIndex);
                    if (rel == null || rel.isEmpty()) continue;
                    LinkedHashSet<String> kept = new LinkedHashSet<>();
                    boolean changed = false;
                    for (String s : rel.split(",")) {
                        if (s.trim().isEmpty()) {
                            changed = true;
                            continue;
                        }
                        if (validIds.contains(s.trim())) {
                            kept.add(s.trim());
                        } else {
                            changed = true;
                        }
                    }
                    if (changed) {
                        updatesMap.put(c.getLong(idIndex), String.join(",", kept));
                    }
                }
            }
            for (Map.Entry<Long, String> e : updatesMap.entrySet()) {
                ContentValues values = new ContentValues();
                values.put(RepoContract.RepoCredential.COLUMN_REL_REPO, e.getValue());
                getInstance().mWritableDB.update(
                    RepoContract.RepoCredential.TABLE_NAME, values,
                    RepoContract.RepoCredential._ID + " = ?",
                    new String[]{String.valueOf(e.getKey())});
            }
            if (!updatesMap.isEmpty()) {
                notifyObservers(RepoContract.RepoCredential.TABLE_NAME);
            }
        }
    }

    /**
     * 取全部凭证列表（凭证选择弹窗用）。
     */
    public static List<Credential> getAllCredentials() {
        List<Credential> list = new ArrayList<>();
        try (Cursor c = queryAllCredential()) {
            if (c != null) {
                while (c.moveToNext()) {
                    list.add(new Credential(c));
                }
            }
        }
        return list;
    }

    public static Cursor searchRepo(String query) {
        return getInstance()._searchRepo(query);
    }

    public static Cursor queryAllRepo() {
        return getInstance()._queryAllRepo();
    }

    public static Cursor getRepoById(long id) {
        return getInstance()._getRepoById(id);
    }

    public static long importRepo(String localPath, String status) {
        return createRepo(localPath, "", status);
    }

    public static void setLocalPath(long repoId, String path) {
        ContentValues values = new ContentValues();
        values.put(RepoContract.RepoEntry.COLUMN_NAME_LOCAL_PATH, path);
        updateRepo(repoId, values);
    }

    public static long createRepo(String localPath, String remoteURL, String status) {
        ContentValues values = new ContentValues();
        values.put(RepoContract.RepoEntry.COLUMN_NAME_LOCAL_PATH, localPath);
        values.put(RepoContract.RepoEntry.COLUMN_NAME_REMOTE_URL, remoteURL);
        values.put(RepoContract.RepoEntry.COLUMN_NAME_REPO_STATUS, status);
        long id = getInstance().mWritableDB.insert(RepoContract.RepoEntry.TABLE_NAME, null, values);
        notifyObservers(RepoContract.RepoEntry.TABLE_NAME);
        return id;
    }

    public static void updateRepo(long id, ContentValues values) {
        String whereClause = RepoContract.RepoEntry._ID + " = ?";
        String[] whereArgs = {String.valueOf(id)};
        getInstance().mWritableDB.update(RepoContract.RepoEntry.TABLE_NAME, values, whereClause,
            whereArgs);
        notifyObservers(RepoContract.RepoEntry.TABLE_NAME);
    }

    public static void deleteRepo(long id) {
        getInstance()._deleteRepo(id);
    }

    // ============================================================ 凭证

    /**
     * 按 account 查找已存在的凭证 id。
     * 用于“新建凭证”前检查同名冲突；UI 层应在返回值 != -1 时弹确认框。
     *
     * @return 已存在凭证的 id；不存在返回 -1。
     */
    public static long findCredentialIdByAccount(String token_account) {
        if (token_account == null) {
            return -1;
        }
        try (Cursor cursor = getInstance()._queryAllCredential()) {
            if (cursor != null && cursor.moveToFirst()) {
                int accountIndex = cursor.getColumnIndex(
                    RepoContract.RepoCredential.COLUMN_TOKEN_ACCOUNT);
                int idIndex = cursor.getColumnIndex(RepoContract.RepoCredential._ID);
                if (accountIndex < 0 || idIndex < 0) {
                    Timber.tag("DB").e("findCredentialIdByAccount: column missing");
                    return -1;
                }
                do {
                    if (token_account.equals(cursor.getString(accountIndex))) {
                        return cursor.getLong(idIndex);
                    }
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Timber.tag("DB").e(e, "findCredentialIdByAccount failed");
        }
        return -1;
    }

    /**
     * 覆盖已有凭证：更新 secret，并可选地将新关联仓库与旧关联取并集。
     * 关联仓库只会合并，不会删除旧关联（取并集）。
     *
     * @param credentialId        已存在的凭证 id
     * @param newSecret           新 secret；传 null 表示不更新 secret
     * @param relatedReposToMerge 需要与旧关联取并集的新仓库 id（逗号分隔）；
     *                            传 null 或空字符串表示不改动关联仓库
     */
    public static void overwriteCredential(long credentialId, String newSecret,
                                           String relatedReposToMerge) {
        synchronized (DB_LOCK) {
            ContentValues values = new ContentValues();
            if (newSecret != null && !newSecret.isEmpty()) {
                values.put(RepoContract.RepoCredential.COLUMN_TOKEN_SECRET, newSecret);
            }
            if (relatedReposToMerge != null && !relatedReposToMerge.isEmpty()) {
                for (String rid : relatedReposToMerge.split(",")) {
                    if (!rid.isEmpty()) _unrelateRepoFromAllCredentials(rid, credentialId);
                }
                try (Cursor credential = getCredentialById(credentialId)) {
                    if (credential != null && credential.moveToFirst()) {
                        int columnIndex = credential.getColumnIndex(
                            RepoContract.RepoCredential.COLUMN_REL_REPO);
                        if (columnIndex >= 0) {
                            String existing = credential.getString(columnIndex);
                            // LinkedHashSet 保证顺序稳定，便于调试与去重
                            LinkedHashSet<String> merged = new LinkedHashSet<>();
                            if (existing != null && !existing.isEmpty()) {
                                for (String rid : existing.split(",")) {
                                    if (!rid.isEmpty()) merged.add(rid);
                                }
                            }
                            for (String rid : relatedReposToMerge.split(",")) {
                                if (!rid.isEmpty()) merged.add(rid);
                            }
                            values.put(RepoContract.RepoCredential.COLUMN_REL_REPO,
                                String.join(",", merged));
                        }
                    }
                }
            }
            if (values.size() > 0) {
                updateCredential(credentialId, values);
            }
        }
    }

    /**
     * 从所有凭证的 rel_repo 中移除指定 repoId。
     *
     * @param repoId   要解除关联的仓库 id（字符串形式）
     * @param exceptId 跳过此凭证 id，不修改它的 rel_repo；传 -1 表示不排除任何凭证。
     *                 调用方必须持有 DB_LOCK。
     */
    private static void _unrelateRepoFromAllCredentials(String repoId, long exceptId) {
        RepoDbManager instance = getInstance();
        // 先收集，再批量更新：避免边遍历 Cursor 边改同一张表
        Map<Long, String> updates = new LinkedHashMap<>();
        try (Cursor cursor = instance._queryAllCredential()) {
            if (cursor == null || !cursor.moveToFirst()) return;
            int idIndex = cursor.getColumnIndex(RepoContract.RepoCredential._ID);
            int relIndex = cursor.getColumnIndex(RepoContract.RepoCredential.COLUMN_REL_REPO);
            if (idIndex < 0 || relIndex < 0) return;
            do {
                long cid = cursor.getLong(idIndex);
                if (cid == exceptId) continue;
                String rel = cursor.getString(relIndex);
                if (rel == null || rel.isEmpty()) continue;
                boolean contains = false;
                LinkedHashSet<String> remaining = new LinkedHashSet<>();
                for (String s : rel.split(",")) {
                    if (s.isEmpty()) continue;
                    if (repoId.equals(s)) {
                        contains = true;
                    } else {
                        remaining.add(s);
                    }
                }
                if (contains) {
                    updates.put(cid, String.join(",", remaining));
                }
            } while (cursor.moveToNext());
        }
        for (Map.Entry<Long, String> e : updates.entrySet()) {
            ContentValues values = new ContentValues();
            values.put(RepoContract.RepoCredential.COLUMN_REL_REPO, e.getValue());
            instance.mWritableDB.update(RepoContract.RepoCredential.TABLE_NAME, values,
                RepoContract.RepoCredential._ID + " = ?",
                new String[]{String.valueOf(e.getKey())});
        }
    }

    /**
     * 无排除版本：从所有凭证中移除 repoId 的关联。
     */
    private static void _unrelateRepoFromAllCredentials(String repoId) {
        _unrelateRepoFromAllCredentials(repoId, -1);
    }

    /**
     * 覆盖已有凭证的 secret，关联仓库保持不变。
     */
    public static void overwriteCredentialSecret(long credentialId, String newSecret) {
        overwriteCredential(credentialId, newSecret, null);
    }

    /**
     * 一次性设置凭证关联的仓库集合（替换语义），内部只触发一次 notify。
     * 修复：加入 DB_LOCK，避免与 relate/unrelate 的复合读-改-写并发丢失更新。
     */
    public static void setCredentialRepos(long credentialId, Set<String> repoIds) {
        synchronized (DB_LOCK) {
            LinkedHashSet<String> ordered = new LinkedHashSet<>();
            if (repoIds != null) {
                for (String rid : repoIds) {
                    if (rid != null && !rid.isEmpty()) ordered.add(rid);
                }
            }
            ContentValues values = new ContentValues();
            values.put(RepoContract.RepoCredential.COLUMN_REL_REPO, String.join(",", ordered));
            updateCredential(credentialId, values);
        }
    }

    /**
     * 创建凭证。
     * <p>
     * 新建前 UI 应先调用 {@link #findCredentialIdByAccount(String)} 检查同名冲突，
     * 若存在则弹确认框，由用户决定是否覆盖：
     * - 用户选择覆盖：调用 {@link #overwriteCredential(long, String, String)}；
     * - 用户选择取消：不调用本方法。
     * <p>
     * 本方法保留查重作为兜底：若已存在同名 account，返回旧 id，不更新 secret。
     * 注意：查重+插入在 DB_LOCK 内完成，避免并发重复插入。
     * 修复：notifyObservers 移到 synchronized 块外。
     * 注意：若由 persistCredentials 调用，外层仍持有 DB_LOCK，此时 notify 仍在锁内；
     * 但观察者均通过 runOnUiThread 异步切主线程，不会阻塞持锁线程，无死锁风险。
     */
    public static long createCredential(String token_account, String token_secret) {
        long credentialId;
        boolean created = false;
        synchronized (DB_LOCK) {
            credentialId = findCredentialIdByAccount(token_account);
            if (credentialId == -1) {
                ContentValues values = new ContentValues();
                values.put(RepoContract.RepoCredential.COLUMN_TOKEN_ACCOUNT, token_account);
                values.put(RepoContract.RepoCredential.COLUMN_TOKEN_SECRET, token_secret);
                credentialId = getInstance()
                    .mWritableDB
                    .insert(RepoContract.RepoCredential.TABLE_NAME, null, values);
                created = true;
            }
        }
        if (created) {
            notifyObservers(RepoContract.RepoCredential.TABLE_NAME);
        }
        return credentialId;
    }

    public static void relateRepoWithCredential(long credentialId, String repo) {
        synchronized (DB_LOCK) {
            try (Cursor credential = getCredentialById(credentialId)) {
                if (credential == null || !credential.moveToFirst()) {
                    return;
                }
                int columnIndex = credential.getColumnIndex(
                    RepoContract.RepoCredential.COLUMN_REL_REPO);
                if (columnIndex < 0) return;
                String relRepoString = credential.getString(columnIndex);
                LinkedHashSet<String> rel_list = new LinkedHashSet<>();
                if (relRepoString != null && !relRepoString.isEmpty()) {
                    for (String s : relRepoString.split(",")) {
                        if (!s.isEmpty()) rel_list.add(s);
                    }
                }
                rel_list.add(repo);
                ContentValues values = new ContentValues();
                values.put(RepoContract.RepoCredential.COLUMN_REL_REPO,
                    String.join(",", rel_list));
                updateCredential(credentialId, values);
            }
        }
    }

    public static void unrelateRepoWithCredential(long id, String repo) {
        synchronized (DB_LOCK) {
            try (Cursor credential = getCredentialById(id)) {
                if (credential == null || !credential.moveToFirst()) {
                    return;
                }
                int columnIndex = credential.getColumnIndex(
                    RepoContract.RepoCredential.COLUMN_REL_REPO);
                if (columnIndex < 0) return;
                String relRepoString = credential.getString(columnIndex);
                if (relRepoString == null || relRepoString.isEmpty()) return;
                LinkedHashSet<String> rel_list = new LinkedHashSet<>();
                for (String s : relRepoString.split(",")) {
                    if (!s.isEmpty()) rel_list.add(s);
                }
                if (!rel_list.contains(repo)) return;
                rel_list.remove(repo);
                ContentValues values = new ContentValues();
                values.put(RepoContract.RepoCredential.COLUMN_REL_REPO,
                    String.join(",", rel_list));
                updateCredential(id, values);
            }
        }
    }

    public static void updateCredential(long id, ContentValues values) {
        String whereClause = RepoContract.RepoCredential._ID + " = ?";
        String[] whereArgs = {String.valueOf(id)};
        getInstance().mWritableDB.update(RepoContract.RepoCredential.TABLE_NAME, values,
            whereClause, whereArgs);
        notifyObservers(RepoContract.RepoCredential.TABLE_NAME);
    }

    public static Cursor getCredentialById(long id) {
        return getInstance()._getCredentialById(id);
    }

    public static Cursor queryAllCredential() {
        return getInstance()._queryAllCredential();
    }

    public static void deleteCredential(long id) {
        // _deleteCredential 内部负责 notify 两个表
        getInstance()._deleteCredential(id);
    }

    /**
     * 按 repoId 反查关联的凭证。
     * 当前数据模型下一个 repo 只对应一个凭证，因此返回第一条匹配即可；
     * 若后续改为多凭证，需要在此显式定义优先级。
     * 修复：新建后未关联的凭证 rel_repo 为 NULL，直接 split 会 NPE。
     */
    public static Map<String, String> queryCredentialByRepoId(String repo_id) {
        try (Cursor cursor = RepoDbManager.queryAllCredential()) {
            if (cursor != null && cursor.moveToFirst()) {
                int rel_repoIndex = cursor.getColumnIndex(
                    RepoContract.RepoCredential.COLUMN_REL_REPO);
                int secretIndex = cursor.getColumnIndex(
                    RepoContract.RepoCredential.COLUMN_TOKEN_SECRET);
                int accountIndex = cursor.getColumnIndex(
                    RepoContract.RepoCredential.COLUMN_TOKEN_ACCOUNT);
                if (rel_repoIndex < 0 || secretIndex < 0 || accountIndex < 0) {
                    Timber.tag("DB").e("queryCredentialByRepoId: column missing");
                    return Collections.emptyMap();
                }
                do {
                    String rel_repo = cursor.getString(rel_repoIndex);
                    if (rel_repo == null || rel_repo.isEmpty()) continue;
                    for (String rid : rel_repo.split(",")) {
                        if (repo_id.equals(rid)) {
                            Map<String, String> result = new HashMap<>();
                            result.put(RepoContract.RepoCredential.COLUMN_TOKEN_SECRET,
                                cursor.getString(secretIndex));
                            result.put(RepoContract.RepoCredential.COLUMN_TOKEN_ACCOUNT,
                                cursor.getString(accountIndex));
                            return result;
                        }
                    }
                } while (cursor.moveToNext());
            } else {
                Timber.tag("DB").d("No credentials found.");
            }
            return Collections.emptyMap();
        }
    }

    // ============================================================ 内部查询

    private Cursor _searchRepo(String query) {
        String whereClause =
            RepoContract.RepoEntry.COLUMN_NAME_LOCAL_PATH
                + " LIKE ? OR " + RepoContract.RepoEntry.COLUMN_NAME_REMOTE_URL
                + " LIKE ? OR " + RepoContract.RepoEntry.COLUMN_NAME_LATEST_COMMITTER_UNAME
                + " LIKE ? OR " + RepoContract.RepoEntry.COLUMN_NAME_LATEST_COMMIT_MSG
                + " LIKE ?";
        query = "%" + query + "%";
        String[] whereArgs = {query, query, query, query};
        return mReadableDB.query(true, RepoContract.RepoEntry.TABLE_NAME,
            RepoContract.RepoEntry.ALL_COLUMNS, whereClause, whereArgs, null, null, null, null);
    }

    private Cursor _queryAllRepo() {
        return mReadableDB.query(true, RepoContract.RepoEntry.TABLE_NAME,
            RepoContract.RepoEntry.ALL_COLUMNS, null, null, null, null, null, null);
    }

    private Cursor _getRepoById(long id) {
        String whereClause = RepoContract.RepoEntry._ID + " = ?";
        String[] whereArgs = {String.valueOf(id)};
        Cursor cursor = mReadableDB.query(true, RepoContract.RepoEntry.TABLE_NAME,
            RepoContract.RepoEntry.ALL_COLUMNS, whereClause, whereArgs, null, null, null, null);
        if (cursor.getCount() < 1) {
            cursor.close();
            return null;
        }
        return cursor;
    }

    private void _deleteRepo(long id) {
        synchronized (DB_LOCK) {
            String whereClause = RepoContract.RepoEntry._ID + " = ?";
            String[] whereArgs = {String.valueOf(id)};
            mWritableDB.delete(RepoContract.RepoEntry.TABLE_NAME, whereClause, whereArgs);
            // 修复：从所有凭证的 rel_repo 中移除该 repoId，避免悬空引用与计数虚高。
            _unrelateRepoFromAllCredentials(String.valueOf(id));
        }
        notifyObservers(RepoContract.RepoEntry.TABLE_NAME);
        notifyObservers(RepoContract.RepoCredential.TABLE_NAME);
    }

    private Cursor _getCredentialById(long id) {
        String whereClause = RepoContract.RepoCredential._ID + " = ?";
        String[] whereArgs = {String.valueOf(id)};
        Cursor cursor = mReadableDB.query(true, RepoContract.RepoCredential.TABLE_NAME,
            RepoContract.RepoCredential.ALL_COLUMNS, whereClause, whereArgs, null, null, null,
            null);
        if (cursor.getCount() < 1) {
            cursor.close();
            return null;
        }
        return cursor;
    }

    private Cursor _queryAllCredential() {
        return mReadableDB.query(true, RepoContract.RepoCredential.TABLE_NAME,
            RepoContract.RepoCredential.ALL_COLUMNS, null, null, null, null, null, null);
    }

    /**
     * 删除凭证。仓库的关联数据源就在 credentials 表，删除后关联自然失效，无需额外清理。
     */
    private void _deleteCredential(long id) {
        synchronized (DB_LOCK) {
            mWritableDB.delete(RepoContract.RepoCredential.TABLE_NAME,
                RepoContract.RepoCredential._ID + " = ?",
                new String[]{String.valueOf(id)});
        }
        notifyObservers(RepoContract.RepoCredential.TABLE_NAME);
    }

    public interface RepoDbObserver {
        void notifyChanged();
    }
}
