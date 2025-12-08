package ts.realms.m2git.ui.screens.fragments;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import java.io.File;

import timber.log.Timber;
import ts.realms.m2git.MainApplication;
import ts.realms.m2git.R;
import ts.realms.m2git.core.git.tasks.CloneTask;
import ts.realms.m2git.core.git.tasks.InitLocalTask;
import ts.realms.m2git.core.models.Repo;
import ts.realms.m2git.local.preference.PreferenceHelper;

public class CloneViewModel extends AndroidViewModel {

    public MutableLiveData<String> localRepoName;
    public MutableLiveData<Boolean> initLocal;
    public MutableLiveData<String> remoteUrlError;
    public MutableLiveData<String> localRepoNameError;
    public MutableLiveData<Boolean> visible;
    public boolean cloneRecursively;
    private String remoteUrl;

    public CloneViewModel(Application application) {
        super(application);
        this.localRepoName = new MutableLiveData<>();
        this.initLocal = new MutableLiveData<>();
        this.remoteUrlError = new MutableLiveData<>();
        this.localRepoNameError = new MutableLiveData<>();
        this.visible = new MutableLiveData<>();
        this.cloneRecursively = false;
        this.visible.setValue(false);
        this.initLocal.setValue(false);
    }

    public String getRemoteUrl() {
        return this.remoteUrl;
    }

    public void setRemoteUrl(String value) {
        this.remoteUrl = value;
        this.localRepoName.setValue(stripGitExtension(stripUrlFromRepo(value)));
    }

    public void show(boolean show) {
        this.visible.setValue(show);
    }

    public void cloneRepo() {
        // FIXME: createRepo should not use user visible strings, instead will need to be refactored
        // to set an observable state
        if (Boolean.TRUE.equals(this.initLocal.getValue())) {
            Timber.d("INIT LOCAL %s", this.localRepoName.getValue());
            initLocalRepo();
        } else {
            Timber.d("CLONE REPO %s %s [%b]", this.localRepoName.getValue(), this.remoteUrl,
                this.cloneRecursively);
            Repo repo = Repo.createRepo(this.localRepoName.getValue(), this.remoteUrl, "");
            CloneTask task = new CloneTask(repo, this.cloneRecursively, "", null);
            task.executeTask();
            this.setRemoteUrl("");
            show(false);
        }
    }

    public boolean validate() {
        if (Boolean.TRUE.equals(this.initLocal.getValue())) {
            return validateLocalName(this.localRepoName.getValue());
        } else {
            return validateRemoteUrl(this.remoteUrl) && validateLocalName(this.localRepoName.getValue());
        }
    }

    public void initLocalRepo() {
        Repo repo = Repo.createRepo(this.localRepoName.getValue(), "local repository", "");
        InitLocalTask task = new InitLocalTask(repo);
        task.executeTask();
    }

    private String stripUrlFromRepo(String remoteUrl) {
        int lastSlash = remoteUrl.lastIndexOf("/");
        if (lastSlash != -1) {
            return remoteUrl.substring(lastSlash + 1);
        } else {
            return remoteUrl;
        }
    }

    private String stripGitExtension(String remoteUrl) {
        int extension = remoteUrl.indexOf(".git");
        if (extension != -1) {
            return remoteUrl.substring(0, extension);
        } else {
            return remoteUrl;
        }
    }

    private boolean validateRemoteUrl(String remoteUrl) {
        this.remoteUrlError.setValue(null);
        if (remoteUrl.isBlank()) {
            this.remoteUrlError.setValue(getApplication().getString(R.string.alert_remoteurl_required));
            return false;
        }
        return true;
    }

    private boolean validateLocalName(String localName) {
        this.localRepoNameError.setValue(null);
        if (localName.isBlank()) {
            this.localRepoNameError.setValue(getApplication().getString(R.string.alert_localpath_required));
            return false;
        }
        if (localName.contains("/")) {
            this.localRepoNameError.setValue(getApplication().getString(R.string.alert_localpath_format));
            return false;
        }

        PreferenceHelper prefsHelper = ((MainApplication) getApplication()).getPreferenceHelper();
        File file = Repo.getDir(prefsHelper, localName);
        if (file.exists()) {
            this.localRepoNameError.setValue(getApplication().getString(R.string.alert_localpath_repo_exists));
            return false;
        }
        return true;
    }
}
