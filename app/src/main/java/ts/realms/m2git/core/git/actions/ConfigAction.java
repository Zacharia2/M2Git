package ts.realms.m2git.core.git.actions;

import android.app.AlertDialog;
import android.view.LayoutInflater;

import androidx.databinding.DataBindingUtil;

import timber.log.Timber;
import ts.realms.m2git.R;
import ts.realms.m2git.common.errors.StopTaskException;
import ts.realms.m2git.core.git.RepoAction;
import ts.realms.m2git.core.models.Repo;
import ts.realms.m2git.databinding.DialogRepoConfigBinding;
import ts.realms.m2git.ui.screens.fragments.RepoDetailActivity;
import ts.realms.m2git.ui.viewmodels.GitConfig;

/**
 * Action to display configuration for a Repo
 */
public class ConfigAction extends RepoAction {


    public ConfigAction(Repo repo, RepoDetailActivity activity) {
        super(repo, activity);
    }

    @Override
    public void execute() {

        try {
            DialogRepoConfigBinding binding =
                DataBindingUtil.inflate(LayoutInflater.from(mActivity),
                    R.layout.dialog_repo_config, null, false);
            GitConfig gitConfig = new GitConfig(mRepo);
            binding.setViewModel(gitConfig);

            AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
            builder.setView(binding.getRoot()).setNeutralButton(R.string.label_done, null).create().show();

        } catch (StopTaskException e) {
            //FIXME: show error to user
            Timber.e(e);
        }
    }

}
