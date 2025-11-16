package xyz.realms.mgit.tasks.repo;

import org.eclipse.jgit.api.Git;

import xyz.realms.mgit.database.Repo;
import xyz.realms.mgit.database.RepoContract;

public class InitLocalTask extends RepoOpTask {

    public InitLocalTask(Repo repo) {
        super(repo);
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        boolean result = init();
        if (!result) {
            mRepo.deleteRepoSync(true);
            return false;
        }
        return true;
    }

    protected void onPostExecute(Boolean isSuccess) {
        super.onPostExecute(isSuccess);
        if (isSuccess) {
            mRepo.updateLatestCommitInfo();
            mRepo.updateStatus(RepoContract.REPO_STATUS_NULL);
            mRepo.applyLfs();
        }
    }

    public boolean init() {
        try {
            Git.init().setDirectory(mRepo.getDir()).call();
        } catch (Throwable e) {
            setException(e);
            return false;
        }
        return true;
    }
}
