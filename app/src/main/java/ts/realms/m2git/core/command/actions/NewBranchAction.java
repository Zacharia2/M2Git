package ts.realms.m2git.core.command.actions;

import ts.realms.m2git.R;
import ts.realms.m2git.core.command.RepoAction;
import ts.realms.m2git.core.command.tasks.MAsyncTask;
import ts.realms.m2git.core.command.tasks.local.CheckoutTask;
import ts.realms.m2git.core.models.Repo;
import ts.realms.m2git.ui.screens.repoDetail.RepoDetailActivity;

/**
 * Created by liscju - piotr.listkiewicz@gmail.com on 2015-03-15.
 */
public class NewBranchAction extends RepoAction {
    public NewBranchAction(Repo mRepo, RepoDetailActivity mActivity) {
        super(mRepo, mActivity);
    }

    @Override
    public void execute() {
        mActivity.showEditTextDialog(R.string.dialog_create_branch_title,
            R.string.dialog_create_branch_hint, R.string.label_create, branchName -> {
                CheckoutTask checkoutTask = new CheckoutTask(mRepo, null, branchName,
                    new ActivityResetPostCallback(branchName));
                checkoutTask.executeTask();
            });
        mActivity.closeOperationDrawer();
    }

    private class ActivityResetPostCallback implements MAsyncTask.AsyncTaskPostCallback {
        private final String mBranchName;

        public ActivityResetPostCallback(String branchName) {
            mBranchName = branchName;
        }

        @Override
        public void onPostExecute(Boolean isSuccess) {
            mActivity.reset(mBranchName);
        }
    }
}
