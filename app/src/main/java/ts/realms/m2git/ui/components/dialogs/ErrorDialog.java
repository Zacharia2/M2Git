package ts.realms.m2git.ui.components.dialogs;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.conscrypt.BuildConfig;

import timber.log.Timber;
import ts.realms.m2git.R;
import ts.realms.m2git.databinding.DialogErrorBinding;
import ts.realms.m2git.ui.screens.fragments.SheimiDialogFragment;

public class ErrorDialog extends SheimiDialogFragment {
    private Throwable mThrowable = null;

    @StringRes
    private int mErrorRes = 0;

    @StringRes
    private int mErrorTitleRes = 0;

    public int getErrorTitleRes() {
        return mErrorTitleRes != 0 ? mErrorTitleRes : R.string.dialog_error_title;
    }

    public void setErrorTitleRes(int ErrorTitleRes) {
        mErrorTitleRes = ErrorTitleRes;
    }

    @NonNull
    @SuppressLint("SetTextI18n")
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);
        /*
         * DateBinding示例
         *
         * 绑定类的名称是通过将 XML 文件的名称转换为驼峰式大小写，并在结尾处添加 Binding 一词来生成的。
         * 例如，假设某个布局文件的名称为 dialog_error.xml，所生成的绑定类的名称就为 DialogErrorBinding
         */
        android.view.LayoutInflater inflater = getActivity().getLayoutInflater();
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        DialogErrorBinding layout = DialogErrorBinding.inflate(inflater);
        String details = mThrowable instanceof Exception ? mThrowable.getMessage() : "";
        layout.errorMessage.setText(getString(mErrorRes) + "\n" + details);

        builder.setView(layout.getRoot());

        // set button listener
        builder.setTitle(getErrorTitleRes());
        builder.setPositiveButton(getString(R.string.label_ok), new DummyDialogListener());
        return builder.create();
    }

    @Override
    public void onStart() {
        super.onStart();
        AlertDialog dialog = (AlertDialog) getDialog();
        Button positiveButton = dialog.getButton(Dialog.BUTTON_POSITIVE);
        positiveButton.setOnClickListener(v -> {
            if (BuildConfig.DEBUG) {
                // when debugging just log the exception
                if (mThrowable != null) {
                    Timber.e(mThrowable);
                } else {
                    Timber.e(mErrorRes != 0 ? getString(mErrorRes) : "");
                }
            }
            dismiss();
        });

    }

    public void setThrowable(Throwable throwable) {
        mThrowable = throwable;
    }

    public void setErrorRes(@StringRes int errorRes) {
        mErrorRes = errorRes;
    }
}
