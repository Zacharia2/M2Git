package ts.realms.m2git.core.git.actions;

import ts.realms.m2git.R;
import ts.realms.m2git.core.git.RepoAction;
import ts.realms.m2git.core.git.tasks.local.ResetCommitTask;
import ts.realms.m2git.core.models.Repo;
import ts.realms.m2git.ui.screens.fragments.RepoDetailActivity;

public class ResetAction extends RepoAction {

    public ResetAction(Repo repo, RepoDetailActivity activity) {
        super(repo, activity);
    }

    @Override
    public void execute() {
        mActivity.showMessageDialog(R.string.dialog_reset_commit_title,
            R.string.dialog_reset_commit_msg, R.string.action_reset,
            (dialogInterface, i) -> reset());
        mActivity.closeOperationDrawer();
    }

    public void reset() {
        ResetCommitTask resetTask = new ResetCommitTask(mRepo, isSuccess -> mActivity.reset());
        resetTask.executeTask();
    }
}
