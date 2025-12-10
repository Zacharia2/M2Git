package ts.realms.m2git.ui.screens.settings;

import android.os.Bundle;

import ts.realms.m2git.R;
import ts.realms.m2git.ui.screens.main.BaseCompatActivity;

/**
 * Activity for user settings
 */
public class UserSettingsActivity extends BaseCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setSupportActionBar(findViewById(R.id.settings_activity_toolbar));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        getSupportFragmentManager().beginTransaction()
            .replace(android.R.id.content, new SettingsFragment())
            .commit();
    }
}
