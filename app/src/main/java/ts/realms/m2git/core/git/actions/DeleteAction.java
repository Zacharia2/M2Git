package ts.realms.m2git.core.git.actions;

import ts.realms.m2git.R;
import ts.realms.m2git.core.git.RepoAction;
import ts.realms.m2git.core.models.Repo;
import ts.realms.m2git.ui.screens.repoDetail.RepoDetailActivity;

public class DeleteAction extends RepoAction {

    public DeleteAction(Repo repo, RepoDetailActivity activity) {
        super(repo, activity);
    }

    @Override
    public void execute() {
        mActivity.showMessageDialog(R.string.dialog_delete_repo_title,
            R.string.dialog_delete_repo_msg, R.string.label_delete, (dialogInterface, i) -> {
                mRepo.deleteRepo(true);
                mActivity.finish();
            });
        mActivity.closeOperationDrawer();
    }
}
