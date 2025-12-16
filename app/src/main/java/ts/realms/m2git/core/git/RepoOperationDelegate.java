package ts.realms.m2git.core.git;

import org.eclipse.jgit.lib.Ref;

import java.io.File;
import java.util.ArrayList;

import ts.realms.m2git.core.git.actions.AddAllAction;
import ts.realms.m2git.core.git.actions.AddRemoteAction;
import ts.realms.m2git.core.git.actions.CherryPickAction;
import ts.realms.m2git.core.git.actions.CommitAction;
import ts.realms.m2git.core.git.actions.ConfigAction;
import ts.realms.m2git.core.git.actions.DiffAction;
import ts.realms.m2git.core.git.actions.FetchAction;
import ts.realms.m2git.core.git.actions.MergeAction;
import ts.realms.m2git.core.git.actions.NewBranchAction;
import ts.realms.m2git.core.git.actions.NewDirAction;
import ts.realms.m2git.core.git.actions.NewFileAction;
import ts.realms.m2git.core.git.actions.PullAction;
import ts.realms.m2git.core.git.actions.PushAction;
import ts.realms.m2git.core.git.actions.RawConfigAction;
import ts.realms.m2git.core.git.actions.RebaseAction;
import ts.realms.m2git.core.git.actions.RemoveRemoteAction;
import ts.realms.m2git.core.git.actions.ResetAction;
import ts.realms.m2git.core.git.actions.SyncRepoAction;
import ts.realms.m2git.core.git.actions.UndoAction;
import ts.realms.m2git.core.git.tasks.local.AddToStageTask;
import ts.realms.m2git.core.git.tasks.local.CheckoutFileTask;
import ts.realms.m2git.core.git.tasks.local.CheckoutTask;
import ts.realms.m2git.core.git.tasks.local.DeleteFileFromRepoTask;
import ts.realms.m2git.core.git.tasks.local.MergeTask;
import ts.realms.m2git.core.git.tasks.local.UpdateIndexTask;
import ts.realms.m2git.core.models.Repo;
import ts.realms.m2git.ui.screens.fragments.RepoDetailActivity;
import ts.realms.m2git.utils.FsUtils;

public class RepoOperationDelegate {
    private final Repo mRepo;
    private final RepoDetailActivity mActivity;
    private final ArrayList<RepoAction> mActions = new ArrayList<>();

    public RepoOperationDelegate(Repo repo, RepoDetailActivity activity) {
        mRepo = repo;
        mActivity = activity;
        initActions();
    }

    private void initActions() {
        mActions.add(new SyncRepoAction(mRepo, mActivity));
        mActions.add(new NewBranchAction(mRepo, mActivity));
        mActions.add(new PullAction(mRepo, mActivity));
        mActions.add(new PushAction(mRepo, mActivity));
        mActions.add(new AddAllAction(mRepo, mActivity));
        mActions.add(new CommitAction(mRepo, mActivity));
        mActions.add(new UndoAction(mRepo, mActivity));
        mActions.add(new ResetAction(mRepo, mActivity));
        mActions.add(new MergeAction(mRepo, mActivity));
        mActions.add(new FetchAction(mRepo, mActivity));
        mActions.add(new RebaseAction(mRepo, mActivity));
        mActions.add(new CherryPickAction(mRepo, mActivity));
        mActions.add(new DiffAction(mRepo, mActivity));
        mActions.add(new NewFileAction(mRepo, mActivity));
        mActions.add(new NewDirAction(mRepo, mActivity));
        mActions.add(new AddRemoteAction(mRepo, mActivity));
        mActions.add(new RemoveRemoteAction(mRepo, mActivity));
        mActions.add(new RawConfigAction(mRepo, mActivity));
        mActions.add(new ConfigAction(mRepo, mActivity));
    }

    public void executeAction(int key) {
        RepoAction action = mActions.get(key);
        if (action == null) return;
        action.execute();
    }

    public void checkoutCommit(final String commitName) {
        CheckoutTask checkoutTask = new CheckoutTask(mRepo, commitName, null,
            isSuccess -> mActivity.reset(commitName));
        checkoutTask.executeTask();
    }

    public void checkoutCommit(final String commitName, final String branch) {
        CheckoutTask checkoutTask = new CheckoutTask(mRepo, commitName, branch,
            isSuccess -> mActivity.reset(branch));
        checkoutTask.executeTask();
    }

    public void mergeBranch(final Ref commit, final String ffModeStr, final boolean autoCommit) {
        MergeTask mergeTask = new MergeTask(mRepo, commit, ffModeStr, autoCommit,
            isSuccess -> mActivity.reset());
        mergeTask.executeTask();
    }

    public void addToStage(String filepath) {
        String relative = getRelativePath(filepath);
        AddToStageTask addToStageTask = new AddToStageTask(mRepo, relative, null);
        addToStageTask.executeTask();
    }

    public void checkoutFile(String filepath) {
        String relative = getRelativePath(filepath);
        CheckoutFileTask task = new CheckoutFileTask(mRepo, relative, null);
        task.executeTask();
    }

    public void deleteFileFromRepo(String filepath,
                                   DeleteFileFromRepoTask.DeleteOperationType deleteOperationType) {
        String relative = getRelativePath(filepath);
        DeleteFileFromRepoTask task = new DeleteFileFromRepoTask(mRepo, relative,
            deleteOperationType, isSuccess -> {
            // TODO Auto-generated method stub
            mActivity.getFilesFragment().reset();
        });
        task.executeTask();
    }

    private String getRelativePath(String filepath) {
        File base = mRepo.getDir();
        return FsUtils.getRelativePath(new File(filepath), base);
    }


    public void updateIndex(final String mFilePath, final int newMode) {
        String relative = getRelativePath(mFilePath);
        UpdateIndexTask task = new UpdateIndexTask(mRepo, relative, newMode);
        task.executeTask();
    }
}
