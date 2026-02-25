package ts.realms.m2git.core.network.mws;

import java.io.File;
import java.util.Locale;
import java.util.Map;

import io.milton.config.HttpManagerBuilder;
import io.milton.http.SecurityManager;
import io.milton.simpleton.SimpletonServer;
import timber.log.Timber;
import ts.realms.m2git.core.network.mws.fs.FileSystemResourceFactory;
import ts.realms.m2git.core.network.mws.fs.LocalCacheManager;
import ts.realms.m2git.core.network.mws.fs.SimplePropertyManager;
import ts.realms.m2git.core.network.mws.fs.SimpleSecurityManager;

public class SimpleMiltonServer {
    private static SimpleMiltonServer miltonServer;
    private SimpletonServer ss;

    public static SimpleMiltonServer getInstance() {
        if (miltonServer == null) {
            miltonServer = new SimpleMiltonServer();
        }
        return miltonServer;
    }

    public void buildWithSecurityManager(String homeFolder, int port, SecurityManager securityManager) {
        Timber.tag("miltonServer").i(homeFolder + "::" + port);
        Locale.setDefault(Locale.ENGLISH);
        FileSystemResourceFactory resourceFactory = new FileSystemResourceFactory(new File(homeFolder), securityManager, "/");
        resourceFactory.setAllowDirectoryBrowsing(true);
        resourceFactory.setPropertyManager(new SimplePropertyManager(new LocalCacheManager()));
        HttpManagerBuilder b = new HttpManagerBuilder();
        b.setResourceFactory(resourceFactory);
        b.setEnableQuota(true);
        b.setEnableFormAuth(false);
        ss = new SimpletonServer(b.buildHttpManager(), b.getOuterWebdavResponseHandler(), 100, 10);
        ss.setHttpPort(port);
    }

    public void build(String homeFolder, int port, String user, String password) {
        SimpleSecurityManager ssm = new SimpleSecurityManager();
        // Realm（领域） 定义受保护资源的“安全空间”。告诉浏览器或客户端正在尝试访问哪个区域。
        ssm.setRealm("User Files");
        ssm.setNameAndPasswords(Map.of(user, password));
        this.buildWithSecurityManager(homeFolder, port, ssm);
    }

    public void start() {
        if (ss == null) return;
        ss.start();
        Timber.tag("miltonServer").i("miltonServer start");
    }

    public void stop() {
        if (ss == null) return;
        ss.stop();
        Timber.tag("miltonServer").i("miltonServer stop");
    }
}
