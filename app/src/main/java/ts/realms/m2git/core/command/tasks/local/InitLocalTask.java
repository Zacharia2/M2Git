package ts.realms.m2git.core.command.tasks.local;

import org.eclipse.jgit.api.Git;

import ts.realms.m2git.core.command.MAsyncTask;
import ts.realms.m2git.core.command.tasks.RepoOpTask;
import ts.realms.m2git.core.models.Repo;
import ts.realms.m2git.local.database.RepoContract;

public class InitLocalTask extends RepoOpTask implements MAsyncTask.AsyncTaskDoCallback {

    public InitLocalTask(Repo repo) {
        super(repo);
    }

    @Override
    public boolean doInBackground(Void... params) {
        boolean result = init();
        if (!result) {
            mRepo.deleteRepoSync(true);
            return false;
        }
        return true;
    }

    @Override
    public void onPostExecute(Boolean isSuccess) {
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
