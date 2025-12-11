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

//        - view是一个列表视图
//            - 仓库和证书的关系是：一个仓库对应一个证书、一个证书对应多个仓库。
//        - 创建，在视图中创建，或者在代码请求中不存在创建(可以直接通过cid关联)。
//        - 删除
//            - 根据cid删除，列表视图可以提供cid，只有在视图中删除
//            - 查找
//            - 查找全部，获得全部记录，在列表视图加载的时候
//            - 根据RepoId查找，获得唯一一个证书记录，在代码中查找，请求凭证时。
//        - 根据cid查找，获得一条记录
//            - 是否还需要根据其它方式查找？看需要。有什么需要？关联的需要、断联的需要、获取的需要。其实就是修改的需要。
//        - 关联
//            - 根据cid，repoId关联
//            - 在视图中关联（视图会有cid）、在视图创建后再次编辑进行关联（视图会有cid）、在请求不存在创建后关联（用创建后的cid）
//        - 断联
//            - 只在视图中断联
//        - 仓库本身自己就有 username和password、不知道是不是凭证。
//        BaseCompatActivity(promptForPasswordInner) -> RepoRemoteOpTask(onClicked) -> RepoOpTask(setCredentials)


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
