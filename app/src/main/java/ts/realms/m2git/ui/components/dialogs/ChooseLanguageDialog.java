package ts.realms.m2git.ui.components.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;

import java.util.List;

import ts.realms.m2git.R;
import ts.realms.m2git.ui.components.fragments.ViewFileActivity;
import ts.realms.m2git.utils.CodeGuesser;

/**
 * Created by sheimi on 8/16/13.
 */
public class ChooseLanguageDialog extends BaseDialogFragment {

    private ViewFileActivity mActivity;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);

        mActivity = (ViewFileActivity) getActivity();

        final List<String> langs = CodeGuesser.getLanguageList();
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);

        builder.setTitle(R.string.dialog_choose_language_title);
        builder.setItems(langs.toArray(new String[0]),
            (dialogInterface, position) -> {
                String lang = langs.get(position);
                String tag = CodeGuesser.getLanguageTag(lang);
                mActivity.setLanguage(tag);
            });

        return builder.create();
    }

}
