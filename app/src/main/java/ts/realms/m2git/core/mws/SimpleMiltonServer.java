package ts.realms.m2git.core.mws;

import java.io.File;

import io.milton.config.HttpManagerBuilder;
import io.milton.http.HttpManager;
import io.milton.http.fs.NullSecurityManager;
import io.milton.simpleton.SimpletonServer;
import ts.realms.m2git.core.mws.fs.FileSystemResourceFactory;

public class SimpleMiltonServer {
    private static SimpleMiltonServer miltonServer;
    private SimpletonServer ss;

    public static SimpleMiltonServer getInstance() {
        if (miltonServer == null) {
            miltonServer = new SimpleMiltonServer();
        }
        return miltonServer;
    }

    public void build(int port, String homeFolder) {
        NullSecurityManager nsm = new NullSecurityManager();
        FileSystemResourceFactory resourceFactory = new FileSystemResourceFactory(new File(homeFolder), nsm, "/");
        resourceFactory.setAllowDirectoryBrowsing(true);
        HttpManagerBuilder b = new HttpManagerBuilder();
        b.setEnableFormAuth(false);
        b.setResourceFactory(resourceFactory);
        HttpManager httpManager = b.buildHttpManager();
        ss = new SimpletonServer(httpManager, b.getOuterWebdavResponseHandler(), 100, 10);
        ss.setHttpPort(port);

    }

    public void start() {
        ss.start();
    }

    public void stop() {
        ss.stop();
    }
}
