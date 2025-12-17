package ts.realms.m2git.core.command.tasks;

import ts.realms.m2git.MainApplication;
import ts.realms.m2git.core.models.Repo;
import ts.realms.m2git.local.preference.PreferenceHelper;
import ts.realms.m2git.ui.screens.main.BaseCompatActivity;

/**
 * Super class for Tasks that operate on a git remote
 */

public abstract class RepoRemoteOpTask extends RepoOpTask implements BaseCompatActivity.OnPasswordEntered {


    public RepoRemoteOpTask(Repo repo) {
        super(repo);
        mRepo.setToken(MainApplication.getContext());
    }

    public RepoRemoteOpTask(Repo repo, boolean parallel) {
        super(repo, parallel);
        mRepo.setToken(MainApplication.getContext());
    }

    @Override
    public void onClicked(String username, String password, boolean savePassword) {
        mRepo.setUsername(username);
        mRepo.setPassword(password);
        if (savePassword) {
            PreferenceHelper prefHelper = PreferenceHelper.getInstance(MainApplication.getContext());
            if (prefHelper != null) {
                prefHelper.setTokenAccount(username);
                prefHelper.setTokenSecretKey(password);
            }
            mRepo.saveCredentials();
        }
        mRepo.removeTask(this);
        getNewTask().executeTask();
    }

    @Override
    public void onCanceled() {

    }

    public abstract RepoRemoteOpTask getNewTask();
}
