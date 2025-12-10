package ts.realms.m2git.ui.screens.settings;


import android.os.Bundle;

import ts.realms.m2git.R;
import ts.realms.m2git.ui.screens.main.BaseCompatActivity;

public class CredentialActivity extends BaseCompatActivity {
    private static final String TAG = "DemoFragmentActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_credential);
        setSupportActionBar(findViewById(R.id.credential_activity_top_app_bar));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }
}
