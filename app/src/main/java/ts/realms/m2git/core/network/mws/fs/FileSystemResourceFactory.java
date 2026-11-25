/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package ts.realms.m2git.core.network.mws.fs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

import io.milton.http.LockManager;
import io.milton.http.PropertyManager;
import io.milton.http.ResourceFactory;
import io.milton.http.WSManager;
import io.milton.resource.Resource;

/**
 * Android 局域网文件服务定制版。
 * <p>
 * 相对上游 milton fs 的差异（Android 场景裁剪）：
 * - segment 校验只保留跨平台都非法的内容（NUL / '/' / '\\' / 控制字符），
 * Windows 保留设备名、冒号、结尾点空格等规则已删除（Android 文件系统无此约束）；
 * - realpath 级 root 包含校验保留（GET 通道兜底，成本极低）；
 * - 新增根证书下载路径（{@code /__ca__.pem} / {@code /__ca__.crt}）拦截，
 * 走与其它资源一致的 SecurityManager 认证。
 */
public class FileSystemResourceFactory implements ResourceFactory {

    // RFC 3986 path segment 中除 unreserved 外仍合法的字符；其余一律 percent-encode
    public static final String SEGMENT_SAFE = "-._~!$&'()*+,;=@";

    private static final Logger log = LoggerFactory.getLogger(FileSystemResourceFactory.class);

    File root;
    io.milton.http.SecurityManager securityManager;
    LockManager lockManager;
    PropertyManager propertyManager;
    Long maxAgeSeconds;
    String contextPath;
    boolean allowDirectoryBrowsing;
    String defaultPage;
    boolean digestAllowed = true;
    private FileContentService contentService = new SimpleFileContentService();
    private WSManager wsManager;
    private String ssoPrefix;
    /**
     * 根证书 PEM；null 表示未配置，证书下载路径不被拦截
     */
    private String caCertPem;

    public FileSystemResourceFactory() {
        log.debug("setting default configuration...");
        String sRoot = System.getProperty("user.home");
        io.milton.http.SecurityManager sm = new NullSecurityManager();
        init(sRoot, sm);
    }

    public FileSystemResourceFactory(File root, io.milton.http.SecurityManager securityManager) {
        setRoot(root);
        setSecurityManager(securityManager);
    }

    public FileSystemResourceFactory(File root, io.milton.http.SecurityManager securityManager, String contextPath) {
        setRoot(root);
        setSecurityManager(securityManager);
        setContextPath(contextPath);
    }

    /**
     * 公开给子资源使用（createCollection / createNew / moveTo / copyTo）。
     * 对 "." / ".." 与 resolvePath 策略一致（放行），创建类操作须配合 resolveChild 使用。
     */
    public static void validateName(String name) {
        validateSegment(name, name);
    }

    /**
     * segment 级合法性校验。Android 场景只拦真正跨平台非法的内容：
     * - NUL：任何文件系统都非法；
     * - '/' / '\\'：分隔符注入；
     * - C0 控制字符：Linux 文件名虽允许，但会污染 HTML 目录列表与 PROPFIND XML。
     * "." / ".." 由 resolvePath / resolveChild 显式处理。
     */
    private static void validateSegment(String part, String original) {
        if (part == null || part.isEmpty()) {
            throw new IllegalArgumentException("Empty path segment: " + original);
        }
        if (part.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("NUL byte in segment: " + original);
        }
        if (part.indexOf('/') >= 0) {
            throw new IllegalArgumentException("Segment contains '/'" + original);
        }
        if (part.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Segment contains backslash: " + original);
        }
        for (int i = 0; i < part.length(); i++) {
            if (part.charAt(i) < 0x20) {
                throw new IllegalArgumentException("Control character in segment: " + original);
            }
        }
    }

    public static String encodePath(String uri) {
        String[] parts = uri.split("/", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(encodeSegment(parts[i]));
            }
            if (i < parts.length - 1) {
                sb.append('/');
            }
        }
        return sb.toString();
    }

    public static String encodeSegment(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xff);
            boolean safe = (c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')
                || SEGMENT_SAFE.indexOf(c) >= 0;
            if (safe) {
                sb.append(c);
            } else {
                sb.append('%');
                sb.append(String.format("%02X", b));
            }
        }
        return sb.toString();
    }

    private void init(String sRoot, io.milton.http.SecurityManager securityManager) {
        setRoot(new File(sRoot));
        setSecurityManager(securityManager);
    }

    public File getRoot() {
        return root;
    }

    public void setRoot(File root) {
        log.debug("root: {}", root.getAbsolutePath());
        this.root = root;
        if (!root.exists()) {
            log.warn("Root folder does not exist: {}", root.getAbsolutePath());
        }
        if (!root.isDirectory()) {
            log.warn("Root exists but is not a directory: {}", root.getAbsolutePath());
        }
        log.info("FileSystemResourceFactory init: root={}", root.getAbsolutePath());
    }

    @Override
    public Resource getResource(String host, String url) {
        log.debug("getResource: host: {} - url:{}", host, url);
        url = stripContext(url);

        // 根证书下载路径优先拦截（不进入文件系统解析）。
        // 访问权限与其它资源一致，由 SecurityManager 统一裁决（A 方案：需先通过 Digest 认证）。
        if (hasCaCertPem() && url != null) {
            if ("/__ca__.pem".equals(url)) {
                return new CaCertDownloadResource(host, this, caCertPem,
                    "milton-root-ca.pem", "application/x-pem-file");
            }
            if ("/__ca__.crt".equals(url)) {
                return new CaCertDownloadResource(host, this, caCertPem,
                    "milton-root-ca.crt", "application/x-x509-ca-cert");
            }
        }

        File requested;
        try {
            requested = resolvePath(root, url);
        } catch (IllegalArgumentException e) {
            // 畸形 segment（含 '/' / '\\' / 控制字符 / NUL 等）一律按 404 处理
            log.warn("Rejecting malformed url: {} ({})", url, e.getMessage());
            return null;
        }
        if (requested == null || !isUnderRoot(requested)) {
            log.warn("Requested path is null or escapes root, denying: {}", url);
            return null;
        }
        return resolveFile(host, requested);
    }

    /**
     * GET 通道的兜底防线（realpath 级）。
     * Android 上 WebDAV 协议无法创建 symlink、App 也不能在共享存储建链接，
     * 创建类通道的越界风险可忽略，此处保留仅作为 GET 的低成本防御。
     */
    public FsResource resolveFile(String host, File file) {
        if (!isUnderRoot(file)) {
            log.warn("Resolved file escapes root, denying: {}", file.getAbsolutePath());
            return null;
        }
        FsResource r;
        if (!file.exists()) {
            log.debug("file not found: {}", file.getAbsolutePath());
            return null;
        } else if (file.isDirectory()) {
            r = new FsDirectoryResource(host, this, file, contentService);
        } else {
            r = new FsFileResource(host, this, file, contentService);
        }
        if (r != null) {
            r.ssoPrefix = ssoPrefix;
        }
        return r;
    }

    /**
     * URL 由 mws 适配层解码为文件名 segment 序列后按字面量逐段处理。
     * 注意：这里不做 percent-decode——创建类通道（createCollection/createNew/moveTo/copyTo
     * 经 resolveChild）拿到的已是解码后名字，GET 通道同理，任何一处再 decode
     * 都会把文件名里的字面量 '%' 二次解码成错误路径。
     */
    public File resolvePath(File root, String url) {
        io.milton.common.Path path = io.milton.common.Path.path(url);
        Deque<String> parts = new ArrayDeque<>();
        for (String raw : path.getParts()) {
            validateSegment(raw, raw);
            if (".".equals(raw)) {
                continue;
            }
            if ("..".equals(raw)) {
                if (parts.isEmpty()) {
                    return null; // 越出 root，按 404 处理
                }
                parts.pollLast();
                continue;
            }
            parts.addLast(raw);
        }
        File f = root;
        for (String p : parts) {
            f = new File(f, p);
        }
        return f;
    }

    public File resolveChild(File parentDir, String name) {
        validateName(name);
        File f = resolvePath(parentDir, name);
        if (f == null) {
            // resolvePath 对 ".." 越出父目录（deque 弹空）返回 null
            throw new IllegalArgumentException("Name escapes parent directory: " + name);
        }
        if (f.equals(parentDir)) {
            // 名字为 "."（或经折叠后落到父目录本身）
            throw new IllegalArgumentException("Name resolves to the directory itself: " + name);
        }
        return f;
    }

    /**
     * realpath 级前缀校验。
     * - toRealPath 解析符号链接：root 内 symlink 指向 root 外时不再被放行；
     * - 文件不存在（GET 404 场景）时 toRealPath 抛 IOException，退回词法级
     * normalize + startsWith 比较，仍能挡住 ".." 注入。
     */
    private boolean isUnderRoot(File f) {
        try {
            Path rootReal = root.getAbsoluteFile().toPath().toRealPath();
            Path fileReal = f.getAbsoluteFile().toPath().toRealPath();
            return fileReal.startsWith(rootReal);
        } catch (IOException e) {
            // 文件不存在或无法解析：退回词法比较
            Path rootPath = root.getAbsoluteFile().toPath().normalize();
            Path filePath = f.getAbsoluteFile().toPath().normalize();
            return filePath.startsWith(rootPath);
        }
    }

    /**
     * 先 toRealPath（解析 symlink）再 relativize。
     * 旧实现若 file 路径含 ".."，relativize 会抛 IllegalArgumentException 或产出含 ".." 的结果，
     * 进而污染 WSManager 的事件路径。
     */
    public String toResourcePath(File file) {
        try {
            Path rootReal = root.getAbsoluteFile().toPath().toRealPath();
            Path fileReal = file.getAbsoluteFile().toPath().toRealPath();
            if (!fileReal.startsWith(rootReal)) {
                throw new IllegalArgumentException("File is outside root: " + file.getAbsolutePath());
            }
            return rootReal.relativize(fileReal).toString().replace(File.separatorChar, '/');
        } catch (IOException e) {
            // 文件不存在（如刚被删除）时退回词法计算
            Path rootPath = root.getAbsoluteFile().toPath().normalize();
            Path filePath = file.getAbsoluteFile().toPath().normalize();
            if (!filePath.startsWith(rootPath)) {
                throw new IllegalArgumentException("File is outside root: " + file.getAbsolutePath());
            }
            return rootPath.relativize(filePath).toString().replace(File.separatorChar, '/');
        }
    }

    public String getRealm(String host) {
        String s = securityManager.getRealm(host);
        if (s == null) {
            throw new NullPointerException("Got null realm from securityManager: "
                + securityManager + " for host=" + host);
        }
        return s;
    }

    public Long maxAgeSeconds(FsResource resource) {
        return maxAgeSeconds;
    }

    public io.milton.http.SecurityManager getSecurityManager() {
        return securityManager;
    }

    public void setSecurityManager(io.milton.http.SecurityManager securityManager) {
        if (securityManager != null) {
            log.debug("securityManager: {}", securityManager.getClass());
        } else {
            log.warn("Setting null FsSecurityManager. This WILL cause null pointer exceptions");
        }
        this.securityManager = securityManager;
    }

    public Long getMaxAgeSeconds() {
        return maxAgeSeconds;
    }

    public void setMaxAgeSeconds(Long maxAgeSeconds) {
        this.maxAgeSeconds = maxAgeSeconds;
    }

    public LockManager getLockManager() {
        return lockManager;
    }

    public void setLockManager(LockManager lockManager) {
        this.lockManager = lockManager;
    }

    public PropertyManager getPropertyManager() {
        return propertyManager;
    }

    public void setPropertyManager(PropertyManager propertyManager) {
        this.propertyManager = propertyManager;
    }

    public String getContextPath() {
        return contextPath;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public boolean isAllowDirectoryBrowsing() {
        return allowDirectoryBrowsing;
    }

    public void setAllowDirectoryBrowsing(boolean allowDirectoryBrowsing) {
        this.allowDirectoryBrowsing = allowDirectoryBrowsing;
    }

    public String getDefaultPage() {
        return defaultPage;
    }

    public void setDefaultPage(String defaultPage) {
        this.defaultPage = defaultPage;
    }

    /**
     * 旧实现 replaceFirst('/' + contextPath, "") 未锚定行首：URL /foo/webdav/bar 会误删中间的 /webdav；
     * 且 contextPath 含正则元字符(如 '.')时会抛 PatternSyntaxException。
     * 改为精确前缀匹配，仅剥去行首的 context。
     */
    private String stripContext(String url) {
        if (this.contextPath != null && !contextPath.isEmpty()) {
            String prefix = "/" + contextPath;
            if (url.equals(prefix)) {
                url = "/";
            } else if (url.startsWith(prefix + "/")) {
                url = url.substring(prefix.length());
            }
        }
        return url;
    }

    boolean isDigestAllowed() {
        boolean b = digestAllowed && securityManager != null && securityManager.isDigestAllowed();
        if (log.isTraceEnabled()) {
            log.trace("isDigestAllowed: {}", b);
        }
        return b;
    }

    public void setDigestAllowed(boolean digestAllowed) {
        this.digestAllowed = digestAllowed;
    }

    public String getSsoPrefix() {
        return ssoPrefix;
    }

    public void setSsoPrefix(String ssoPrefix) {
        this.ssoPrefix = ssoPrefix;
    }

    public FileContentService getContentService() {
        return contentService;
    }

    public void setContentService(FileContentService contentService) {
        this.contentService = contentService;
    }

    public Optional<WSManager> getWsManager() {
        return Optional.ofNullable(wsManager);
    }

    public void setWsManager(WSManager wsManager) {
        this.wsManager = wsManager;
    }

    /**
     * 注入根证书 PEM。设为 null 或空字符串时，{@code /__ca__.pem} / {@code /__ca__.crt}
     * 不被拦截（按普通路径解析，通常 404）。
     * <p>
     * 由 {@code SimpleMiltonServer.build()} 在根 CA 加载完成后调用。
     */
    public void setCaCertPem(String caCertPem) {
        this.caCertPem = caCertPem;
    }

    public boolean hasCaCertPem() {
        return caCertPem != null && !caCertPem.isEmpty();
    }
}
