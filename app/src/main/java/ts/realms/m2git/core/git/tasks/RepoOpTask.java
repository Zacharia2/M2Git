package ts.realms.m2git.core.git.tasks;

import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import timber.log.Timber;
import ts.realms.m2git.R;
import ts.realms.m2git.core.git.SheimiAsyncTask;
import ts.realms.m2git.core.models.Repo;
import ts.realms.m2git.ui.screens.fragments.SheimiFragmentActivity.OnPasswordEntered;
import ts.realms.m2git.utils.BasicFunctions;

public abstract class RepoOpTask extends SheimiAsyncTask<Void, String, Boolean> {

    protected Repo mRepo;
    protected boolean mIsTaskAdded;
    private int mSuccessMsg = 0;
    private boolean mParallel = false;

    public RepoOpTask(Repo repo) {
        mRepo = repo;
        mIsTaskAdded = repo.addTask(this);
    }

    public RepoOpTask(Repo repo, boolean parallel) {
        mRepo = repo;
        // 有并行任务，也有串行任务。
        mIsTaskAdded = parallel;
        mParallel = parallel;
    }

    protected void onPostExecute(Boolean isSuccess) {
        super.onPostExecute(isSuccess);
        if (!mParallel) mRepo.removeTask(this);
        if (!isSuccess && !isTaskCanceled()) {
            if (mException == null) {
                BasicFunctions.showError(BasicFunctions.getActiveActivity(), mErrorRes,
                    getErrorTitleRes());
            } else {
                BasicFunctions.showException(BasicFunctions.getActiveActivity(), mException,
                    mErrorRes, getErrorTitleRes());
            }
        }
        if (isSuccess && mSuccessMsg != 0) {
            BasicFunctions.getActiveActivity().showToastMessage(mSuccessMsg);
        }
    }

    protected void setSuccessMsg(int successMsg) {
        mSuccessMsg = successMsg;
    }

    public void executeTask() {
        if (mIsTaskAdded) {
            execute();
            return;
        }
        BasicFunctions.getActiveActivity().showToastMessage(R.string.error_task_running);
    }

    protected void setCredentials(TransportCommand command) {
        String username = mRepo.getUsername();
        String password = mRepo.getPassword();

        if (username != null && password != null && !username.trim().isEmpty() && !password.trim().isEmpty()) {
            UsernamePasswordCredentialsProvider auth =
                new UsernamePasswordCredentialsProvider(username, password);
            command.setCredentialsProvider(auth);
        } else {
            Timber.d("no CredentialsProvider when no username/password provided");
        }

    }

    protected void handleAuthError(OnPasswordEntered onPassEntered) {
        String msg = mException.getMessage();
        Timber.w("clone Auth error: %s", msg);

        if (msg == null || ((!msg.contains("Auth fail")) && (!msg.toLowerCase().contains("auth")))) {
            return;
        }

        String errorInfo = null;
        if (msg.contains("Auth fail")) {
            errorInfo =
                BasicFunctions.getActiveActivity().getString(R.string.dialog_prompt_for_password_title_auth_fail);
        }
        BasicFunctions.getActiveActivity().promptForPassword(onPassEntered, errorInfo);
    }

    class BasicProgressMonitor implements ProgressMonitor {

        private int mTotalWork;
        private int mWorkDone;
        private int mLastProgress;
        private String mTitle;

        @Override
        public void start(int i) {
        }

        @Override
        public void beginTask(String title, int totalWork) {
            mTotalWork = totalWork;
            mWorkDone = 0;
            mLastProgress = 0;
            if (title != null) {
                mTitle = title;
            }
            setProgress();
        }

        @Override
        public void update(int i) {
            mWorkDone += i;
            if (mTotalWork != ProgressMonitor.UNKNOWN && mTotalWork != 0 && mTotalWork - mLastProgress >= 1) {
                setProgress();
                mLastProgress = mWorkDone;
            }
        }

        @Override
        public void endTask() {
        }

        @Override
        public boolean isCancelled() {
            return isTaskCanceled();
        }

        @Override
        public void showDuration(boolean enabled) {
        }

        private void setProgress() {
            String msg = mTitle;
            int showedWorkDown = Math.min(mWorkDone, mTotalWork);
            int progress = 0;
            String rightHint = "0/0";
            String leftHint = "0%";
            if (mTotalWork != 0) {
                progress = 100 * showedWorkDown / mTotalWork;
                rightHint = showedWorkDown + "/" + mTotalWork;
                leftHint = progress + "%";
            }
            publishProgress(msg, leftHint, rightHint, Integer.toString(progress));
        }

    }

}
