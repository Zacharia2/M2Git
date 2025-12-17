package ts.realms.m2git.core.command.tasks.local;

import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.revwalk.RevCommit;

import java.util.ArrayList;
import java.util.List;

import ts.realms.m2git.ui.common.errors.StopTaskException;
import ts.realms.m2git.core.command.MAsyncTask;
import ts.realms.m2git.core.command.tasks.RepoOpTask;
import ts.realms.m2git.core.models.Repo;

public class GetCommitTask extends RepoOpTask implements MAsyncTask.AsyncTaskDoCallback {

    private final GetCommitCallback mCallback;
    private final String mFile;
    private List<RevCommit> mResult;

    public GetCommitTask(Repo repo, String file, GetCommitCallback callback) {
        super(repo, true);
        mFile = file;
        mCallback = callback;
    }

    @Override
    public boolean doInBackground(Void... params) {
        return getCommitsList();
    }

    @Override
    public void onPostExecute(Boolean isSuccess) {
        super.onPostExecute(isSuccess);
        if (mCallback != null) {
            mCallback.postCommits(mResult);
        }
    }

    public boolean getCommitsList() {
        try {
            LogCommand cmd = mRepo.getGit().log();
            if (mFile != null)
                cmd.addPath(mFile);
            Iterable<RevCommit> commits = cmd.call();
            mResult = new ArrayList<RevCommit>();
            for (RevCommit commit : commits) {
                mResult.add(commit);
            }
        } catch (StopTaskException e) {
            return false;
        } catch (Throwable e) {
            setException(e);
            return false;
        }
        return true;
    }

    public interface GetCommitCallback {
        void postCommits(List<RevCommit> commits);
    }

}
