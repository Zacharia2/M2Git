package ts.realms.m2git.core.command.tasks;

import ts.realms.m2git.R;
import ts.realms.m2git.core.models.Credential;
import ts.realms.m2git.core.models.Repo;
import ts.realms.m2git.ui.screens.main.BaseCompatActivity;
import ts.realms.m2git.utils.BasicFunctions;

/**
 * Super class for Tasks that operate on a git remote.
 * 认证统一走凭证管理器：重试弹窗为凭证选择器（选已有凭证 / 手动输入）。
 */
public abstract class RepoRemoteOpTask extends RepoOpTask
    implements BaseCompatActivity.OnCredentialEntered {

    public RepoRemoteOpTask(Repo repo) {
        super(repo);
        mRepo.setToken();
    }

    public RepoRemoteOpTask(Repo repo, boolean parallel) {
        super(repo, parallel);
        mRepo.setToken();
    }

    @Override
    public void onCredentialPicked(Credential c) {
        // id 用于克隆成功后落库关联；account/secret 立即生效
        mRepo.setPendingCredential(c.getID(), c.getTokenAccount(), c.getTokenSecret());
        mRepo.removeTask(this);
        getNewTask().executeTask();
    }

    @Override
    public void onManualEntered(String username, String password, boolean save) {
        if (username.trim().isEmpty() || password.trim().isEmpty()) {
            BasicFunctions.getActiveActivity()
                .showToastMessage(R.string.credential_input_required);
            return;  // 不重试；Task 的失败清理已由 doInBackground 兜底
        }
        mRepo.clearPendingCredential();  // 手动输入取代之前选的凭证，废弃 pending 关联
        mRepo.setTempCredential(username, password);
        mRepo.removeTask(this);
        RepoRemoteOpTask newTask = getNewTask();  // CloneTask 会把 temp 凭证带到新 repo
        if (save) {
            // 关键：对"活着的"新 repo 落库，确保 rel_repo 写的是有效 id
            newTask.mRepo.saveCredentials();
        }
        newTask.executeTask();
    }

    @Override
    public void onCanceled() {
        // 用户取消，什么都不做（CloneTask 的失败清理逻辑已在 doInBackground 兜底）
    }

    public abstract RepoRemoteOpTask getNewTask();
}
