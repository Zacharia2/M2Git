package ts.realms.m2git.core.command.tasks.remote;

import android.content.Context;

import androidx.annotation.StringRes;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.lib.ProgressMonitor;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Locale;

import timber.log.Timber;
import ts.realms.m2git.MainApplication;
import ts.realms.m2git.R;
import ts.realms.m2git.core.command.LfsAutoTool;
import ts.realms.m2git.core.command.MAsyncTask;
import ts.realms.m2git.core.command.tasks.RepoRemoteOpTask;
import ts.realms.m2git.core.models.Repo;
import ts.realms.m2git.core.network.ssh.SgitTransportCallback;
import ts.realms.m2git.local.database.RepoContract;
import ts.realms.m2git.local.database.RepoDbManager;
import ts.realms.m2git.local.preference.Profile;

public class CloneTask extends RepoRemoteOpTask implements MAsyncTask.AsyncTaskDoCallback {

    private final AsyncTaskCallback mCallback;
    private final boolean mCloneRecursive;
    private final String mCloneStatusName;

    public CloneTask(Repo repo, boolean cloneRecursive, String statusName,
                     AsyncTaskCallback callback) {
        super(repo);
        mCloneRecursive = cloneRecursive;
        mCloneStatusName = statusName;
        mCallback = callback;
    }

    @Override
    public boolean doInBackground(Void... v) {
        boolean result = cloneRepo();
        if (!result) {
            Timber.e("del repo. clone failed");
            mRepo.deleteRepoSync(true);
        } else if (mCallback != null) {
            result = mCallback.doInBackground(v);
        }
        return result;
    }

    @Override
    public void onPostExecute(Boolean isSuccess) {
        super.onPostExecute(isSuccess);
        if (isTaskCanceled()) return;
        if (isSuccess) {
            mRepo.updateLatestCommitInfo();
            mRepo.updateStatus(RepoContract.REPO_STATUS_NULL);

            long pendingId = mRepo.getPendingCredentialId();
            if (pendingId != -1) {
                RepoDbManager.relateRepoWithCredential(pendingId, String.valueOf(mRepo.getID()));
                mRepo.clearPendingCredential();
                mRepo.clearTempCredential();          // 已落库，下次走查表分支
            }
        }
    }

    public boolean cloneRepo() {
        File localRepo = mRepo.getDir();
        CloneCommand cloneCommand = Git.cloneRepository()
            .setURI(mRepo.getRemoteURL())
            .setCloneAllBranches(true)
            .setProgressMonitor(new RepoCloneMonitor())
            .setTransportConfigCallback(new SgitTransportCallback())
            .setDirectory(localRepo)
            .setCloneSubmodules(mCloneRecursive);
        setCredentials(cloneCommand);
        try {
            cloneCommand.call();
            mRepo.applyLfs();

            String worktree = mRepo.getDir().getPath();
            if (LfsAutoTool.hasLfs(worktree)) {
                Context ctx = MainApplication.getContext();
                mRepo.updateStatus(ctx.getString(R.string.lfs_downloading));
                Collection<Path> files = LfsAutoTool.downloadAll(worktree);
                Timber.i("共下载 " + files.size() + " 个 LFS 对象");

                // ---- LFS 检出：直接调用标准 checkout，触发 SmudgeFilter 替换工作区指针 ----
                mRepo.updateStatus(ctx.getString(R.string.lfs_checking_out));
                try (Git git = Git.open(localRepo)) {
                    git.checkout()
                        .setStartPoint("HEAD")
                        .setAllPaths(true)
                        .setForced(true)   // 克隆刚完成，仓库干净，强制重写无副作用
                        .call();
                }
                Timber.i("LFS 检出完成，工作区已替换为真实内容");
            }

            Profile.setLastCloneSuccess();
        } catch (InvalidRemoteException e) {
            setError(R.string.error_invalid_remote);
            Profile.setLastCloneFailed(mRepo);
            return false;
        } catch (TransportException e) {
            setException(e);
            Profile.setLastCloneFailed(mRepo);
            handleAuthError(this);
            return false;
        } catch (GitAPIException e) {
            setException(e, R.string.error_clone_failed);
            return false;
        } catch (JGitInternalException e) {
            // not supported when unsupported git remotehttp://asdgfkas URI
            if (e.getCause() instanceof NotSupportedException) {
                setError(R.string.error_invalid_remote);
            } else {
                setException(e);
            }
            return false;
        } catch (OutOfMemoryError e) {
            setException(e, R.string.error_out_of_memory);
            return false;
        } catch (Throwable e) {
            setException(e);
            return false;
        }
        return true;
    }

    @Override
    public void cancelTask() {
        super.cancelTask();
        mRepo.deleteRepo(true);
    }

    @Override
    public RepoRemoteOpTask getNewTask() {
        // need to call create repo again as when clone fails due auth error, the repo initially
        // created gets deleted
        String userName = mRepo.getUsername();
        String password = mRepo.getPassword();
        long pendingId = mRepo.getPendingCredentialId();

        mRepo = Repo.createRepo(mRepo.getLocalPath(), mRepo.getRemoteURL(), mCloneStatusName);
        if (pendingId != -1) {
            mRepo.setPendingCredential(pendingId, userName, password);
        } else {
            mRepo.setTempCredential(userName, password);   // 弹窗场景
        }
        return new CloneTask(mRepo, mCloneRecursive, mCloneStatusName, mCallback);
    }

    @Override
    @StringRes
    public int getErrorTitleRes() {
        return R.string.error_clone_failed;
    }

    public class RepoCloneMonitor implements ProgressMonitor {

        private int mTotalWork;
        private int mWorkDone;
        private int mLastProgress;
        private String mTitle;

        private void publishProgressInner() {
            String status = "";
            String percent = "";
            if (mTitle != null) {
                status = String.format(Locale.getDefault(), "%s ... ", mTitle);
                percent = "0%";
            }
            if (mTotalWork != 0) {
                int p = 100 * mWorkDone / mTotalWork;
                if (p - mLastProgress < 1) {
                    return;
                }
                mLastProgress = p;
                percent = String.format(Locale.getDefault(), "(%d%%)", p);
            }
            mRepo.updateStatus(status + percent);
        }

        @Override
        public void start(int totalTasks) {
            publishProgressInner();
        }

        @Override
        public void beginTask(String title, int totalWork) {
            mTotalWork = totalWork;
            mWorkDone = 0;
            mLastProgress = 0;
            mTitle = title;
            publishProgressInner();
        }

        @Override
        public void update(int i) {
            mWorkDone += i;
            publishProgressInner();
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

    }

}

