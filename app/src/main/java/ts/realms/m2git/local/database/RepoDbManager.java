package ts.realms.m2git.local.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import timber.log.Timber;
import ts.realms.m2git.utils.BasicFunctions;

/**
 * Manage entries in the persisted database tracking local repo metadata.
 * 管理持久化数据库中 跟踪本地仓库元数据 的条目，以及仓库关联的凭证。
 */
public class RepoDbManager {

    private static final Map<String, Set<RepoDbObserver>> mObservers = new HashMap<>();
    private static RepoDbManager mInstance;
    private final SQLiteDatabase mWritableDB;
    private final SQLiteDatabase mReadableDB;


    private RepoDbManager(Context context) {
        RepoDbHelper mDbHelper = new RepoDbHelper(context);
        mWritableDB = mDbHelper.getWritableDatabase();
        mReadableDB = mDbHelper.getReadableDatabase();
    }

    private static RepoDbManager getInstance() {
        if (mInstance == null) {
            mInstance = new RepoDbManager(BasicFunctions.getActiveActivity());
        }
        return mInstance;
    }

    public static void registerDbObserver(String table, RepoDbObserver observer) {
        Set<RepoDbObserver> set = mObservers.computeIfAbsent(table, k -> new HashSet<>());
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
        for (RepoDbObserver observer : set) {
            observer.notifyChanged();
        }
    }

    public static void persistCredentials(long repoId, String username, String password) {
        ContentValues values = new ContentValues();
        if (username != null && password != null) {
            values.put(RepoContract.RepoEntry.COLUMN_NAME_USERNAME, username);
            values.put(RepoContract.RepoEntry.COLUMN_NAME_PASSWORD, password);
            relateRepoWithCredential(createCredential(username, password), String.valueOf(repoId));
        } else {
            values.put(RepoContract.RepoEntry.COLUMN_NAME_USERNAME, "");
            values.put(RepoContract.RepoEntry.COLUMN_NAME_PASSWORD, "");
        }
        updateRepo(repoId, values);
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

    //    我应该在设置中的安全中创建一个选项，《凭证管理器》
    //    然后点击后弹出一个小窗口，里面可以添加、删除、关联
    //    每当仓库需要的时候，先去检查凭证管理器是否有相关的凭证
    //    如果没有相关的凭证就可以新建一个凭证，并将其置放到凭证管理器中。
    //    凭证管理器需要的组件：一个弹出式的窗口、列表（有关联仓库）、修改是点击列表条目然后弹出新的窗口
    //    然后修改tokens、secret以及关联仓库。
    public static long createCredential(String token_account, String token_secret) {
        long credentialId = -1;
        try (Cursor cursor = getInstance()._queryAllCredential()) {
            if (cursor != null) {
                cursor.moveToFirst();
                while (!cursor.isAfterLast()) {
                    int columnTokenAccountIndex = cursor.getColumnIndex(RepoContract.RepoCredential.COLUMN_TOKEN_ACCOUNT);
                    int columnCredentialIdIndex = cursor.getColumnIndex(RepoContract.RepoCredential._ID);
                    String curr_token_account = cursor.getString(columnTokenAccountIndex);
                    if (token_account.equals(curr_token_account)) {
                        credentialId = cursor.getInt(columnCredentialIdIndex);
                        break;
                    }
                    cursor.moveToNext();
                }
            }
            if (credentialId == -1) {
                ContentValues values = new ContentValues();
                values.put(RepoContract.RepoCredential.COLUMN_TOKEN_ACCOUNT, token_account);
                values.put(RepoContract.RepoCredential.COLUMN_TOKEN_SECRET, token_secret);
                credentialId = getInstance()
                    .mWritableDB
                    .insert(RepoContract.RepoCredential.TABLE_NAME, null, values);
            }
        } catch (Exception e) {
            e.fillInStackTrace();
        }
        return credentialId;
    }

    public static void relateRepoWithCredential(long credentialId, String repo) {
        Cursor credential = getCredentialById(credentialId);
        if (credential == null || !credential.moveToFirst()) {
            return; // Credential not found or cursor is empty
        }
        int columnIndex = credential.getColumnIndex(RepoContract.RepoCredential.COLUMN_REL_REPO);
        String relRepoString = credential.getString(columnIndex);
        String new_rel;
        ContentValues values = new ContentValues();
        if (relRepoString == null || relRepoString.isEmpty()) {
            new_rel = repo;
        } else {
            HashSet<String> rel_list = new HashSet<>(Arrays.asList(relRepoString.split(",")));
            rel_list.add(repo);
            new_rel = String.join(",", rel_list);
        }
        values.put(RepoContract.RepoCredential.COLUMN_REL_REPO, new_rel);
        updateCredential(credentialId, values);
    }

    public static void unrelateRepoWithCredential(long id, String repo) {
        Cursor credential = getCredentialById(id);
        if (credential == null || !credential.moveToFirst()) {
            return; // Credential not found or cursor is empty
        }
        int columnIndex = credential.getColumnIndex(RepoContract.RepoCredential.COLUMN_REL_REPO);
        String relRepoString = credential.getString(columnIndex);
        if (relRepoString == null) return;
        HashSet<String> rel_list = new HashSet<>(Arrays.asList(relRepoString.split(",")));
        if (!rel_list.contains(repo)) return;
        rel_list.remove(repo);
        String new_rel = String.join(",", rel_list);
        ContentValues values = new ContentValues();
        values.put(RepoContract.RepoCredential.COLUMN_REL_REPO, new_rel);
        updateCredential(id, values);
    }

    public static void updateCredential(long id, ContentValues values) {
        String whereClause = RepoContract.RepoCredential._ID + " = ?";
        String[] whereArgs = {String.valueOf(id)};
        getInstance().mWritableDB.update(RepoContract.RepoCredential.TABLE_NAME, values,
            whereClause, whereArgs);
    }

    public static Cursor getCredentialById(long id) {
        return getInstance()._getCredentialById(id);
    }

    public static Cursor queryAllCredential() {
        return getInstance()._queryAllCredential();
    }

    public static void deleteCredential(long id) {
        getInstance()._deleteCredential(id);
    }

    public static Map<String, String> queryCredentialByRepoId(String repo_id) {
        try (Cursor cursor = RepoDbManager.queryAllCredential()) {
            Map<String, String> queryResult = Collections.emptyMap();
            if (cursor != null && cursor.moveToFirst()) {
                // 循环遍历所有行
                int rel_repoIndex = cursor.getColumnIndex(RepoContract.RepoCredential.COLUMN_REL_REPO);
                int secretIndex = cursor.getColumnIndex(RepoContract.RepoCredential.COLUMN_TOKEN_SECRET);
                int accountIndex = cursor.getColumnIndex(RepoContract.RepoCredential.COLUMN_TOKEN_ACCOUNT);
                do {
                    String rel_repo = cursor.getString(rel_repoIndex);
                    String[] rel_repo_list = rel_repo.split(",");
                    if (Arrays.asList(rel_repo_list).contains(repo_id)) {
                        queryResult = Map.of(
                            RepoContract.RepoCredential.COLUMN_TOKEN_SECRET, cursor.getString(secretIndex),
                            RepoContract.RepoCredential.COLUMN_TOKEN_ACCOUNT, cursor.getString(accountIndex)
                        );
                        break;
                    }

                } while (cursor.moveToNext()); // 移动到下一行，如果成功则继续循环
            } else {
                Timber.tag("DB").d("No credentials found.");
            }
            return queryResult;
        }
    }

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
        String whereClause = RepoContract.RepoEntry._ID + " = ?";
        String[] whereArgs = {String.valueOf(id)};
        mWritableDB.delete(RepoContract.RepoEntry.TABLE_NAME, whereClause, whereArgs);
        notifyObservers(RepoContract.RepoEntry.TABLE_NAME);
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

    private void _deleteCredential(long id) {
        String whereClause = RepoContract.RepoCredential._ID + " = ?";
        String[] whereArgs = {String.valueOf(id)};
        mWritableDB.delete(RepoContract.RepoCredential.TABLE_NAME, whereClause, whereArgs);
    }

    public interface RepoDbObserver {
        void notifyChanged();
    }

}
