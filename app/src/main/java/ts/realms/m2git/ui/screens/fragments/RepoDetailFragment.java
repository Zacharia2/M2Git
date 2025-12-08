package ts.realms.m2git.ui.screens.fragments;

import ts.realms.m2git.ui.viewmodels.BaseFragment;

public abstract class RepoDetailFragment extends BaseFragment {

    public RepoDetailActivity getRawActivity() {
        return (RepoDetailActivity) super.getRawActivity();
    }

}
