package ts.realms.m2git.core.network.mws;

import static ts.realms.m2git.core.network.mws.SecureStore.CredentialStore.DUMMY_HA1;

import android.content.Context;
import android.util.Base64;

import org.simpleframework.http.core.ContainerServer;
import org.simpleframework.transport.Server;
import org.simpleframework.transport.Socket;
import org.simpleframework.transport.connect.SocketConnection;

import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.security.MessageDigest;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

import io.milton.config.HttpManagerBuilder;
import io.milton.http.Auth;
import io.milton.http.HttpManager;
import io.milton.http.Request;
import io.milton.http.Request.Method;
import io.milton.http.SecurityManager;
import io.milton.http.http11.Http11ResponseHandler;
import io.milton.http.http11.auth.DigestResponse;
import io.milton.simpleton.SslSimpletonServer;
import timber.log.Timber;
import ts.realms.m2git.core.network.mws.SecureStore.CaStore;
import ts.realms.m2git.core.network.mws.SecureStore.CredentialStore;
import ts.realms.m2git.core.network.mws.SecureStore.MasterKeyCipher;
import ts.realms.m2git.core.network.mws.SecureStore.PersistentCertStore;
import ts.realms.m2git.core.network.mws.fs.FileSystemResourceFactory;
import ts.realms.m2git.core.network.mws.fs.LocalCacheManager;
import ts.realms.m2git.core.network.mws.fs.SimplePropertyManager;

/**
 * WebDAV 服务器单例 + 其专属组件（硬化 TLS 服务器 / Digest 认证）。
 * <p>
 * TLS 信任模型：服务器叶证书由持久化根 CA 签发。
 * 外部客户端可通过根目录 HTML 页面下载根 CA（/__ca__.pem 或 /__ca__.crt），
 * 导入系统信任库后叶证书轮换对其透明。
 * 根证书下载路径与其它资源一样，须先通过 WebDAV Digest 认证（A 方案）。
 */
public class SimpleMiltonServer {

    private static volatile SimpleMiltonServer instance;
    private volatile int boundPort;
    private volatile HardenedSimpletonServer server;
    private CredentialStore credentialStore;
    private volatile boolean started = false;
    private volatile boolean exposeToLan = false;

    // 根 CA 与叶证书链（[0]=leaf, [1]=root CA）
    private CaStore caStore;
    private X509Certificate[] serverChain;

    private SimpleMiltonServer() {
    }

    public static SimpleMiltonServer getInstance() {
        if (instance == null) {
            synchronized (SimpleMiltonServer.class) {
                if (instance == null) instance = new SimpleMiltonServer();
            }
        }
        return instance;
    }

    private static String encodePem(X509Certificate cert) {
        try {
            String b64 = Base64.encodeToString(cert.getEncoded(), Base64.NO_WRAP);
            StringBuilder sb = new StringBuilder("-----BEGIN CERTIFICATE-----\n");
            for (int i = 0; i < b64.length(); i += 64) {
                int end = Math.min(i + 64, b64.length());
                sb.append(b64, i, end).append('\n');
            }
            return sb.append("-----END CERTIFICATE-----\n").toString();
        } catch (CertificateEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public int getPort() {
        return boundPort;
    }

    public boolean isExposeToLan() {
        return exposeToLan;
    }

    // ==================================================================
    // 构建
    // ==================================================================

    public void setExposeToLan(boolean expose) {
        this.exposeToLan = expose;
    }

    // ==================================================================
    // 生命周期
    // ==================================================================

    /**
     * 构建服务器。整段同步，避免 build/start 并发竞争。
     * 首次调用生成根 CA 并写入默认凭证；后续调用保留已有 CA 与凭证。
     * <p>
     * 若根 CA 或叶证书需要生成，首次启动会有 ECDSA 密钥生成开销；
     * 建议在后台线程调用，避免阻塞主线程。
     */
    public synchronized void build(Context appContext, String homeFolder, int port,
                                   String defaultUser, String defaultPassword) throws Exception {
        if (started || server != null) {
            throw new IllegalStateException("already built; call stop() first");
        }

        // Milton HTTP 日期头解析依赖默认 Locale；这是刻意的全局副作用。
        Locale.setDefault(Locale.ENGLISH);
        System.setProperty("java.net.preferIPv4Stack", "true");

        // 1) TEE 主密钥
        MasterKeyCipher cipher = MasterKeyCipher.get();

        // 2) 根 CA（生成一次，永久固定；文件损坏时才重建）
        CaStore ca = CaStore.load(appContext, cipher);

        // 3) 叶证书（由根 CA 签发；SAN 不匹配时自动重签）
        //    证书 SAN 包含全部 LAN IP，客户端连哪个接口都能通过 TLS 校验
        java.util.List<String> lanIps = LanAddress.listInet4Strings();
        PersistentCertStore certStore = PersistentCertStore.load(
            appContext, cipher, ca, lanIps.toArray(new String[0]));

        this.caStore = ca;
        this.serverChain = certStore.getChain();

        // 4) 凭证存储（首次写入，后续不覆盖）
        this.credentialStore = CredentialStore.load(appContext, cipher);
        if (!credentialStore.hasUser(defaultUser)) {
            credentialStore.setUserPassword(defaultUser, defaultPassword);
            Timber.tag("miltonServer").i("initial credential stored for %s", defaultUser);
        } else {
            Timber.tag("miltonServer").i("credential for %s already exists, kept", defaultUser);
        }

        // 5) SecurityManager
        SecurityManager sm = new StoreBackedSecurityManager(credentialStore);

        // 6) 资源工厂（注入根证书 PEM，供 /__ca__.pem 与 /__ca__.crt 下载）
        FileSystemResourceFactory rf = new FileSystemResourceFactory(
            new File(homeFolder), sm, "/");
        rf.setAllowDirectoryBrowsing(false);
        rf.setPropertyManager(new SimplePropertyManager(new LocalCacheManager()));
        rf.setCaCertPem(encodePem(ca.getCaCert()));

        // 7) HttpManager
        HttpManagerBuilder b = new HttpManagerBuilder();
        b.setResourceFactory(rf);
        b.setEnableQuota(true);
        b.setEnableFormAuth(false);
        b.setEnableBasicAuth(false);
        b.setEnableDigestAuth(true);

        // 8) 硬化 TLS 服务器
        server = new HardenedSimpletonServer(
            b.buildHttpManager(), b.getOuterWebdavResponseHandler(),
            100, 10, certStore.getSslContext());
        server.setHttpPort(port);
        server.setBindAddress(exposeToLan ? null : "127.0.0.1");
        this.boundPort = port;
        Timber.tag("miltonServer").i("built port=%d bind=%s",
            port, exposeToLan ? "*" : "127.0.0.1");
    }

    public void changeUserPassword(String user, String newPlaintext) throws Exception {
        if (credentialStore == null) throw new IllegalStateException("build() first");
        credentialStore.setUserPassword(user, newPlaintext);
    }

    public synchronized void start() {
        if (server == null) throw new IllegalStateException("build() first");
        if (started) return;
        try {
            server.start();
            started = true;
            Timber.tag("miltonServer").i("started");
        } catch (RuntimeException e) {
            // 启动失败 → 回到未构建状态，允许调用方 build() 重试
            try {
                server.stop();
            } catch (Exception ignored) {
            }
            resetState();
            throw e;
        }
    }

    public synchronized void stop() {
        if (server != null && started) {
            try {
                server.stop();
            } catch (Exception e) {
                Timber.w(e);
            }
        }
        resetState();
    }

    private void resetState() {
        started = false;
        server = null;
        caStore = null;
        serverChain = null;
        credentialStore = null;
    }

    // ==================================================================
    // 证书信息（供外部客户端导入根 CA）
    // ==================================================================

    public boolean isRunning() {
        return started && server != null;
    }

    /**
     * 根 CA 证书 —— 外部客户端导入用。
     */
    public X509Certificate getRootCaCertificate() {
        if (caStore == null) throw new IllegalStateException("build() first");
        return caStore.getCaCert();
    }

    /**
     * 服务器证书链 [leaf, root CA] —— 诊断 / 导出用。
     * TLS 握手时 server 仅发送 leaf。
     */
    public X509Certificate[] getServerChain() {
        if (serverChain == null) throw new IllegalStateException("build() first");
        return serverChain.clone();
    }

    /**
     * 根 CA 的 PEM —— 外部客户端导入根 CA 用。
     * <p>
     * 与 {@code /__ca__.pem} 下载的内容一致。
     */
    public String getCaCertPem() {
        return encodePem(getRootCaCertificate());
    }

    // ==================================================================
    // 嵌套：硬化版 simple 服务器（强制 TLS1.2/1.3 + AEAD 套件，可绑定地址）
    // ==================================================================
    public static class HardenedSimpletonServer extends SslSimpletonServer {
        private static final String[] ALLOWED_PROTOCOLS = {"TLSv1.3", "TLSv1.2"};
        private static final String[] ALLOWED_CIPHERS = {
            "TLS_AES_128_GCM_SHA256",
            "TLS_AES_256_GCM_SHA384",
            "TLS_CHACHA20_POLY1305_SHA256",
            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256",
        };

        private final SSLContext injectedContext;
        private final int numThreads;
        private String bindAddress = "127.0.0.1";

        public HardenedSimpletonServer(HttpManager httpManager,
                                       Http11ResponseHandler responseHandler,
                                       int capacity, int numThreads, SSLContext sslContext) {
            super(httpManager, responseHandler, capacity, numThreads);
            this.injectedContext = sslContext;
            this.numThreads = numThreads;
        }

        public void setBindAddress(String addr) {
            this.bindAddress = addr;
        }

        @Override
        protected SocketConnection initHttps(int port) {
            try {
                ContainerServer processor = new ContainerServer(this, this.numThreads);
                Server secure = new HardenedSecureProcessor(processor, injectedContext);
                SocketConnection ssl = new SocketConnection(secure);
                InetSocketAddress addr = (bindAddress != null)
                    ? new InetSocketAddress(InetAddress.getByName(bindAddress), port)
                    : new InetSocketAddress(port);
                ssl.connect(addr);
                Timber.tag("HttpsSrv").i("HTTPS listening on %s", addr);
                return ssl;
            } catch (BindException ex) {
                throw new RuntimeException("bind failed: " + port + " @ " + bindAddress, ex);
            } catch (IOException ex) {
                throw new RuntimeException("initHttps failed", ex);
            }
        }

        private record HardenedSecureProcessor(ContainerServer processor, SSLContext context)
            implements Server {

            private static void applyHardening(SSLEngine e) {
                e.setUseClientMode(false);
                SSLParameters p = e.getSSLParameters();
                p.setProtocols(filter(ALLOWED_PROTOCOLS, e.getSupportedProtocols()));
                p.setCipherSuites(filter(ALLOWED_CIPHERS, e.getSupportedCipherSuites()));
                e.setNeedClientAuth(false);
                e.setWantClientAuth(false);
                e.setSSLParameters(p);
            }

            private static String[] filter(String[] want, String[] have) {
                return Arrays.stream(want)
                    .filter(w -> Arrays.asList(have).contains(w))
                    .toArray(String[]::new);
            }

            @Override
            public void process(Socket pipeline) throws IOException {
                final SocketChannel ch = pipeline.getChannel();
                final Map<String, Object> attrs = new HashMap<>(pipeline.getAttributes());
                Socket secure = new Socket() {
                    private volatile SSLEngine engine;

                    @Override
                    public Map<String, Object> getAttributes() {
                        return attrs;
                    }

                    @Override
                    public SocketChannel getChannel() {
                        return ch;
                    }

                    @Override
                    public SSLEngine getEngine() {
                        SSLEngine e = engine;
                        if (e == null) {
                            synchronized (this) {
                                e = engine;
                                if (e == null) {
                                    e = context.createSSLEngine();
                                    applyHardening(e);
                                    engine = e;
                                }
                            }
                        }
                        return e;
                    }
                };
                processor.process(secure);
            }

            @Override
            public void stop() { /* no-op */ }
        }
    }

    // ==================================================================
    // 嵌套：只接受 Digest 认证的 SecurityManager。
    // ==================================================================
    public static class StoreBackedSecurityManager implements SecurityManager {
        private final CredentialStore store;

        public StoreBackedSecurityManager(CredentialStore store) {
            this.store = store;
        }

        private static String md5Hex(String input) throws Exception {
            byte[] digest = MessageDigest.getInstance("MD5")
                .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        }

        @Override
        public Object authenticate(String user, String password) {
            // Basic 认证路径明确拒绝
            return null;
        }

        @Override
        public Object authenticate(DigestResponse r) {
            try {
                String user = r.getUser();
                String ha1 = store.getHa1(user);
                if (ha1 == null) {
                    // 防用户枚举：对不存在用户做一次恒时比较
                    store.verifyHa1(user, DUMMY_HA1);
                    return null;
                }
                String qop = r.getQop();
                if ("auth-int".equalsIgnoreCase(qop)) {
                    Timber.tag("Auth").w("auth-int qop unsupported");
                    return null;
                }
                if (r.getRealm() != null && !store.getRealm().equals(r.getRealm())) {
                    return null;
                }
                String expected = calculateResponseDigest(r, ha1);
                String actual = r.getResponseDigest();
                if (expected == null || actual == null) return null;
                boolean ok = MessageDigest.isEqual(
                    expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                return ok ? user : null;
            } catch (Exception e) {
                Timber.tag("Auth").w(e, "digest auth failed");
                return null;
            }
        }

        /**
         * RFC 2617：
         * qop 存在: HA2 = MD5(method:uri), response = MD5(HA1:nonce:nc:cnonce:qop:HA2)
         * qop 不存在: response = MD5(HA1:nonce:HA2)
         */
        private String calculateResponseDigest(DigestResponse r, String ha1) throws Exception {
            String method = r.getMethod().name();
            String ha2 = md5Hex(method + ":" + r.getUri());
            String qop = r.getQop();
            if (qop != null && !qop.isEmpty()) {
                if (qop.toLowerCase().contains("auth-int")) return null;
                if (r.getNc() == null || r.getCnonce() == null) return null;
            }
            String input = (qop == null || qop.isEmpty())
                ? ha1 + ":" + r.getNonce() + ":" + ha2
                : ha1 + ":" + r.getNonce() + ":" + r.getNc() + ":"
                + r.getCnonce() + ":" + qop + ":" + ha2;
            return md5Hex(input);
        }

        @Override
        public boolean authorise(Request request, Method method, Auth auth,
                                 io.milton.resource.Resource resource) {
            return auth != null && auth.getTag() != null;
        }

        @Override
        public String getRealm(String host) {
            return store.getRealm();
        }

        @Override
        public boolean isDigestAllowed() {
            return true;
        }
    }
}
