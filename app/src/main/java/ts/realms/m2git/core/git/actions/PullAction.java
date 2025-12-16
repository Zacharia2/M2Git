package ts.realms.m2git.core.git.actions;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;

import java.util.Set;

import ts.realms.m2git.R;
import ts.realms.m2git.core.git.RepoAction;
import ts.realms.m2git.core.git.tasks.remote.PullTask;
import ts.realms.m2git.core.models.Repo;
import ts.realms.m2git.ui.components.dialogs.BaseDialogFragment;
import ts.realms.m2git.ui.components.dialogs.DummyDialogListener;
import ts.realms.m2git.ui.screens.repoDetail.RepoDetailActivity;

public class PullAction extends RepoAction {

    public PullAction(Repo repo, RepoDetailActivity activity) {
        super(repo, activity);
    }

    private static void pull(Repo repo, RepoDetailActivity activity, String remote,
                             boolean forcePull) {
        PullTask pullTask = new PullTask(repo, remote, forcePull,
            activity.new ProgressCallback(R.string.pull_msg_init));
        pullTask.executeTask();
        activity.closeOperationDrawer();
    }

    @Override
    public void execute() {
        Set<String> remotes = mRepo.getRemotes();
        if (remotes == null || remotes.isEmpty()) {
            mActivity.showToastMessage(R.string.alert_please_add_a_remote);
            return;
        }
        PullDialog pd = new PullDialog();
        pd.setArguments(mRepo.getBundle());
        pd.show(mActivity.getSupportFragmentManager(), "pull-repo-dialog");
        mActivity.closeOperationDrawer();
    }

    public static class PullDialog extends BaseDialogFragment {

        private Repo mRepo;
        private RepoDetailActivity mActivity;
        private CheckBox mForcePull;
        private ArrayAdapter<String> mAdapter;

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            super.onCreateDialog(savedInstanceState);
            Bundle args = getArguments();
            if (args != null && args.containsKey(Repo.TAG)) {
                mRepo = (Repo) args.getSerializable(Repo.TAG);
            }

            mActivity = (RepoDetailActivity) getActivity();
            AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
            LayoutInflater inflater = mActivity.getLayoutInflater();

            View layout = inflater.inflate(R.layout.dialog_pull, null);
            mForcePull = layout.findViewById(R.id.forcePull);
            ListView mRemoteList = layout.findViewById(R.id.remoteList);

            mAdapter = new ArrayAdapter<String>(mActivity, android.R.layout.simple_list_item_1);
            Set<String> remotes = mRepo.getRemotes();
            mAdapter.addAll(remotes);
            mRemoteList.setAdapter(mAdapter);

            mRemoteList.setOnItemClickListener((parent, view, position, id) -> {
                String remote = mAdapter.getItem(position);
                boolean isForcePull = mForcePull.isChecked();
                pull(mRepo, mActivity, remote, isForcePull);
                dismiss();
            });

            builder.setTitle(R.string.dialog_pull_repo_title).setView(layout).setNegativeButton(R.string.label_cancel, new DummyDialogListener());
            return builder.create();
        }
    }

}
