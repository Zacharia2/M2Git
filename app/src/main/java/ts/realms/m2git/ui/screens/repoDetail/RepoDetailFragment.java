package ts.realms.m2git.ui.screens.repoDetail;

import ts.realms.m2git.ui.viewModels.BaseFragment;

public abstract class RepoDetailFragment extends BaseFragment {

    public RepoDetailActivity getRawActivity() {
        return (RepoDetailActivity) super.getRawActivity();
    }

}
