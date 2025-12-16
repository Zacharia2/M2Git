package ts.realms.m2git.core.git.actions;

import ts.realms.m2git.R;
import ts.realms.m2git.core.git.RepoAction;
import ts.realms.m2git.core.git.tasks.local.CherryPickTask;
import ts.realms.m2git.core.models.Repo;
import ts.realms.m2git.ui.screens.fragments.RepoDetailActivity;

public class CherryPickAction extends RepoAction {

    public CherryPickAction(Repo repo, RepoDetailActivity activity) {
        super(repo, activity);
    }

    @Override
    public void execute() {
        mActivity.showEditTextDialog(R.string.dialog_cherrypick_title,
            R.string.dialog_cherrypick_msg_hint, R.string.dialog_label_cherrypick,
            this::cherrypick);
        mActivity.closeOperationDrawer();
    }

    public void cherrypick(String commit) {
        CherryPickTask task = new CherryPickTask(mRepo, commit, isSuccess -> mActivity.reset());
        task.executeTask();
    }

}
