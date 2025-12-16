package ts.realms.m2git.core.git;

import ts.realms.m2git.core.models.Repo;
import ts.realms.m2git.ui.screens.repoDetail.RepoDetailActivity;

public abstract class RepoAction {

    protected Repo mRepo;
    protected RepoDetailActivity mActivity;

    public RepoAction(Repo repo, RepoDetailActivity activity) {
        mRepo = repo;
        mActivity = activity;
    }

    public abstract void execute();
}
