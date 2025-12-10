package ts.realms.m2git.ui.components.dialogs;

import android.content.Context;
import android.content.DialogInterface;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import ts.realms.m2git.ui.screens.main.BaseCompatActivity;

public class BaseDialogFragment extends DialogFragment {

    // It's safe to assume onAttach is called before other code.
    @NonNull
    private BaseCompatActivity mActivity;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mActivity = (BaseCompatActivity) context;
    }

    @NonNull
    public BaseCompatActivity getRawActivity() {
        return mActivity;
    }

    public void showMessageDialog(int title, int msg, int positiveBtn,
                                  DialogInterface.OnClickListener positiveListener) {
        getRawActivity().showMessageDialog(title, msg, positiveBtn,
            positiveListener);
    }

    public void showMessageDialog(int title, String msg, int positiveBtn,
                                  DialogInterface.OnClickListener positiveListener) {
        getRawActivity().showMessageDialog(title, msg, positiveBtn,
            positiveListener);
    }

    public void showToastMessage(int resId) {
        getRawActivity().showToastMessage(getString(resId));
    }

    public void showToastMessage(String msg) {
        getRawActivity().showToastMessage(msg);
    }

    public void promptForPassword(BaseCompatActivity.OnPasswordEntered onPasswordEntered,
                                  int errorId) {
        getRawActivity().promptForPassword(onPasswordEntered, errorId);
    }

    public void promptForPassword(BaseCompatActivity.OnPasswordEntered onPasswordEntered) {
        getRawActivity().promptForPassword(onPasswordEntered, null);
    }
}
