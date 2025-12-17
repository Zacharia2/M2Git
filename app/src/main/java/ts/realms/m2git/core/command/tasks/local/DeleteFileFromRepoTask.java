package ts.realms.m2git.core.command.tasks.local;

import java.io.File;

import ts.realms.m2git.R;
import ts.realms.m2git.ui.common.errors.StopTaskException;
import ts.realms.m2git.core.command.tasks.MAsyncTask;
import ts.realms.m2git.core.command.tasks.RepoOpTask;
import ts.realms.m2git.core.models.Repo;
import ts.realms.m2git.utils.FsUtils;

public class DeleteFileFromRepoTask extends RepoOpTask implements MAsyncTask.AsyncTaskDoCallback {

    private final DeleteOperationType mOperationType;
    public String mFilePattern;
    public AsyncTaskPostCallback mCallback;

    public DeleteFileFromRepoTask(Repo repo, String filePattern,
                                  DeleteOperationType deleteOperationType, AsyncTaskPostCallback callback) {
        super(repo);
        mFilePattern = filePattern;
        mCallback = callback;
        mOperationType = deleteOperationType;
        setSuccessMsg(R.string.success_remove_file);
    }

    @Override
    public boolean doInBackground(Void... params) {
        return removeFile();
    }

    @Override
    public void onPostExecute(Boolean isSuccess) {
        super.onPostExecute(isSuccess);
        if (mCallback != null) {
            mCallback.onPostExecute(isSuccess);
        }
    }

    public boolean removeFile() {
        try {
            switch (mOperationType) {
                case DELETE:
                    File fileToDelete = FsUtils.joinPath(mRepo.getDir(), mFilePattern);
                    FsUtils.deleteFile(fileToDelete);
                    break;
                case REMOVE_CACHED:
                    mRepo.getGit().rm().setCached(true).addFilepattern(mFilePattern).call();
                    break;
                case REMOVE_FORCE:
                    mRepo.getGit().rm().addFilepattern(mFilePattern).call();
                    break;
            }
        } catch (StopTaskException e) {
            return false;
        } catch (Throwable e) {
            setException(e);
            return false;
        }
        return true;
    }

    /**
     * Created by lee on 2015-01-30.
     */
    public enum DeleteOperationType {
        DELETE, REMOVE_CACHED, REMOVE_FORCE
    }
}
