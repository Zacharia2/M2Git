package ts.realms.m2git.core.command.actions;

import ts.realms.m2git.core.command.RepoAction;
import ts.realms.m2git.core.command.tasks.local.AddToStageTask;
import ts.realms.m2git.core.models.Repo;
import ts.realms.m2git.ui.screens.repoDetail.RepoDetailActivity;

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
