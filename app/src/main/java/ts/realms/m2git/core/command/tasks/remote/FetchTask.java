package ts.realms.m2git.core.command.tasks.remote;

import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.FetchResult;

import ts.realms.m2git.R;
import ts.realms.m2git.ui.common.errors.StopTaskException;
import ts.realms.m2git.core.command.tasks.MAsyncTask;
import ts.realms.m2git.core.command.tasks.RepoRemoteOpTask;
import ts.realms.m2git.core.models.Repo;
import ts.realms.m2git.core.network.ssh.SgitTransportCallback;

public class FetchTask extends RepoRemoteOpTask implements MAsyncTask.AsyncTaskCallback {

    private final AsyncTaskCallback mCallback;
    private final String[] mRemotes;
    private FetchResult fetchResult;
    private FetchCallback mfetchCallback;

    public FetchTask(String[] remotes, Repo repo, AsyncTaskCallback callback) {
        super(repo);
        mCallback = callback;
        mRemotes = remotes;
    }

    public FetchTask(String[] remotes, Repo repo, AsyncTaskCallback callback,
                     FetchCallback fetchCallback) {
        super(repo, true);
        mCallback = callback;
        mfetchCallback = fetchCallback;
        mRemotes = remotes;
    }

    @Override
    public boolean doInBackground(Void... params) {
        boolean result = true;
        for (final String remote : mRemotes) {
            result = fetchRepo(remote) & result;
            if (mCallback != null) {
                result = mCallback.doInBackground(params) & result;
            }
        }
        return result;
    }

    @Override
    public void onProgressUpdate(String... progress) {
        if (mCallback != null) {
            mCallback.onProgressUpdate(progress);
        }
    }

    @Override
    public void onPreExecute() {
        if (mCallback != null) {
            mCallback.onPreExecute();
        }
    }

    @Override
    public void onPostExecute(Boolean isSuccess) {
        super.onPostExecute(isSuccess);
        if (mCallback != null) {
            mCallback.onPostExecute(isSuccess);
        }
        if (mfetchCallback != null) {
            mfetchCallback.onPostExecute(this.fetchResult);
        }
    }

    private boolean fetchRepo(String remote) {
        Git git;
        try {
            git = mRepo.getGit();
        } catch (StopTaskException e) {
            return false;
        }

        final FetchCommand fetchCommand = git.fetch()
            .setProgressMonitor(new BasicProgressMonitor())
            .setTransportConfigCallback(new SgitTransportCallback())
            .setRemote(remote);

        setCredentials(fetchCommand);

        try {
            fetchResult = fetchCommand.call();
        } catch (TransportException e) {
            setException(e);
            handleAuthError(this);
            return false;
        } catch (Exception e) {
            setException(e, R.string.error_pull_failed);
            return false;
        } catch (OutOfMemoryError e) {
            setException(e, R.string.error_out_of_memory);
            return false;
        } catch (Throwable e) {
            setException(e);
            return false;
        }
        mRepo.updateLatestCommitInfo();
        return true;
    }

    @Override
    public RepoRemoteOpTask getNewTask() {
        return new FetchTask(mRemotes, mRepo, mCallback);
    }

    public interface FetchCallback {
        void onPostExecute(FetchResult fetchResult);
    }
}
