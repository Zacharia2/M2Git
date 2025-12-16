package ts.realms.m2git.ui.components.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

import java.io.File;

import ts.realms.m2git.R;
import ts.realms.m2git.core.models.Repo;
import ts.realms.m2git.local.database.RepoContract;
import ts.realms.m2git.local.preference.PreferenceHelper;
import ts.realms.m2git.utils.FsUtils;

/**
 * Created by sheimi on 8/24/13.
 */

public class ImportLocalRepoDialog extends BaseDialogFragment implements
    View.OnClickListener {

    public static final String FROM_PATH = "from path";
    private File mFile;
    private String mFromPath;
    private Activity mActivity;
    private EditText mLocalPath;
    private CheckBox mImportAsExternal;
    private PreferenceHelper mPrefsHelper;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);

        mActivity = getActivity();

        mPrefsHelper = PreferenceHelper.getInstance(this.getContext());

        Bundle args = getArguments();
        if (args != null && args.containsKey(FROM_PATH)) {
            mFromPath = args.getString(FROM_PATH);
        }
        mFile = new File(mFromPath);
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        builder.setTitle(getString(R.string.dialog_import_set_local_repo_title));
        View view = getActivity().getLayoutInflater().inflate(
            R.layout.dialog_import_repo, null);

        builder.setView(view);
        mLocalPath = view.findViewById(R.id.localPath);
        mLocalPath.setText(mFile.getName());
        mImportAsExternal = view.findViewById(R.id.importAsExternal);
        mImportAsExternal
            .setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    mLocalPath.setText(Repo.EXTERNAL_PREFIX
                        + mFile.getAbsolutePath());
                } else {
                    mLocalPath.setText(mFile.getName());
                }
                mLocalPath.setEnabled(isChecked);
            });

        // set button listener
        builder.setNegativeButton(R.string.label_cancel, new DummyDialogListener());
        builder.setPositiveButton(R.string.label_import, new DummyDialogListener());

        return builder.create();
    }

    @Override
    public void onStart() {
        super.onStart();
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog == null)
            return;
        Button positiveButton = dialog
            .getButton(Dialog.BUTTON_POSITIVE);
        positiveButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        final String localPath = mLocalPath.getText().toString().trim();
        if (!mImportAsExternal.isChecked()) {
            if (localPath.isEmpty()) {
                showToastMessage(R.string.alert_field_not_empty);
                mLocalPath.setError(getString(R.string.alert_field_not_empty));
                return;
            }

            if (localPath.contains("/")) {
                showToastMessage(R.string.alert_localpath_format);
                mLocalPath.setError(getString(R.string.alert_localpath_format));
                return;
            }

            File file = Repo.getDir(mPrefsHelper, localPath);
            if (file.exists()) {
                showToastMessage(R.string.alert_file_exists);
                mLocalPath.setError(getString(R.string.alert_file_exists));
                return;
            }
        }

        final Repo repo = Repo.importRepo(localPath, getString(R.string.importing));

        if (mImportAsExternal.isChecked()) {
            updateRepoInformation(repo);
            dismiss();
            return;
        }
        final File repoFile = Repo.getDir(mPrefsHelper, localPath);
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                FsUtils.copyDirectory(mFile, repoFile);
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateRepoInformation(repo);
                    }
                });
            }
        });
        thread.start();
        dismiss();
    }

    private void updateRepoInformation(Repo repo) {
        repo.updateLatestCommitInfo();
        repo.updateRemote();
        repo.updateStatus(RepoContract.REPO_STATUS_NULL);
    }
}
