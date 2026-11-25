/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package ts.realms.m2git.core.network.mws.fs;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import io.milton.http.Auth;
import io.milton.http.Range;
import io.milton.http.Request;
import io.milton.http.Request.Method;
import io.milton.http.http11.auth.DigestResponse;
import io.milton.resource.DigestResource;
import io.milton.resource.GetableResource;
import io.milton.resource.PropFindableResource;

/**
 * 根证书下载资源。
 * <p>
 * 根证书是公开信息（设计上就该公开传播），通过此资源对外提供 PEM 格式的根 CA 证书，
 * 供外部客户端导入系统信任库。访问权限由 SecurityManager 统一裁决（与其它资源一致），
 * 首次配置时用户须先通过 WebDAV Digest 认证。
 * <p>
 * 路径 {@code /__ca__.pem} 与 {@code /__ca__.crt} 的拦截在
 * {@link FileSystemResourceFactory#getResource} 中完成，本资源不参与文件系统解析。
 * <p>
 * 缓存策略：{@link #getMaxAgeSeconds} 返回 {@code 0L}，不缓存。
 * 根 CA 设计上永不轮换，但 {@link ts.realms.m2git.core.network.mws.SecureStore.CaStore#load}
 * 在文件损坏时会重建 CA，此时旧缓存会让客户端拿到过期证书导致 pin 失败。
 * 下载是低频操作，多一次请求不值得冒此风险。
 */
public class CaCertDownloadResource implements GetableResource, PropFindableResource, DigestResource {

    private final String host;
    private final FileSystemResourceFactory factory;
    private final byte[] content;
    private final String fileName;
    private final String mimeType;

    public CaCertDownloadResource(String host, FileSystemResourceFactory factory,
                                  String pem, String fileName, String mimeType) {
        this.host = host;
        this.factory = factory;
        this.content = pem.getBytes(StandardCharsets.US_ASCII);
        this.fileName = fileName;
        this.mimeType = mimeType;
    }

    @Override
    public String getUniqueId() {
        return "ca-cert-" + fileName;
    }

    @Override
    public Object authenticate(DigestResponse digestRequest) {
        return factory.getSecurityManager().authenticate(digestRequest);
    }

    @Override
    public boolean isDigestAllowed() {
        // factory.isDigestAllowed() 是包私有（同包 fs），可直接访问
        return factory.isDigestAllowed();
    }

    @Override
    public String getName() {
        return fileName;
    }

    @Override
    public Object authenticate(String user, String password) {
        return null; // Basic 认证路径明确拒绝
    }

    @Override
    public String getRealm() {
        return factory.getRealm(host);
    }

    @Override
    public Date getCreateDate() {
        return null;
    }

    @Override
    public Date getModifiedDate() {
        return null;
    }

    @Override
    public Long getContentLength() {
        return (long) content.length;
    }

    @Override
    public String getContentType(String accepts) {
        return mimeType;
    }

    @Override
    public Long getMaxAgeSeconds(Auth auth) {
        return 0L;   // 显式不缓存：见类注释
    }

    @Override
    public String checkRedirect(Request request) {
        return null;
    }

    @Override
    public boolean authorise(Request request, Method method, Auth auth) {
        return factory.getSecurityManager().authorise(request, method, auth, this);
    }

    @Override
    public void sendContent(OutputStream out, Range range, Map<String, String> params,
                            String contentType) throws IOException {
        out.write(content);
        out.flush();
    }
}
