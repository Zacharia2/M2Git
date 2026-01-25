package ts.realms.m2git.core.network.mws;

import java.io.File;
import java.util.Locale;

import io.milton.config.HttpManagerBuilder;
import io.milton.http.fs.NullSecurityManager;
import io.milton.simpleton.SimpletonServer;
import timber.log.Timber;
import ts.realms.m2git.core.network.mws.fs.FileSystemResourceFactory;
import ts.realms.m2git.core.network.mws.fs.LocalCacheManager;
import ts.realms.m2git.core.network.mws.fs.SimplePropertyManager;

public class SimpleMiltonServer {
    private static SimpleMiltonServer miltonServer;
    private SimpletonServer ss;

    public static SimpleMiltonServer getInstance() {
        if (miltonServer == null) {
            miltonServer = new SimpleMiltonServer();
        }
        return miltonServer;
    }

    public void build(String homeFolder, int port) {
        Timber.tag("miltonServer").i(homeFolder + "::" + port);
        Locale.setDefault(Locale.ENGLISH);
        NullSecurityManager nsm = new NullSecurityManager();
        FileSystemResourceFactory resourceFactory = new FileSystemResourceFactory(new File(homeFolder), nsm, "/");
        resourceFactory.setAllowDirectoryBrowsing(true);
        resourceFactory.setPropertyManager(new SimplePropertyManager(new LocalCacheManager()));
        HttpManagerBuilder b = new HttpManagerBuilder();
        b.setResourceFactory(resourceFactory);
        b.setEnableQuota(true);
        b.setEnableFormAuth(false);
        ss = new SimpletonServer(b.buildHttpManager(), b.getOuterWebdavResponseHandler(), 100, 10);
        ss.setHttpPort(port);
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
