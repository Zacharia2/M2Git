package ts.realms.m2git.core.git.actions;

import ts.realms.m2git.core.git.RepoAction;
import ts.realms.m2git.core.models.Repo;
import ts.realms.m2git.ui.screens.repoDetail.RepoDetailActivity;

public class DiffAction extends RepoAction {

    public DiffAction(Repo repo, RepoDetailActivity activity) {
        super(repo, activity);
    }

    @Override
    public void execute() {
        mActivity.enterDiffActionMode();
        mActivity.closeOperationDrawer();
    }
}
