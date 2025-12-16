package ts.realms.m2git.core.git.tasks.local;

import ts.realms.m2git.R;
import ts.realms.m2git.common.errors.StopTaskException;
import ts.realms.m2git.core.git.tasks.MAsyncTask;
import ts.realms.m2git.core.git.tasks.RepoOpTask;
import ts.realms.m2git.core.models.Repo;

public class RebaseTask extends RepoOpTask implements MAsyncTask.AsyncTaskDoCallback {

    private final AsyncTaskPostCallback mCallback;
    public String mUpstream;

    public RebaseTask(Repo repo, String upstream, AsyncTaskPostCallback callback) {
        super(repo);
        mUpstream = upstream;
        mCallback = callback;
        setSuccessMsg(R.string.success_rebase);
    }

    @Override
    public boolean doInBackground(Void... params) {
        return rebase();
    }

    @Override
    public void onPostExecute(Boolean isSuccess) {
        super.onPostExecute(isSuccess);
        if (mCallback != null) {
            mCallback.onPostExecute(isSuccess);
        }
    }

    public boolean rebase() {
        try {
            mRepo.getGit().rebase().setUpstream(mUpstream).call();
        } catch (StopTaskException e) {
            return false;
        } catch (Throwable e) {
            setException(e);
            return false;
        }
        return true;
    }
}
