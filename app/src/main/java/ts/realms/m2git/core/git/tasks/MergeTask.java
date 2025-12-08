package ts.realms.m2git.core.git.tasks;

import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.lib.Ref;

import ts.realms.m2git.R;
import ts.realms.m2git.common.errors.StopTaskException;
import ts.realms.m2git.core.models.Repo;
import ts.realms.m2git.utils.BasicFunctions;

public class MergeTask extends RepoOpTask {

    private final AsyncTaskPostCallback mCallback;
    private final Ref mCommit;
    private final String mFFModeStr;
    private final boolean mAutoCommit;

    public MergeTask(Repo repo, Ref commit, String ffModeStr,
                     boolean autoCommit, AsyncTaskPostCallback callback) {
        super(repo);
        mCallback = callback;
        mCommit = commit;
        mFFModeStr = ffModeStr;
        mAutoCommit = autoCommit;

    }

    @Override
    protected Boolean doInBackground(Void... params) {
        return mergeBranch();
    }

    protected void onPostExecute(Boolean isSuccess) {
        super.onPostExecute(isSuccess);
        if (mCallback != null) {
            mCallback.onPostExecute(isSuccess);
        }
    }

    public boolean mergeBranch() {
        String[] stringArray = BasicFunctions.getActiveActivity()
            .getResources().getStringArray(R.array.merge_ff_type);
        MergeCommand.FastForwardMode ffMode = MergeCommand.FastForwardMode.FF;
        if (mFFModeStr.equals(stringArray[1])) {
            // FF Only
            ffMode = MergeCommand.FastForwardMode.FF_ONLY;
        } else if (mFFModeStr.equals(stringArray[2])) {
            // No FF
            ffMode = MergeCommand.FastForwardMode.NO_FF;
        }
        try {
            mRepo.getGit().merge().include(mCommit).setFastForward(ffMode)
                .call();
        } catch (StopTaskException e) {
            return false;
        } catch (Throwable e) {
            setException(e);
            return false;
        }
        if (mAutoCommit) {
            String b1 = mRepo.getBranchName();
            String b2 = mCommit.getName();
            String msg = null;
            if (b1 == null) {
                msg = String.format("Merge branch '%s'",
                    Repo.getCommitDisplayName(b2));
            } else {
                msg = String.format("Merge branch '%s' into %s",
                    Repo.getCommitDisplayName(b2),
                    Repo.getCommitDisplayName(b1));
            }
            try {
                CommitChangesTask.commit(mRepo, false, false, msg, null, null);
            } catch (StopTaskException e) {
                return false;
            } catch (Throwable e) {
                setException(e);
                return false;
            }
        }
        mRepo.updateLatestCommitInfo();
        return true;
    }
}
