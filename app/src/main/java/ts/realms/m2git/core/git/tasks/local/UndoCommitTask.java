package ts.realms.m2git.core.git.tasks.local;

import org.eclipse.jgit.api.ResetCommand;

import ts.realms.m2git.R;
import ts.realms.m2git.common.errors.StopTaskException;
import ts.realms.m2git.core.git.tasks.MAsyncTask;
import ts.realms.m2git.core.git.tasks.RepoOpTask;
import ts.realms.m2git.core.models.Repo;

public class UndoCommitTask extends RepoOpTask implements MAsyncTask.AsyncTaskDoCallback {

    private final AsyncTaskPostCallback mCallback;

    public UndoCommitTask(Repo repo, AsyncTaskPostCallback callback) {
        super(repo);
        mCallback = callback;
        setSuccessMsg(R.string.success_undo);
    }

    @Override
    public boolean doInBackground(Void... params) {
        return undo();
    }

    @Override
    public void onPostExecute(Boolean isSuccess) {
        super.onPostExecute(isSuccess);
        if (mCallback != null) {
            mCallback.onPostExecute(isSuccess);
        }
    }

    public boolean undo() {
        try {
            mRepo.getGit().getRepository().writeMergeCommitMsg(null);
            mRepo.getGit().getRepository().writeMergeHeads(null);
            mRepo.getGit().reset().setRef("HEAD~").setMode(ResetCommand.ResetType.SOFT).call();
        } catch (StopTaskException e) {
            return false;
        } catch (Throwable e) {
            setException(e);
            return false;
        }
        return true;
    }
}
