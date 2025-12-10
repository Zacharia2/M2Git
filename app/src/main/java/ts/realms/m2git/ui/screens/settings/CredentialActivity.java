package ts.realms.m2git.ui.screens.settings;


import android.database.Cursor;
import android.os.Bundle;

import java.util.Map;

import ts.realms.m2git.R;
import ts.realms.m2git.core.models.Credential;
import ts.realms.m2git.local.database.RepoDbManager;
import ts.realms.m2git.ui.screens.main.BaseCompatActivity;

public class CredentialActivity extends BaseCompatActivity {
    private static final String TAG = "DemoFragmentActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_credential);
        setSupportActionBar(findViewById(R.id.credential_activity_top_app_bar));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);


        long cid = RepoDbManager.createCredential("ts1","s1");
        Cursor cursor = RepoDbManager.getCredentialById(cid);
        Credential credential = new Credential(cursor);
        RepoDbManager.relateRepoWithCredential(cid,"1212");
        RepoDbManager.relateRepoWithCredential(cid,"2323");
        RepoDbManager.unrelateRepoWithCredential(cid,"1212");
        int all = RepoDbManager.queryAllCredential().getCount();
        RepoDbManager.deleteCredential(cid);
        Map<String, String> re =  RepoDbManager.queryCredentialByRepoId("2323");
    }
}
