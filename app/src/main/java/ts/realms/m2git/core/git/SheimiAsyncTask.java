package ts.realms.m2git.core.git;

import android.os.AsyncTask;

import androidx.annotation.StringRes;

import timber.log.Timber;
import ts.realms.m2git.R;
import ts.realms.m2git.ui.components.dialogs.ErrorDialog;

public abstract class SheimiAsyncTask<Params, Progress, Result> extends AsyncTask<Params, Progress, Result> {

    protected Throwable mException;
    protected int mErrorRes = 0;
    private boolean mIsCanceled = false;

    protected void setException(Throwable e) {
        Timber.e(e, "set exception");
        mException = e;
    }

    protected void setException(Throwable e, int errorRes) {
        Timber.e(e, "set error [%d] exception", errorRes);
        mException = e;
        mErrorRes = errorRes;
    }

    protected void setError(int errorRes) {
        Timber.e("set error res id: %d", errorRes);
        mErrorRes = errorRes;
    }

    public void cancelTask() {
        mIsCanceled = true;
    }

    /**
     * This method is to be overridden and should return the resource that
     * is used as the title as the
     * {@link ErrorDialog} title when the
     * task fails with an exception.
     */
    @StringRes
    public int getErrorTitleRes() {
        return R.string.dialog_error_title;
    }

    public boolean isTaskCanceled() {
        return mIsCanceled;
    }

    public interface AsyncTaskPostCallback {
        void onPostExecute(Boolean isSuccess);
    }

    public interface AsyncTaskCallback {
        boolean doInBackground(Void... params);

        void onPreExecute();

        void onProgressUpdate(String... progress);

        void onPostExecute(Boolean isSuccess);
    }
}
