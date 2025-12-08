package ts.realms.m2git.core.git.actions;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

import java.util.Iterator;

import ts.realms.m2git.R;
import ts.realms.m2git.common.errors.StopTaskException;
import ts.realms.m2git.core.git.RepoAction;
import ts.realms.m2git.core.git.SheimiAsyncTask.AsyncTaskPostCallback;
import ts.realms.m2git.core.git.tasks.UndoCommitTask;
import ts.realms.m2git.core.models.Repo;
import ts.realms.m2git.ui.screens.fragments.RepoDetailActivity;

public class UndoAction extends RepoAction {
    public UndoAction(Repo repo, RepoDetailActivity activity) {
        super(repo, activity);
    }

    @Override
    public void execute() {
        boolean firstCommit = true;
        boolean noCommit = true;
        try {
            Iterator<RevCommit> logIt = mRepo.getGit().log().call().iterator();
            noCommit = !logIt.hasNext();
            if (!noCommit) {
                logIt.next();
                firstCommit = !logIt.hasNext();
            }
        } catch (GitAPIException | StopTaskException e) {
            e.fillInStackTrace();
        }
        if (noCommit) {
            mActivity.showMessageDialog(R.string.dialog_undo_commit_title,
                R.string.dialog_undo_no_commit_msg);
        } else if (firstCommit) {
            mActivity.showMessageDialog(R.string.dialog_undo_commit_title,
                R.string.dialog_undo_first_commit_msg);
        } else {
            mActivity.showMessageDialog(R.string.dialog_undo_commit_title,
                R.string.dialog_undo_commit_msg, R.string.action_undo,
                (dialogInterface, i) -> undo());
        }
        mActivity.closeOperationDrawer();
    }

    public void undo() {
        UndoCommitTask undoTask = new UndoCommitTask(mRepo, new AsyncTaskPostCallback() {
            @Override
            public void onPostExecute(Boolean isSuccess) {
                mActivity.reset();
            }
        });
        undoTask.executeTask();
    }
}
