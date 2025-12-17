package ts.realms.m2git.core.command.actions;

import ts.realms.m2git.R;
import ts.realms.m2git.core.command.RepoAction;
import ts.realms.m2git.core.models.Repo;
import ts.realms.m2git.ui.screens.repoDetail.RepoDetailActivity;

public class NewDirAction extends RepoAction {

    public NewDirAction(Repo repo, RepoDetailActivity activity) {
        super(repo, activity);
    }

    @Override
    public void execute() {
        mActivity.showEditTextDialog(R.string.dialog_create_dir_title,
            R.string.dialog_create_dir_hint, R.string.label_create,
            text -> mActivity.getFilesFragment().newDir(text));
        mActivity.closeOperationDrawer();
    }
}
