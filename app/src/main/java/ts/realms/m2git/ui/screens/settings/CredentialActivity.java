package ts.realms.m2git.ui.screens.settings;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import ts.realms.m2git.R;
import ts.realms.m2git.core.models.Credential;
import ts.realms.m2git.core.models.Repo;
import ts.realms.m2git.local.database.RepoContract;
import ts.realms.m2git.local.database.RepoDbManager;
import ts.realms.m2git.ui.screens.main.BaseCompatActivity;

/**
 * 凭证管理器。
 *
 * 功能：列表 / 新建 / 编辑 / 删除 / 关联仓库。
 * 数据变更由 RepoDbObserver 驱动，回调可能来自后台线程，统一切主线程。
 */
public class CredentialActivity extends BaseCompatActivity
        implements RepoDbManager.RepoDbObserver {

    /** 编辑时发现重名 account 的提示（应迁移到 strings.xml）。 */
    private static final String MSG_DUPLICATE_ACCOUNT = "该账户名已被其他凭证使用，请更换账户名";

    private final List<Credential> mCredentials = new ArrayList<>();
    private AccountAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_credential);

        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.setTitle(R.string.title_activity_credential_manager);
            bar.setDisplayHomeAsUpEnabled(true);
        }

        RecyclerView list = findViewById(R.id.accountList);
        list.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new AccountAdapter(mCredentials, new AccountAdapter.Listener() {
            @Override public void onEdit(Credential c)   { showEditDialog(c); }
            @Override public void onDelete(Credential c) { confirmDelete(c); }
        });
        list.setAdapter(mAdapter);

        findViewById(R.id.addAccountLayout).setOnClickListener(v -> showEditDialog(null));
    }

    @Override
    protected void onStart() {
        super.onStart();
        RepoDbManager.registerDbObserver(RepoContract.RepoCredential.TABLE_NAME, this);
        reload();
    }

    @Override
    protected void onStop() {
        super.onStop();
        RepoDbManager.unregisterDbObserver(RepoContract.RepoCredential.TABLE_NAME, this);
    }

    @Override
    public void notifyChanged() {
        // 通知可能来自后台线程（Repo.updateLatestCommitInfo 等），统一切主线程；
        // 同时避免 Activity 已销毁时仍然操作 Adapter。
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            reload();
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ============================================================ 列表

    private void reload() {
        mCredentials.clear();
        Cursor cursor = RepoDbManager.queryAllCredential();
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    do { mCredentials.add(new Credential(cursor)); }
                    while (cursor.moveToNext());
                }
            } finally {
                cursor.close();
            }
        }
        mAdapter.notifyDataSetChanged();
    }

    // ============================================================ 新建/编辑

    private void showEditDialog(Credential existing) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_account, null);
        EditText editAccount = view.findViewById(R.id.editAccount);
        EditText editSecret  = view.findViewById(R.id.editSecret);
        TextView relSummary  = view.findViewById(R.id.textRelSummary);
        View     relRow      = view.findViewById(R.id.layoutRelRepo);

        final Set<String> selected = new HashSet<>();
        if (existing != null) {
            editAccount.setText(existing.getTokenAccount());
            // 编辑时不回填明文 secret；留空表示不修改
            editSecret.setText("");
            editSecret.setHint(R.string.credential_secret_keep_hint);
            String[] rel = existing.getRelRepoId();
            if (rel != null) selected.addAll(Arrays.asList(rel));
        }
        refreshRelSummary(relSummary, selected);

        relRow.setOnClickListener(v -> showPickReposDialog(selected,
            () -> refreshRelSummary(relSummary, selected)));

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(existing == null
                ? R.string.dialog_credential_new_title
                : R.string.dialog_credential_edit_title)
            .setView(view)
            .setPositiveButton(R.string.label_save, null)
            .setNegativeButton(R.string.label_cancel, null)
            .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(v -> {
                String account = editAccount.getText().toString().trim();
                String secret  = editSecret.getText().toString().trim();
                if (account.isEmpty()) {
                    showToastMessage(R.string.credential_account_required);
                    return;
                }
                if (existing == null) {
                    long sameAccountId = RepoDbManager.findCredentialIdByAccount(account);
                    if (sameAccountId != -1) {
                        confirmOverwrite(dialog, sameAccountId, account, secret, selected);
                        return;
                    }
                } else {
                    long sameAccountId = RepoDbManager.findCredentialIdByAccount(account);
                    if (sameAccountId != -1 && sameAccountId != existing.getID()) {
                        showToastMessage(R.string.credential_account_duplicate);
                        return;
                    }
                    // if (secret.isEmpty()) {
                    //     secret = existing.getTokenSecret();
                    // }
                }
                saveCredential(existing, account, secret, selected);
                dialog.dismiss();
            }));
        dialog.show();
    }

    /**
     * 新建时发现同名 account 的覆盖确认。
     * 覆盖语义：更新 secret，并将本次勾选的关联仓库与旧关联取并集（不删除旧关联）。
     */
    private void confirmOverwrite(AlertDialog editDialog, long existingId,
                                String account, String secret, Set<String> repoIds) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_credential_overwrite_title)
            .setMessage(getString(R.string.dialog_credential_overwrite_msg, account))
            .setPositiveButton(R.string.label_overwrite, (d, w) -> {
                String merged = String.join(",", repoIds);
                // secret 留空表示“只合并关联，不动密码”，传 null 而非 ""
                String newSecret = (secret == null || secret.isEmpty()) ? null : secret;
                RepoDbManager.overwriteCredential(existingId, newSecret, merged);
                editDialog.dismiss();
            })
            .setNegativeButton(R.string.label_cancel, null)
            .show();
    }

    private void saveCredential(Credential existing, String account, String secret,
                                Set<String> repoIds) {
        if (existing == null) {
            // 新建：secret 允许为空（公开仓库场景），产品如需禁止可在此加校验
            long cid = RepoDbManager.createCredential(account, secret);
            if (cid == -1) {
                showToastMessage(R.string.credential_save_failed);
                return;
            }
            // 一次性写入关联，避免逐个 relate 触发多次 notify
            RepoDbManager.setCredentialRepos(cid, repoIds);
        // CredentialActivity.saveCredential 编辑分支
        } else {
            LinkedHashSet<String> ordered = new LinkedHashSet<>(repoIds);
            ContentValues values = new ContentValues();
            values.put(RepoContract.RepoCredential.COLUMN_TOKEN_ACCOUNT, account);
            boolean secretChanged = false;
            if (secret != null && !secret.isEmpty()) {
                values.put(RepoContract.RepoCredential.COLUMN_TOKEN_SECRET, secret);
                secretChanged = true;
            }
            values.put(RepoContract.RepoCredential.COLUMN_REL_REPO, String.join(",", ordered));
            try {
                RepoDbManager.updateCredential(existing.getID(), values);
            } catch (android.database.sqlite.SQLiteConstraintException e) {
                showToastMessage(R.string.credential_account_duplicate);
                return;
            }
            // 同步 repo 表冗余字段
            Set<String> oldSet = new HashSet<>();
            if (existing.getRelRepoId() != null) oldSet.addAll(Arrays.asList(existing.getRelRepoId()));

            // 被移除关联的 repo：清空冗余凭证
            for (String rid : oldSet) {
                if (!ordered.contains(rid)) {
                    RepoDbManager.clearRepoCredential(Long.parseLong(rid));
                }
            }
            // 仍然关联的 repo：刷新为新 account / secret
            String effectiveSecret = secretChanged ? secret : existing.getTokenSecret();
            for (String rid : ordered) {
                RepoDbManager.setRepoCredential(Long.parseLong(rid), account, effectiveSecret);
            }
        }
    }

    private void refreshRelSummary(TextView tv, Set<String> selected) {
        if (selected.isEmpty()) {
            tv.setText(R.string.credential_no_related_repos);
        } else {
            tv.setText(getString(R.string.credential_related_count, selected.size()));
        }
    }

    // ============================================================ 仓库多选

    private void showPickReposDialog(Set<String> selected, Runnable onDone) {
        Cursor cursor = RepoDbManager.queryAllRepo();
        if (cursor == null) return;
        final List<Repo> repos;
        try {
            repos = Repo.getRepoList(cursor);
        } finally {
            cursor.close();
        }

        if (repos.isEmpty()) {
            showToastMessage(R.string.credential_no_repos);
            return;
        }

        // 用 draft 副本接收勾选，取消时不影响原 selected
        final Set<String> draft = new HashSet<>(selected);
        CharSequence[] labels = new CharSequence[repos.size()];
        boolean[] checked = new boolean[repos.size()];
        for (int i = 0; i < repos.size(); i++) {
            Repo r = repos.get(i);
            String id = String.valueOf(r.getID());
            labels[i] = r.getDisplayName() + "  (#" + id + ")";
            checked[i] = draft.contains(id);
        }

        new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_credential_pick_repos_title)
            .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> {
                String id = String.valueOf(repos.get(which).getID());
                if (isChecked) draft.add(id); else draft.remove(id);
            })
            .setPositiveButton(R.string.label_ok, (d, w) -> {
                selected.clear();
                selected.addAll(draft);
                if (onDone != null) onDone.run();
            })
            .setNegativeButton(R.string.label_cancel, null)
            .show();
    }

    // ============================================================ 删除

    private void confirmDelete(Credential c) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_credential_delete_title)
            .setMessage(getString(R.string.dialog_credential_delete_msg, c.getTokenAccount()))
            .setPositiveButton(R.string.label_delete,
                (d, w) -> RepoDbManager.deleteCredential(c.getID()))
            .setNegativeButton(R.string.label_cancel, null)
            .show();
    }
}