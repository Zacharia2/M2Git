package ts.realms.m2git.core.git.tasks;

import org.eclipse.jgit.lib.ObjectId;

import ts.realms.m2git.R;
import ts.realms.m2git.common.errors.StopTaskException;
import ts.realms.m2git.core.models.Repo;

public class CherryPickTask extends RepoOpTask {

    private final AsyncTaskPostCallback mCallback;
    public String mCommitStr;

    public CherryPickTask(Repo repo, String commit,
                          AsyncTaskPostCallback callback) {
        super(repo);
        mCommitStr = commit;
        mCallback = callback;
        setSuccessMsg(R.string.success_cherry_pick);
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        return cherrypick();
    }

    protected void onPostExecute(Boolean isSuccess) {
        super.onPostExecute(isSuccess);
        if (mCallback != null) {
            mCallback.onPostExecute(isSuccess);
        }
    }

    public boolean cherrypick() {
        try {
            ObjectId commit = mRepo.getGit().getRepository()
                .resolve(mCommitStr);
            mRepo.getGit().cherryPick().include(commit).call();
        } catch (StopTaskException e) {
            return false;
        } catch (Throwable e) {
            setException(e);
            return false;
        }
        return true;
    }
}
