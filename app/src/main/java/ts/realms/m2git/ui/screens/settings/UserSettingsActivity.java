package ts.realms.m2git.ui.screens.settings;

import android.os.Bundle;

import ts.realms.m2git.ui.screens.fragments.SettingsFragment;
import ts.realms.m2git.ui.screens.fragments.SheimiFragmentActivity;

/**
 * Activity for user settings
 */
public class UserSettingsActivity extends SheimiFragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        getFragmentManager().beginTransaction()
            .replace(android.R.id.content, new SettingsFragment())
            .commit();
    }
}
