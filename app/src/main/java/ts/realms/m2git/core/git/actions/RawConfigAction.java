package ts.realms.m2git.core.git.actions;

import android.content.Intent;

import ts.realms.m2git.core.git.RepoAction;
import ts.realms.m2git.core.models.Repo;
import ts.realms.m2git.ui.screens.fragments.ViewFileActivity;
import ts.realms.m2git.ui.screens.repoDetail.RepoDetailActivity;

/**
 * Created by phcoder on 05.12.15.
 */
public class RawConfigAction extends RepoAction {

    public RawConfigAction(Repo repo, RepoDetailActivity activity) {
        super(repo, activity);
    }

    @Override
    public void execute() {
        Intent intent = new Intent(mActivity, ViewFileActivity.class);
        intent.putExtra(ViewFileActivity.TAG_FILE_NAME, mRepo.getDir().getAbsoluteFile() + "/.git" +
            "/config");
        mActivity.startActivity(intent);
    }
}
