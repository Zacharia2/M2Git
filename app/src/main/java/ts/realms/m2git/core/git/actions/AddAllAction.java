package ts.realms.m2git.core.git.actions;

import ts.realms.m2git.core.git.RepoAction;
import ts.realms.m2git.core.git.tasks.local.AddToStageTask;
import ts.realms.m2git.core.models.Repo;
import ts.realms.m2git.ui.screens.fragments.RepoDetailActivity;

public class AddAllAction extends RepoAction {

    public AddAllAction(Repo repo, RepoDetailActivity activity) {
        super(repo, activity);
    }

    @Override
    public void execute() {
        AddToStageTask addTask = new AddToStageTask(mRepo, ".", null);
        mActivity.showToastMessage("正在暂存全部。");
        addTask.executeTask();
        mActivity.closeOperationDrawer();
    }

}
