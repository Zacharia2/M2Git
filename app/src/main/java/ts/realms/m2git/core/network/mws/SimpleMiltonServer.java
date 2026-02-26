package ts.realms.m2git.core.network.mws;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

import io.milton.config.HttpManagerBuilder;
import io.milton.http.SecurityManager;
import io.milton.simpleton.SslSimpletonServer;
import timber.log.Timber;
import ts.realms.m2git.MainApplication;
import ts.realms.m2git.R;
import ts.realms.m2git.core.network.mws.fs.FileSystemResourceFactory;
import ts.realms.m2git.core.network.mws.fs.LocalCacheManager;
import ts.realms.m2git.core.network.mws.fs.SimplePropertyManager;
import ts.realms.m2git.core.network.mws.fs.SimpleSecurityManager;

public class SimpleMiltonServer {
    private static SimpleMiltonServer miltonServer;
    private SslSimpletonServer sslServer;

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
        sslServer = new SslSimpletonServer(b.buildHttpManager(), b.getOuterWebdavResponseHandler(), 100, 10);
        sslServer.setHttpPort(port);
        sslServer.setKeystoreFile(this.prepareKeystore());
        sslServer.setKeystorePassword(new String(Base64.getDecoder().decode(MainApplication.getContext().getString(R.string.bk))));
        sslServer.setKeystoreType("BKS"); // Android 上使用 BKS 格式
        sslServer.setSslProtocol("TLS");
    }

    public void build(String homeFolder, int port, String user, String password) {
        SimpleSecurityManager ssm = new SimpleSecurityManager();
        // Realm（领域） 定义受保护资源的“安全空间”。告诉浏览器或客户端正在尝试访问哪个区域。
        ssm.setRealm("User Files");
        ssm.setNameAndPasswords(Map.of(user, password));
        this.buildWithSecurityManager(homeFolder, port, ssm);
    }

    private File prepareKeystore() {
        // 从 res/raw 复制 keystore 到 files 目录
        File keystoreFile = new File(MainApplication.getContext().getFilesDir(), "server.bks");
        if (!keystoreFile.exists()) {
            try (InputStream is = MainApplication.getContext().getResources().openRawResource(R.raw.server);
                 FileOutputStream fos = new FileOutputStream(keystoreFile)) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = is.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
            } catch (IOException e) {
                e.fillInStackTrace();
            }
        }
        return keystoreFile;
    }

    public void start() {
        if (sslServer == null) return;
        sslServer.start();
        Timber.tag("miltonServer").i("miltonServer start");
    }

    public void stop() {
        if (sslServer == null) return;
        sslServer.stop();
        Timber.tag("miltonServer").i("miltonServer stop");
    }
}
