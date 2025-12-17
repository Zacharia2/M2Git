package ts.realms.m2git.core.command;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.StringRes;

import java.util.concurrent.CompletableFuture;

import timber.log.Timber;
import ts.realms.m2git.R;
import ts.realms.m2git.ui.components.dialogs.ErrorDialog;
import ts.realms.m2git.utils.BasicFunctions;

// 异步执行、executeTask、cancelTask、asyncTaskCallback
public abstract class MAsyncTask {
    protected Throwable mException;
    protected int mErrorRes = 0;
    protected boolean mIsTaskAdded; // 共享状态给子类
    protected Object asyncTaskCallback = this; // 最终值是 当前这个抽象类的具体子类的实例对象的引用。
    private CompletableFuture<Void> completableExecuteTaskFuture;
    private boolean mIsCanceled = false;
    private String[] progressValues;

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
        if (!completableExecuteTaskFuture.isCancelled()) {
            completableExecuteTaskFuture.cancel(false);
            mIsCanceled = completableExecuteTaskFuture.isCancelled();
        }
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

    // 主要异步调用入口
    public final CompletableFuture<Void> executeTask(Void... params) {
        // https://www.jianshu.com/p/37502bbbb25a
        // https://www.jianshu.com/p/ba309c0cf533
        // AsyncTaskCallback、AsyncTaskDoCallback、AsyncTaskPostCallback
        if (mIsTaskAdded) {
            // main
            // onPreExecute、AsyncTaskCallback
            if (asyncTaskCallback instanceof AsyncTaskCallback) {
                ((AsyncTaskCallback) asyncTaskCallback).onPreExecute();
            }
            completableExecuteTaskFuture = CompletableFuture.runAsync(() -> {
                // doInBackground、AsyncTaskCallback、AsyncTaskDoCallback
                boolean isSuccess;
                if (asyncTaskCallback instanceof AsyncTaskDoCallback) {
                    isSuccess = ((AsyncTaskDoCallback) asyncTaskCallback).doInBackground(params);
                } else if (asyncTaskCallback instanceof AsyncTaskCallback) {
                    isSuccess = ((AsyncTaskCallback) asyncTaskCallback).doInBackground(params);
                } else {
                    isSuccess = false;
                }
                Handler uiThread = new Handler(Looper.getMainLooper());
                uiThread.post(() -> {
                    // onPostExecute、AsyncTaskCallback、AsyncTaskPostCallback
                    if (asyncTaskCallback instanceof AsyncTaskPostCallback) {
                        ((AsyncTaskPostCallback) asyncTaskCallback).onPostExecute(isSuccess);
                    } else if (asyncTaskCallback instanceof AsyncTaskCallback) {
                        ((AsyncTaskCallback) asyncTaskCallback).onPostExecute(isSuccess);
                    }
                    if (this.progressValues != null && this.progressValues.length != 0) {
                        // onProgressUpdate、AsyncTaskCallback
                        if (asyncTaskCallback instanceof AsyncTaskCallback) {
                            ((AsyncTaskCallback) asyncTaskCallback).onProgressUpdate(progressValues);
                        }
                    }
                });

            });
        } else {
            BasicFunctions.getActiveActivity().showToastMessage(R.string.error_task_running);
        }
        return completableExecuteTaskFuture;
    }

    protected final void publishProgress(String... values) {
        // 从异步线程doInBackground中调用，回到主线程执行。
        Handler uiThread = new Handler(Looper.getMainLooper());
        uiThread.post(() -> this.progressValues = values);
    }

    public interface AsyncTaskCallback {
        void onPreExecute();

        boolean doInBackground(Void... params);

        void onPostExecute(Boolean isSuccess);

        void onProgressUpdate(String... progress);
    }

    public interface AsyncTaskDoCallback {
        boolean doInBackground(Void... params);
    }

    public interface AsyncTaskPostCallback {
        void onPostExecute(Boolean isSuccess);
    }
}
