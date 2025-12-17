package ts.realms.m2git.core.network.ssh;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;

import java.io.File;
import java.io.FileOutputStream;

import timber.log.Timber;
import ts.realms.m2git.R;
import ts.realms.m2git.ui.components.dialogs.BaseDialogFragment;
import ts.realms.m2git.ui.components.fragments.PrivateKeyManageActivity;


public class PrivateKeyGenerate extends BaseDialogFragment {
    private EditText mNewFilename;
    private EditText mKeyLength;
    private RadioGroup mRadioGroup;

    @NonNull
    @SuppressLint("SetTextI18n")
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_generate_key, null);
        mNewFilename = view.findViewById(R.id.newFilename);
        mKeyLength = view.findViewById(R.id.key_size);
        mKeyLength.setText("4096");
        mRadioGroup = view.findViewById(R.id.radio_keygen_type);
        builder.setMessage(R.string.label_dialog_generate_key).setView(view).setPositiveButton(R.string.label_generate_key, (dialog, which) -> generateKey()).setNegativeButton(R.string.label_cancel, (dialog, which) -> {
        });
        return builder.create();
    }

    private void generateKey() {
        String newFilename = mNewFilename.getText().toString().trim();
        if (newFilename.isEmpty()) {
            showToastMessage(R.string.alert_new_filename_required);
            mNewFilename.setError(getString(R.string.alert_new_filename_required));
            return;
        }
        if (newFilename.contains("/")) {
            showToastMessage(R.string.alert_filename_format);
            mNewFilename.setError(getString(R.string.alert_filename_format));
            return;
        }
        int keySize = Integer.parseInt(mKeyLength.getText().toString());
        if (keySize < 1024) {
            showToastMessage(R.string.alert_too_short_key_size);
            mNewFilename.setError(getString(R.string.alert_too_short_key_size));
            return;
        }
        if (keySize > 16384) {
            showToastMessage(R.string.alert_too_long_key_size);
            mNewFilename.setError(getString(R.string.alert_too_long_key_size));
            return;
        }

        int checkedRadioButtonId = mRadioGroup.getCheckedRadioButtonId();
        int type;

        if (checkedRadioButtonId == R.id.radio_dsa) {
            type = KeyPair.DSA;
            // JSCH doesn't support writing ED25519 keys yet, only reading
            //        } else if (checkedRadioButtonId == R.id.radio_ed25519) {
            //            type = KeyPair.ED25519;
        } else {
            type = KeyPair.RSA;
        }

        File newKey = new File(PrivateKeyUtils.getPrivateKeyFolder(), newFilename);
        if (newKey.exists()) {
            showToastMessage(R.string.alert_key_exists);
            mNewFilename.setError(getString(R.string.alert_key_exists));
            return;
        }
        File newPubKey = new File(PrivateKeyUtils.getPublicKeyFolder(), newFilename);
        try {
            JSch jsch = new JSch();
            KeyPair kpair = KeyPair.genKeyPair(jsch, type, keySize);
            kpair.writePrivateKey(new FileOutputStream(newKey));
            kpair.writePublicKey(new FileOutputStream(newPubKey), "mgit");
            kpair.dispose();
        } catch (Exception e) {
            Timber.e(e, "Failed to generate SSH key");
            //TODO: send to acra            new RuntimeException("Failed to generate SSH key", e);
            // Delete any leftover files
            newKey.delete();
            newPubKey.delete();
        }
        ((PrivateKeyManageActivity) getActivity()).refreshList();
    }
}
