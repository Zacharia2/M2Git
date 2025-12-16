package ts.realms.m2git.core.git.tasks.local;

import android.content.Context;

import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.lib.StoredConfig;

import ts.realms.m2git.MainApplication;
import ts.realms.m2git.R;
import ts.realms.m2git.common.errors.StopTaskException;
import ts.realms.m2git.core.git.tasks.MAsyncTask;
import ts.realms.m2git.core.git.tasks.RepoOpTask;
import ts.realms.m2git.core.models.Repo;
import ts.realms.m2git.local.preference.Profile;

public class CommitChangesTask extends RepoOpTask implements MAsyncTask.AsyncTaskDoCallback {

    private final AsyncTaskPostCallback mCallback;
    private final String mCommitMsg;
    private final String mAuthorName;
    private final String mAuthorEmail;
    private final boolean mIsAmend;
    private final boolean mStageAll;

    public CommitChangesTask(Repo repo, String commitMsg, boolean isAmend, boolean stageAll,
                             String authorName, String authorEmail,
                             AsyncTaskPostCallback callback) {
        super(repo);
        mCallback = callback;
        mCommitMsg = commitMsg;
        mIsAmend = isAmend;
        mStageAll = stageAll;
        mAuthorName = authorName;
        mAuthorEmail = authorEmail;
        setSuccessMsg(R.string.success_commit);
    }

    public static void commit(Repo repo, boolean stageAll, boolean isAmend, String msg,
                              String authorName, String authorEmail) throws Exception {
        Context context = MainApplication.getContext();
        StoredConfig config = repo.getGit().getRepository().getConfig();
        String committerEmail = config.getString("user", null, "email");
        String committerName = config.getString("user", null, "name");

        if (committerName == null || committerName.isEmpty()) {
            committerName = Profile.getUsername(context);
        }
        if (committerEmail == null || committerEmail.isEmpty()) {
            committerEmail = Profile.getEmail(context);
        }
        if (committerName.isEmpty() || committerEmail.isEmpty()) {
            throw new Exception("Please set your name and email");
        }
        if (msg.isEmpty()) {
            throw new Exception("Please include a commit message");
        }
        /*
         * Git().commit().setAll(stageAll)
         * 将所有已经被 Git 跟踪的文件（即那些之前已经被 git add 过的文件）的最新更改暂存并提交
         */
        if (stageAll) {
            repo.getGit().add().addFilepattern(".").setRenormalize(false).call();
            repo.getGit().add().setUpdate(true).addFilepattern(".").setRenormalize(false).call();
        }
        CommitCommand cc =
            repo.getGit().commit().setCommitter(committerName, committerEmail).setAmend(isAmend).setMessage(msg);
        if (authorName != null && authorEmail != null) {
            cc.setAuthor(authorName, authorEmail);
        }
        cc.call();
        repo.updateLatestCommitInfo();
    }

    @Override
    public boolean doInBackground(Void... params) {
        return commit();
    }

    @Override
    public void onPostExecute(Boolean isSuccess) {
        super.onPostExecute(isSuccess);
        if (mCallback != null) {
            mCallback.onPostExecute(isSuccess);
        }
    }

    public boolean commit() {
        try {
            commit(mRepo, mStageAll, mIsAmend, mCommitMsg, mAuthorName, mAuthorEmail);
        } catch (StopTaskException e) {
            return false;
        } catch (Throwable e) {
            setException(e);
            return false;
        }
        mRepo.updateLatestCommitInfo();
        return true;
    }
}
