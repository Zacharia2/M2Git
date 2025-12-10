package ts.realms.m2git.ui.components.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import java.io.File;

import ts.realms.m2git.R;
import ts.realms.m2git.local.preference.SecurePrefsHelper;
import ts.realms.m2git.ui.screens.fragments.PrivateKeyManageActivity;

/**
 * Allowing editing password for a stored private key
 */

public class EditKeyPasswordDialog extends BaseDialogFragment implements
    View.OnClickListener, DialogInterface.OnClickListener {

    public static final String KEY_FILE_EXTRA = "extra_key_file";
    private File mKeyFile;
    private PrivateKeyManageActivity mActivity;
    private EditText mPassword;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);
        mActivity = (PrivateKeyManageActivity) getActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        Bundle args = getArguments();
        if (args != null && args.containsKey(KEY_FILE_EXTRA)) {
            mKeyFile = new File(args.getString(KEY_FILE_EXTRA));
        }

        builder.setTitle(getString(R.string.dialog_edit_key_password_title));
        View view = mActivity.getLayoutInflater().inflate(
            R.layout.dialog_prompt_for_password_only, null);

        builder.setView(view);
        mPassword = view.findViewById(R.id.password);

        // set button listener
        builder.setNegativeButton(R.string.label_cancel,
            new DummyDialogListener());
        builder.setPositiveButton(R.string.label_save,
            new DummyDialogListener());

        return builder.create();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_FILE_EXTRA, mKeyFile.getAbsolutePath());
    }

    @Override
    public void onStart() {
        super.onStart();
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog == null) {
            return;
        }
        Button positiveButton = dialog.getButton(Dialog.BUTTON_POSITIVE);
        positiveButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        String newPassword = mPassword.getText().toString().trim();
        SecurePrefsHelper.getInstance(this.getContext()).set(mKeyFile.getName(), newPassword);
        mActivity.refreshList();
        dismiss();
    }

    @Override
    public void onClick(DialogInterface dialogInterface, int i) {
    }

}
