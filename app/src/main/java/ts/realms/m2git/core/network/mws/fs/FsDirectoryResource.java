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

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.milton.http.Auth;
import io.milton.http.Range;
import io.milton.http.Request;
import io.milton.http.XmlWriter;
import io.milton.http.exceptions.NotAuthorizedException;
import io.milton.resource.CollectionResource;
import io.milton.resource.CopyableResource;
import io.milton.resource.DeletableResource;
import io.milton.resource.GetableResource;
import io.milton.resource.MakeCollectionableResource;
import io.milton.resource.MoveableResource;
import io.milton.resource.PropFindableResource;
import io.milton.resource.PutableResource;
import io.milton.resource.QuotaResource;
import io.milton.resource.Resource;

public class FsDirectoryResource extends FsResource implements MakeCollectionableResource, PutableResource,
    CopyableResource, DeletableResource, MoveableResource, PropFindableResource, GetableResource, QuotaResource {

    private static final Logger log = LoggerFactory.getLogger(FsDirectoryResource.class);


    private final FileContentService contentService;

    public FsDirectoryResource(String host, FileSystemResourceFactory factory, File dir, FileContentService contentService) {
        super(host, factory, dir);
        this.contentService = contentService;
        if (!dir.exists()) {
            throw new IllegalArgumentException("Directory does not exist: " + dir.getAbsolutePath());
        }
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException("Is not a directory: " + dir.getAbsolutePath());
        }
    }

    /**
     * 旧实现假设 abUrl 是 "http://host/..." 形式的绝对 URL（indexOf("/", 8)），
     * 但 buildHref 传入的是相对路径（如 "/mydir/"）——路径短于 8 字符时 substring(0, -1)
     * 直接抛 StringIndexOutOfBoundsException，路径较长时 prefix 会被插到路径中间。
     * 现按相对/绝对分别处理。
     */
    public static String insertSsoPrefix(String abUrl, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return abUrl;
        }
        if (abUrl.startsWith("/")) {
            // 相对路径：直接前缀化
            return "/" + prefix + abUrl;
        }
        // 绝对 URL：插在 host 与 path 之间
        int pos = abUrl.indexOf("/", 8);
        if (pos < 0) {
            return abUrl + "/" + prefix;
        }
        return abUrl.substring(0, pos) + "/" + prefix + abUrl.substring(pos);
    }

    // HTML 实体转义——Milton 的 XmlWriter.writeText 不做任何转义
    private static String htmlEscape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append("&#34;");
                    break;
                case '\'':
                    sb.append("&#39;");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 名称校验 + 经 resolveChild 显式处理 "." / ".."；原子创建，已存在时给出明确错误。
     */
    @Override
    public CollectionResource createCollection(String name) {
        FileSystemResourceFactory.validateName(name);
        File fnew = factory.resolveChild(file, name);
        try {
            Files.createDirectory(fnew.toPath());
        } catch (FileAlreadyExistsException e) {
            throw new RuntimeException("Collection already exists: " + fnew.getAbsolutePath(), e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create: " + fnew.getAbsolutePath(), e);
        }
        factory.getWsManager().ifPresent(wsManager -> wsManager.notifyCreated(factory.toResourcePath(fnew)));
        return new FsDirectoryResource(host, factory, fnew, contentService);
    }

    /**
     * 名称校验 + resolvePath（含 segment 校验、"."/".." 处理），
     * resolveFile 内部的 root 包含校验再做一层兜底。任何非法/越界 name 一律按"不存在"返回 null。
     */
    @Override
    public Resource child(String name) {
        // 与 resolveChild 语义对齐："." / ".." 不作为子资源暴露，
        // 否则 child(".") 会返回目录自身，与创建路径的行为不一致
        if (".".equals(name) || "..".equals(name)) {
            return null;
        }
        File fchild;
        try {
            // Milton 传入的是已解码名字，resolvePath 不做 percent-decode
            // （见 FileSystemResourceFactory.resolvePath javadoc）
            fchild = factory.resolvePath(this.file, name);
        } catch (IllegalArgumentException e) {
            log.warn("Rejecting malformed child name: {} ({})", name, e.getMessage());
            return null;
        }
        if (fchild == null) {
            return null; // ".." 越出本目录
        }
        return factory.resolveFile(this.host, fchild);
    }

    @Override
    public List<? extends Resource> getChildren() {
        ArrayList<FsResource> list = new ArrayList<>();
        File[] files = Arrays.stream(Optional.ofNullable(this.file.listFiles()).orElse(new File[0]))
            // 过滤原子覆盖写产生的临时文件：客户端列表不可见、不可下载半成品
            .filter(f -> !(f.getName().startsWith(FileContentService.TMP_PREFIX) && f.getName().endsWith(FileContentService.TMP_SUFFIX)))
            .sorted(Comparator.comparing(File::isDirectory).reversed().thenComparing(File::getName))
            .toArray(File[]::new);
        for (File fchild : files) {
            FsResource res = factory.resolveFile(this.host, fchild);
            if (res != null) {
                list.add(res);
            } else {
                log.error("Couldnt resolve file {}", fchild.getAbsolutePath());
            }
        }
        return list;
    }

    /**
     * defaultPage 经 encodeSegment 编码后再拼进重定向 URL。
     */
    @Override
    public String checkRedirect(Request request) {
        if (factory.getDefaultPage() != null) {
            final boolean hasSlash = request.getAbsoluteUrl().endsWith("/");
            String page = FileSystemResourceFactory.encodeSegment(factory.getDefaultPage());
            return (hasSlash ? request.getAbsoluteUrl() : request.getAbsoluteUrl() + "/") + page;
        } else {
            return null;
        }
    }

    /**
     * 名称校验 + 经 resolveChild 显式处理 "." / ".."；
     * 目标已存在时显式失败（createNew 只用于新建，PUT 覆盖走 replaceContent）；
     * Files.createFile 原子创建，消除 TOCTOU 窗口；写入失败时清理半成品文件。
     */
    @Override
    public Resource createNew(String name, InputStream in, Long length, String contentType) throws IOException {
        FileSystemResourceFactory.validateName(name);
        File dest = factory.resolveChild(this.getFile(), name);
        try {
            // 原子创建：仅当目标不存在时成功；已存在则抛 FileAlreadyExistsException
            Files.createFile(dest.toPath());
        } catch (FileAlreadyExistsException e) {
            throw new IOException("Resource already exists: " + dest.getAbsolutePath(), e);
        }
        try {
            contentService.setFileContent(dest, in);
            refreshMTime(dest);
        } catch (IOException e) {
            // 写入失败时清理半成品文件，避免留下 0 字节残骸
            try {
                Files.deleteIfExists(dest.toPath());
            } catch (IOException cleanupError) {
                log.warn("Failed to cleanup partially-created file: {}", dest.getAbsolutePath(), cleanupError);
            }
            throw e;
        }
        factory.getWsManager().ifPresent(wsManager -> wsManager.notifyCreated(factory.toResourcePath(dest)));
        return factory.resolveFile(this.host, dest);
    }

    @Override
    protected void doCopy(File dest) throws NotAuthorizedException {
        try {
            if (isRecursive(dest)) {
                throw new NotAuthorizedException("Cannot copy to subfolder", this,
                    io.milton.http.ResponseStatus.SC_FORBIDDEN);
            }
            FileUtils.copyDirectory(this.getFile(), dest);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to copy to:" + dest.getAbsolutePath(), ex);
        }
    }

    /**
     * 对 uri 与 name 的每个 segment 分别 percent-encode（保留 '/'）；
     * ssoPrefix 场景走修正后的 insertSsoPrefix。
     * <p>
     * 根目录额外渲染根证书下载入口（供外部客户端导入系统信任库）。
     * 首次访问时须先通过 WebDAV Digest 认证，再看到本页面。
     */
    @Override
    public void sendContent(OutputStream out, Range range, Map<String, String> params, String contentType)
        throws IOException, NotAuthorizedException {
        XmlWriter w = new XmlWriter(out);
        w.open("html");
        w.open("head");
        w.writeText("<meta charset=\"UTF-8\">\n");
        w.close("head");
        w.open("body");
        w.begin("h1").open().writeText(htmlEscape(this.getName())).close();

        // 根证书下载入口：仅根目录 + 已配置 PEM 时显示
        if (isRootDirectory() && factory.hasCaCertPem()) {
            w.open("p");
            w.begin("a").writeAtt("href", caHref("__ca__.pem")).open()
                .writeText("下载根证书 (.pem)").close();
            w.writeText(" | ");
            w.begin("a").writeAtt("href", caHref("__ca__.crt")).open()
                .writeText("下载根证书 (.crt)").close();
            w.writeText("导入根证书后，本服务器的 HTTPS 证书将被客户端信任。");
            w.close("p");
        }

        w.open("table");
        for (Resource r : Optional.ofNullable(getChildren()).orElse(List.of())) {
            w.open("tr");
            w.open("td");
            // URL 语义路径（toResourcePath）拼 href，不经 canonical path
            String path = buildHref(factory.toResourcePath(getFile()), r.getName());
            w.begin("a").writeAtt("href", path).open().writeText(htmlEscape(r.getName())).close();
            w.close("td");
            w.begin("td").open().writeText(r.getModifiedDate() + "").close();
            w.close("tr");
        }
        w.close("table");
        w.close("body");
        w.close("html");
        w.flush();
    }

    private String buildHref(String uri, String name) {
        String abUrl = FileSystemResourceFactory.encodePath(uri);
        if (!abUrl.endsWith("/")) {
            abUrl += "/";
        }
        if (!abUrl.startsWith("/")) {
            abUrl = "/" + abUrl;
        }
        if (ssoPrefix == null) {
            return abUrl + FileSystemResourceFactory.encodeSegment(name);
        } else {
            String s = insertSsoPrefix(abUrl, ssoPrefix);
            return s + FileSystemResourceFactory.encodeSegment(name);
        }
    }

    /**
     * 根证书下载路径的 href。与 buildHref 的 ssoPrefix 规则保持一致：
     * 无 ssoPrefix → {@code /__ca__.pem}；有 ssoPrefix → {@code /<prefix>/__ca__.pem}。
     */
    private String caHref(String name) {
        if (ssoPrefix == null || ssoPrefix.isEmpty()) {
            return "/" + name;
        }
        return "/" + ssoPrefix + "/" + name;
    }

    /**
     * 目录资源的递归删除。基类 delete() 的 file.delete() 对非空目录必然失败。
     */
    @Override
    public void delete() {
        if (isRootDirectory()) {
            throw new RuntimeException("Cannot delete the root directory");
        }
        try {
            FileUtils.deleteDirectory(this.file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete directory: " + file.getAbsolutePath(), e);
        }
        factory.getWsManager().ifPresent(wsManager -> wsManager.notifyDeleted(factory.toResourcePath(file)));
    }

    @Override
    public Long getMaxAgeSeconds(Auth auth) {
        return null;
    }

    @Override
    public String getContentType(String accepts) {
        return "text/html; charset=UTF-8";
    }

    @Override
    public Long getContentLength() {
        return null;
    }

    @Override
    public Long getQuotaUsed() {
        if (!isRootDirectory()) {
            return null;
        }
        long totalSpace = getFile().getTotalSpace();
        long freeSpace = getFile().getFreeSpace();
        if (totalSpace < 0 || freeSpace < 0) {
            return null;
        }
        long used = totalSpace - freeSpace;
        return used >= 0 ? used : null;
    }

    @Override
    public Long getQuotaAvailable() {
        if (!isRootDirectory()) {
            return null;
        }
        long freeSpace = getFile().getFreeSpace();
        if (freeSpace < 0) {
            return null;
        }
        return freeSpace;
    }
}
