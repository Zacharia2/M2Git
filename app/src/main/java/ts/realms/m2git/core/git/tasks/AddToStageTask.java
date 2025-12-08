package ts.realms.m2git.core.git.tasks;

import ts.realms.m2git.R;
import ts.realms.m2git.common.errors.StopTaskException;
import ts.realms.m2git.core.models.Repo;

public class AddToStageTask extends RepoOpTask {

    private final AsyncTaskPostCallback mCallback;
    public String mFilePattern;

    public AddToStageTask(Repo repo, String filePattern, AsyncTaskPostCallback callback) {
        super(repo);
        mFilePattern = filePattern;
        mCallback = callback;
        setSuccessMsg(R.string.success_add_to_stage);
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        return addToStage();
    }

    protected void onPostExecute(Boolean isSuccess) {
        super.onPostExecute(isSuccess);
        if (mCallback != null) {
            mCallback.onPostExecute(isSuccess);
        }
    }

    public boolean addToStage() {
        try {
            //   jGit hasn't a cmd direct add modified/new/deleted files, so if want to add
            //   those 3 types changed, need a combined call like below.
            //   check it: https://stackoverflow.com/a/59434085

            //add modified/new files
            mRepo.getGit().add().addFilepattern(mFilePattern).setRenormalize(false).call();
            //add modified/deleted files
            mRepo.getGit().add().setUpdate(true).addFilepattern(mFilePattern).setRenormalize(false).call();
        } catch (StopTaskException e) {
            return false;
        } catch (Throwable e) {
            setException(e);
            return false;
        }
        return true;
    }
}
