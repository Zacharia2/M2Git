package ts.realms.m2git.core.network.mws;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import timber.log.Timber;

/**
 * 安全存储聚合类：Keystore 主密钥 + HA1 凭证 + TLS 根 CA + 服务器叶证书。
 * <p>
 * 信任模型：
 * <pre>
 *   Trust = RootCA(pinned by external clients, imported via getCaCertPem())
 *           --signed--> LeafCert(presented at TLS handshake)
 * </pre>
 * 磁盘布局：
 * <pre>
 *   files/ca_store.enc    = AES-GCM( PKCS12(CA 私钥 + CA 证书) )   ← 生成一次，永久固定
 *   files/tls_store.enc   = AES-GCM( PKCS12(叶私钥 + 叶证书) )     ← SAN 变化时由 CA 重签
 * </pre>
 * 两者均以 TEE 主密钥（{@link MasterKeyCipher}）为唯一密钥锚。
 */
public final class SecureStore {
    private SecureStore() {
    }

    // ==================================================================
    // 文件 IO 工具（CaStore / PersistentCertStore 共用）
    // ==================================================================
    private static byte[] readFile(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static void writeFile(File f, byte[] data) throws IOException {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(data);
            out.getFD().sync();
        }
    }

    // ==================================================================
    // MasterKeyCipher：Android Keystore AES-256-GCM 主密钥（TEE 锚点）。
    // ==================================================================
    public static final class MasterKeyCipher {
        private static final String KEYSTORE = "AndroidKeyStore";
        private static final String MASTER_ALIAS = "milton_master_key";
        private static final int GCM_TAG_BITS = 128;
        private static final int GCM_IV_BYTES = 12;

        private static volatile MasterKeyCipher instance;

        private final SecretKey masterKey;

        private MasterKeyCipher() throws Exception {
            KeyStore ks = KeyStore.getInstance(KEYSTORE);
            ks.load(null, null);
            if (!ks.containsAlias(MASTER_ALIAS)) {
                Timber.tag("MasterKey").i("generating master AES key in TEE...");
                KeyGenerator kg = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
                kg.init(new KeyGenParameterSpec.Builder(
                    MASTER_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(false)
                    .build());
                this.masterKey = kg.generateKey();
            } else {
                this.masterKey = (SecretKey) ks.getKey(MASTER_ALIAS, null);
            }
        }

        public static MasterKeyCipher get() throws Exception {
            if (instance == null) {
                synchronized (MasterKeyCipher.class) {
                    if (instance == null) {
                        instance = new MasterKeyCipher();
                    }
                }
            }
            return instance;
        }

        /**
         * 明文 → [12B IV || 密文 || 16B tag]
         */
        public byte[] encrypt(byte[] plaintext) throws Exception {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, masterKey);
            byte[] ct = c.doFinal(plaintext);
            byte[] iv = c.getParameters()
                .getParameterSpec(GCMParameterSpec.class).getIV();
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return out;
        }

        /**
         * 解密 [12B IV || 密文 || 16B tag]
         */
        public byte[] decrypt(byte[] sealed) throws Exception {
            if (sealed == null || sealed.length < GCM_IV_BYTES + GCM_TAG_BITS / 8) {
                throw new IllegalArgumentException("sealed data too short");
            }
            byte[] iv = new byte[GCM_IV_BYTES];
            System.arraycopy(sealed, 0, iv, 0, GCM_IV_BYTES);
            byte[] ct = new byte[sealed.length - GCM_IV_BYTES];
            System.arraycopy(sealed, GCM_IV_BYTES, ct, 0, ct.length);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return c.doFinal(ct);
        }
    }

    // ==================================================================
    // CredentialStore：HA1 凭证的加密持久化。
    // 磁盘上只存 AES-GCM(MD5(user:realm:password))，明文密码只在
    // setUserPassword 调用瞬间存在。
    // ==================================================================
    public static final class CredentialStore {
        /**
         * 恒时比较用的占位 HA1
         */
        public static final String DUMMY_HA1 = "00000000000000000000000000000000";

        private static final String PREFS = "milton_credentials";
        private static final String KEY_REALM = "realm";
        private static final String KEY_USERS = "users_sealed";

        private final SharedPreferences prefs;
        private final MasterKeyCipher cipher;
        private final String realm;
        private volatile Map<String, String> ha1Map;

        private CredentialStore(SharedPreferences p, MasterKeyCipher c, String realm) {
            this.prefs = p;
            this.cipher = c;
            this.realm = realm;
            this.ha1Map = loadSealed();
        }

        public static CredentialStore load(Context ctx, MasterKeyCipher cipher) throws Exception {
            SharedPreferences p = ctx.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String realm = p.getString(KEY_REALM, "User Files");
            p.edit().putString(KEY_REALM, realm).apply();
            return new CredentialStore(p, cipher, realm);
        }

        public void setUserPassword(String user, String plaintextPassword) throws Exception {
            String ha1 = computeHa1(user, plaintextPassword);
            Map<String, String> updated = new HashMap<>(ha1Map);
            updated.put(user, ha1);
            persistSealed(updated);
            this.ha1Map = updated;
            Timber.tag("CredStore").i("stored HA1 for user=%s", user);
        }

        public void removeUser(String user) throws Exception {
            Map<String, String> updated = new HashMap<>(ha1Map);
            updated.remove(user);
            persistSealed(updated);
            this.ha1Map = updated;
        }

        public boolean hasUser(String user) {
            return ha1Map.containsKey(user);
        }

        public String getRealm() {
            return realm;
        }

        public String getHa1(String user) {
            return ha1Map.get(user);
        }

        public boolean verifyHa1(String user, String candidateHa1) {
            String expected = ha1Map.get(user);
            byte[] a = (expected != null ? expected : DUMMY_HA1).getBytes(StandardCharsets.UTF_8);
            byte[] b = (candidateHa1 != null ? candidateHa1 : "").getBytes(StandardCharsets.UTF_8);
            boolean equal = MessageDigest.isEqual(a, b);
            return expected != null && equal;
        }

        public String computeHa1(String user, String plaintextPassword) throws Exception {
            String src = user + ":" + realm + ":" + plaintextPassword;
            byte[] d = MessageDigest.getInstance("MD5").digest(src.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte x : d) sb.append(String.format("%02x", x));
            return sb.toString();
        }

        private Map<String, String> loadSealed() {
            Map<String, String> result = new HashMap<>();
            String json = prefs.getString(KEY_USERS, null);
            if (json == null) return result;
            try {
                JSONObject obj = new JSONObject(json);
                Iterator<String> it = obj.keys();
                while (it.hasNext()) {
                    String user = it.next();
                    byte[] sealed = Base64.decode(obj.getString(user), Base64.NO_WRAP);
                    byte[] ha1Bytes = cipher.decrypt(sealed);
                    result.put(user, new String(ha1Bytes, StandardCharsets.UTF_8));
                    Arrays.fill(ha1Bytes, (byte) 0);
                }
            } catch (Exception e) {
                Timber.tag("CredStore").w(e, "decrypt failed, clearing");
                prefs.edit().remove(KEY_USERS).apply();
            }
            return result;
        }

        private void persistSealed(Map<String, String> ha1s) throws Exception {
            JSONObject obj = new JSONObject();
            for (Map.Entry<String, String> e : ha1s.entrySet()) {
                byte[] sealed = cipher.encrypt(e.getValue().getBytes(StandardCharsets.UTF_8));
                obj.put(e.getKey(), Base64.encodeToString(sealed, Base64.NO_WRAP));
            }
            if (!prefs.edit().putString(KEY_USERS, obj.toString()).commit()) {
                throw new IOException("persist credentials failed");
            }
        }
    }

    // ==================================================================
    // CaStore：根 CA 的加密持久化。
    // 生命周期：首次启动生成，此后永久固定，绝不轮换。
    // 仅当文件内容损坏（解密失败 / PKCS12 格式错）时重建；
    // 读文件 IO 抖动向上抛；内容可疑（BasicConstraints 缺失）向上抛。
    // 密钥算法：ECDSA P-384，签名算法：SHA384withECDSA。
    // ==================================================================
    public static final class CaStore {
        private static final String FILE_NAME = "ca_store.enc";
        private static final char[] P12_PASS = "ca-root-internal".toCharArray();
        private static final String KEY_ALIAS = "milton_root_ca";
        private static final Object LOAD_LOCK = new Object();
        private static final SecureRandom RNG = new SecureRandom();

        private final X509Certificate caCert;
        private final PrivateKey caKey;

        private CaStore(X509Certificate caCert, PrivateKey caKey) {
            this.caCert = caCert;
            this.caKey = caKey;
        }

        public static CaStore load(Context ctx, MasterKeyCipher cipher) throws Exception {
            File f = new File(ctx.getApplicationContext().getFilesDir(), FILE_NAME);
            synchronized (LOAD_LOCK) {
                if (!f.exists()) {
                    CaStore fresh = generate();
                    fresh.save(f, cipher);
                    Timber.tag("CaStore").i("root CA generated");
                    return fresh;
                }
                byte[] sealed;
                try {
                    sealed = readFile(f);
                } catch (IOException e) {
                    // 读文件失败 → 可能是 IO 抖动，上抛，下次调用再试
                    throw e;
                }
                try {
                    return loadFromSealed(sealed, cipher);
                } catch (IllegalStateException e) {
                    // BasicConstraints 缺失 / 缺 key-cert → 内容可疑，不静默重建
                    throw e;
                } catch (Exception e) {
                    // 解密失败（AEADBadTag）/ PKCS12 格式错 → 内容损坏，重建
                    Timber.tag("CaStore").w(e, "CA content corrupt, regenerating");
                    CaStore fresh = generate();
                    fresh.save(f, cipher);
                    return fresh;
                }
            }
        }

        private static CaStore loadFromSealed(byte[] sealed, MasterKeyCipher cipher) throws Exception {
            byte[] p12 = cipher.decrypt(sealed);
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (InputStream in = new ByteArrayInputStream(p12)) {
                ks.load(in, P12_PASS);
            } finally {
                Arrays.fill(p12, (byte) 0);
            }
            PrivateKey key = (PrivateKey) ks.getKey(KEY_ALIAS, P12_PASS);
            X509Certificate cert = (X509Certificate) ks.getCertificate(KEY_ALIAS);
            if (key == null || cert == null) {
                throw new IllegalStateException("CA PKCS12 missing key/cert");
            }
            if (cert.getBasicConstraints() < 0) {
                throw new IllegalStateException(
                    "stored CA cert is not a CA (BasicConstraints missing)");
            }
            return new CaStore(cert, key);
        }

        private static CaStore generate() throws Exception {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new ECGenParameterSpec("secp384r1"), RNG);
            KeyPair kp = kpg.generateKeyPair();
            X509Certificate cert = selfSignRoot(kp);
            return new CaStore(cert, kp.getPrivate());
        }

        // ---------- private ----------

        private static X509Certificate selfSignRoot(KeyPair kp) throws Exception {
            X500Name subject = new X500Name("CN=M2Git Milton Root CA, O=M2Git, C=CN");
            Date notBefore = new Date(System.currentTimeMillis() - 24L * 3600 * 1000);
            Date notAfter = new Date(System.currentTimeMillis() + 7300L * 24 * 3600 * 1000); // 20 年

            JcaX509v3CertificateBuilder b = new JcaX509v3CertificateBuilder(
                subject, serial(), notBefore, notAfter, subject, kp.getPublic());

            JcaX509ExtensionUtils ext = new JcaX509ExtensionUtils();
            b.addExtension(Extension.subjectKeyIdentifier, false,
                ext.createSubjectKeyIdentifier(kp.getPublic()));
            b.addExtension(Extension.authorityKeyIdentifier, false,
                ext.createAuthorityKeyIdentifier(kp.getPublic()));
            b.addExtension(Extension.basicConstraints, true,
                new BasicConstraints(true)); // CA:TRUE
            b.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));

            ContentSigner signer = new JcaContentSignerBuilder("SHA384withECDSA")
                .build(kp.getPrivate());
            return new JcaX509CertificateConverter().getCertificate(b.build(signer));
        }

        /**
         * 128 位非负随机序列号（RFC 5280 要求非负）
         */
        private static BigInteger serial() {
            return new BigInteger(128, RNG);
        }

        public X509Certificate getCaCert() {
            return caCert;
        }

        /**
         * 由根 CA 签发服务器叶证书。叶密钥对独立于 CA 密钥对。
         */
        public X509Certificate issueLeaf(PublicKey leafPublicKey, String[] sanIps) throws Exception {
            // 用 DER 编码构造 issuer，避免 RFC 2253 字符串再解析导致 DN 字节漂移
            X500Name issuer = X500Name.getInstance(
                caCert.getSubjectX500Principal().getEncoded());
            X500Name subject = new X500Name("CN=M2Git Milton WebDAV, O=M2Git, C=CN");
            Date notBefore = new Date(System.currentTimeMillis() - 24L * 3600 * 1000);
            Date notAfter = new Date(System.currentTimeMillis() + 825L * 24 * 3600 * 1000);

            JcaX509v3CertificateBuilder b = new JcaX509v3CertificateBuilder(
                issuer, serial(), notBefore, notAfter, subject, leafPublicKey);

            JcaX509ExtensionUtils ext = new JcaX509ExtensionUtils();
            b.addExtension(Extension.subjectKeyIdentifier, false,
                ext.createSubjectKeyIdentifier(leafPublicKey));
            b.addExtension(Extension.authorityKeyIdentifier, false,
                ext.createAuthorityKeyIdentifier(caCert));
            b.addExtension(Extension.basicConstraints, true,
                new BasicConstraints(false));
            // ECDSA 叶密钥只做签名（ECDHE），keyEncipherment 无意义
            b.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.digitalSignature));
            b.addExtension(Extension.extendedKeyUsage, false,
                new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));

            Set<String> seen = new LinkedHashSet<>();
            List<GeneralName> sans = new ArrayList<>();
            seen.add("dns:localhost");
            sans.add(new GeneralName(GeneralName.dNSName, "localhost"));
            seen.add("ip:127.0.0.1");
            sans.add(new GeneralName(GeneralName.iPAddress, "127.0.0.1"));
            if (sanIps != null) {
                for (String ip : sanIps) {
                    if (ip != null && !ip.isEmpty() && seen.add("ip:" + ip)) {
                        sans.add(new GeneralName(GeneralName.iPAddress, ip));
                    }
                }
            }
            b.addExtension(Extension.subjectAlternativeName, false,
                new GeneralNames(sans.toArray(new GeneralName[0])));

            ContentSigner signer = new JcaContentSignerBuilder("SHA384withECDSA")
                .build(caKey);
            return new JcaX509CertificateConverter().getCertificate(b.build(signer));
        }

        private void save(File f, MasterKeyCipher cipher) throws Exception {
            KeyStore p12 = KeyStore.getInstance("PKCS12");
            p12.load(null, null);
            p12.setKeyEntry(KEY_ALIAS, caKey, P12_PASS,
                new Certificate[]{caCert});
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            p12.store(bos, P12_PASS);
            byte[] plaintext = bos.toByteArray();
            byte[] sealed = cipher.encrypt(plaintext);
            Arrays.fill(plaintext, (byte) 0);
            File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
            writeFile(tmp, sealed);
            if (!tmp.renameTo(f)) {
                f.delete();
                tmp.renameTo(f);
            }
            Timber.tag("CaStore").i("CA persisted (%d bytes encrypted)", sealed.length);
        }
    }

    // ==================================================================
    // PersistentCertStore：服务器叶证书的加密持久化。
    // 文件格式：files/tls_store.enc = AES-GCM( PKCS12(叶私钥 + 叶证书) )
    // 叶证书由 CaStore 签发；SAN 不匹配时由 CA 重签（复用叶密钥对）。
    // 叶密钥算法：ECDSA P-256。TLS 握手中 server 仅发 [leaf]。
    // ==================================================================
    public static final class PersistentCertStore {
        private static final String FILE_NAME = "tls_store.enc";
        private static final char[] PKCS12_PASS = "p12-internal".toCharArray();
        private static final String KEY_ALIAS = "milton_https";
        private static final Object LOAD_LOCK = new Object();
        private static final SecureRandom RNG = new SecureRandom();

        private X509Certificate[] chain;   // [0]=leaf, [1]=root CA
        private SSLContext sslContext;

        private PersistentCertStore() {
        }

        public static PersistentCertStore load(Context ctx, MasterKeyCipher cipher,
                                               CaStore caStore, String[] lanIps) throws Exception {
            synchronized (LOAD_LOCK) {
                PersistentCertStore s = new PersistentCertStore();
                File f = new File(ctx.getApplicationContext().getFilesDir(), FILE_NAME);
                if (!f.exists()) {
                    s.generateAndSave(f, cipher, caStore, lanIps);
                    return s;
                }
                byte[] sealed;
                try {
                    sealed = readFile(f);
                } catch (IOException e) {
                    throw e;
                }
                try {
                    s.loadFromSealed(f, sealed, cipher, caStore, lanIps);
                } catch (Exception e) {
                    Timber.tag("CertStore").w(e, "leaf unusable, regenerating with current CA");
                    s.generateAndSave(f, cipher, caStore, lanIps);
                }
                return s;
            }
        }

        private static boolean sanCoversAll(X509Certificate cert, String[] requiredIps) {
            if (requiredIps == null || requiredIps.length == 0) return true;
            try {
                Collection<List<?>> sans = cert.getSubjectAlternativeNames();
                if (sans == null) return false;
                Set<String> have = new HashSet<>();
                for (List<?> san : sans) {
                    Object v = san.get(1);
                    if (v != null) have.add(v.toString());
                }
                for (String ip : requiredIps) {
                    if (ip != null && !ip.isEmpty() && !have.contains(ip)) return false;
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        public SSLContext getSslContext() {
            return sslContext;
        }

        // ---------- private ----------

        /**
         * 不可变视图：返回 [leaf, root CA] 的副本
         */
        public X509Certificate[] getChain() {
            return chain.clone();
        }

        private void loadFromSealed(File f, byte[] sealed, MasterKeyCipher cipher,
                                    CaStore caStore, String[] lanIps) throws Exception {
            byte[] p12 = cipher.decrypt(sealed);
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (InputStream in = new ByteArrayInputStream(p12)) {
                ks.load(in, PKCS12_PASS);
            } finally {
                Arrays.fill(p12, (byte) 0);
            }
            PrivateKey priv = (PrivateKey) ks.getKey(KEY_ALIAS, PKCS12_PASS);
            X509Certificate leaf = (X509Certificate) ks.getCertificate(KEY_ALIAS);
            if (priv == null || leaf == null) {
                throw new IllegalStateException("PKCS12 missing leaf key/cert");
            }

            X509Certificate ca = caStore.getCaCert();
            if (!leaf.getIssuerX500Principal().equals(ca.getSubjectX500Principal())) {
                throw new SecurityException("leaf issuer does not match current root CA");
            }
            leaf.verify(ca.getPublicKey());

            if (!sanCoversAll(leaf, lanIps)) {
                Timber.tag("CertStore").i("SAN mismatch, reissuing leaf (keeping key)");
                X509Certificate reissued = caStore.issueLeaf(leaf.getPublicKey(), lanIps);
                generateAndSaveInternal(f, cipher, caStore, priv, reissued);
                return;
            }

            this.chain = new X509Certificate[]{leaf, ca};
            buildSslContext(priv, leaf);
            Timber.tag("CertStore").i("loaded leaf chain: %s",
                leaf.getSubjectX500Principal());
        }

        private void generateAndSave(File f, MasterKeyCipher cipher,
                                     CaStore caStore, String[] lanIps) throws Exception {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new ECGenParameterSpec("secp256r1"), RNG);
            KeyPair kp = kpg.generateKeyPair();
            X509Certificate leaf = caStore.issueLeaf(kp.getPublic(), lanIps);
            generateAndSaveInternal(f, cipher, caStore, kp.getPrivate(), leaf);
        }

        private void generateAndSaveInternal(File f, MasterKeyCipher cipher,
                                             CaStore caStore, PrivateKey priv,
                                             X509Certificate leaf) throws Exception {
            this.chain = new X509Certificate[]{leaf, caStore.getCaCert()};
            buildSslContext(priv, leaf);

            KeyStore p12 = KeyStore.getInstance("PKCS12");
            p12.load(null, null);
            p12.setKeyEntry(KEY_ALIAS, priv, PKCS12_PASS,
                new Certificate[]{leaf});   // PKCS12 只存 leaf，CA 独立存储
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            p12.store(bos, PKCS12_PASS);
            byte[] plaintext = bos.toByteArray();
            byte[] sealed = cipher.encrypt(plaintext);
            Arrays.fill(plaintext, (byte) 0);
            File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
            writeFile(tmp, sealed);
            if (!tmp.renameTo(f)) {
                f.delete();
                tmp.renameTo(f);
            }
            Timber.tag("CertStore").i("leaf persisted (%d bytes, chain=[leaf,root])",
                sealed.length);
        }

        /**
         * 只把叶证书发给客户端；root CA 由客户端 truststore 提供。
         */
        private void buildSslContext(PrivateKey priv, X509Certificate leaf) throws Exception {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, null);
            ks.setKeyEntry(KEY_ALIAS, priv, PKCS12_PASS,
                new Certificate[]{leaf});
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, PKCS12_PASS);
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, null);
        }
    }
}
