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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Date;

import io.milton.http.Auth;
import io.milton.http.Request;
import io.milton.http.Request.Method;
import io.milton.http.exceptions.ConflictException;
import io.milton.http.exceptions.NotAuthorizedException;
import io.milton.http.http11.auth.DigestResponse;
import io.milton.resource.CollectionResource;
import io.milton.resource.CopyableResource;
import io.milton.resource.DigestResource;
import io.milton.resource.MoveableResource;
import io.milton.resource.Resource;

public abstract class FsResource implements Resource, MoveableResource, CopyableResource, DigestResource {

    private static final Logger log = LoggerFactory.getLogger(FsResource.class);

    final FileSystemResourceFactory factory;
    final String host;
    File file;
    String ssoPrefix;

    public FsResource(String host, FileSystemResourceFactory factory, File file) {
        this.host = host;
        this.file = file;
        this.factory = factory;
    }

    /**
     * 写入后强制刷新 mtime，保证同一秒内两次写入产生不同 ETag，避免错误的 304 缓存命中。
     * Android 共享存储(FUSE)上 setLastModified 可能静默失败（返回 false），
     * 失败时 ETag 的 length 分量仍覆盖大部分场景，但必须打日志暴露问题。
     */
    protected static void refreshMTime(File f) {
        if (!f.setLastModified(System.currentTimeMillis())) {
            log.warn("setLastModified failed (scoped storage / FUSE?), ETag may collide "
                + "for same-size writes within one second: {}", f.getAbsolutePath());
        }
    }

    protected abstract void doCopy(File dest) throws NotAuthorizedException;

    public File getFile() {
        return file;
    }

    /**
     * modtime/size 的 hex 组合 + 路径 hash 的 hex，避免纯 hashCode 碰撞
     * 导致错误的 304 缓存命中。（mtime 刷新失败的场景由写入方打 warn 暴露。）
     */
    @Override
    public String getUniqueId() {
        long lm = file.lastModified();
        long len = file.length();
        int pathHash = file.getAbsolutePath().hashCode();
        return Long.toHexString(lm) + "-" + Long.toHexString(len) + "-" + Integer.toHexString(pathHash);
    }

    @Override
    public String getName() {
        return file.getName();
    }

    @Override
    public Object authenticate(String user, String password) {
        return factory.getSecurityManager().authenticate(user, password);
    }

    public Object authenticate(DigestResponse digestRequest) {
        return factory.getSecurityManager().authenticate(digestRequest);
    }

    public boolean isDigestAllowed() {
        return factory.isDigestAllowed();
    }

    @Override
    public boolean authorise(Request request, Method method, Auth auth) {
        boolean b = factory.getSecurityManager().authorise(request, method, auth, this);
        if (log.isTraceEnabled()) {
            log.trace("authorise: result=" + b);
        }
        return b;
    }

    @Override
    public String getRealm() {
        String r = factory.getRealm(this.host);
        if (r == null) {
            throw new NullPointerException("Got null realm from: " + factory.getClass() + " for host=" + this.host);
        }
        return r;
    }

    @Override
    public Date getModifiedDate() {
        return new Date(file.lastModified());
    }

    public Date getCreateDate() {
        return null;
    }

    public int compareTo(Resource o) {
        return this.getName().compareTo(o.getName());
    }

    /**
     * 自拷贝/移入自身子树检查，供 copyTo 与 moveTo 共用。
     * 1) 比较 "src + 分隔符" 为前缀，避免 /root/mydir -> /root/mydir2 误判；
     * 2) 统一大小写不敏感比较：Android 私有目录(ext4)区分大小写，代价仅是
     * 拒绝 mydir -> MYDIR2 这类仅大小写不同的重命名，个人使用可接受的保守行为。
     */
    protected boolean isRecursive(File dest) throws IOException {
        File srcCanon = this.file.getCanonicalFile();
        File destCanon = dest.getCanonicalFile();
        if (destCanon.equals(srcCanon)) {
            return true;
        }
        String prefix = srcCanon.getPath() + File.separator;
        String dp = destCanon.getPath();
        return dp.length() >= prefix.length()
            && dp.regionMatches(true /* ignoreCase */, 0, prefix, 0, prefix.length());
    }

    /**
     * 原子 move + 显式的目标已存在保护。
     * <p>
     * 注意：Linux/Android 上 ATOMIC_MOVE 直接走 rename(2)，而 POSIX rename 会
     * 静默替换已存在的目标——FileAlreadyExistsException 在此平台是死代码，
     * 不做预检的话 move-over 会直接覆盖丢失目标文件。个人单用户场景无并发竞争，
     * exists() 预检完全够用（copyTo 同样采用该策略）。
     */
    @Override
    public void moveTo(CollectionResource newParent, String newName) throws ConflictException {
        if (!(newParent instanceof FsDirectoryResource newFsParent)) {
            throw new RuntimeException("Destination is an unknown type. Must be a FsDirectoryResource, is a: "
                + newParent.getClass());
        }
        if (isRootDirectory()) {
            throw new RuntimeException("Cannot move the root directory");
        }
        FileSystemResourceFactory.validateName(newName);
        File dest = factory.resolveChild(newFsParent.getFile(), newName);
        try {
            if (isRecursive(dest)) {
                throw new RuntimeException("Cannot move to itself or its own subfolder: " + dest.getAbsolutePath());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to resolve canonical path for move: " + dest.getAbsolutePath(), e);
        }
        if (Files.exists(dest.toPath())) {
            throw new ConflictException(this, "Destination already exists: " + dest.getAbsolutePath());
        }
        String from = factory.toResourcePath(this.file);
        try {
            Files.move(this.file.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // 跨文件系统（FUSE 边界等）退回 copy+delete；中断时会留下半截目标，
            // 个人局域网场景可接受，失败时尽力清理
            try {
                if (this.file.isDirectory()) {
                    FileUtils.moveDirectory(this.file, dest);
                } else {
                    FileUtils.moveFile(this.file, dest);
                }
            } catch (IOException ioe) {
                try {
                    FileUtils.deleteQuietly(dest);   // 文件/目录（递归）都处理，且不抛
                } catch (Exception ignore) {
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to move to: " + dest.getAbsolutePath(), e);
        }
        this.file = dest;
        factory.getWsManager().ifPresent(wsManager -> wsManager.notifyMoved(from, factory.toResourcePath(this.file)));
    }

    @Override
    public void copyTo(CollectionResource newParent, String newName) throws ConflictException, NotAuthorizedException {
        if (!(newParent instanceof FsDirectoryResource newFsParent)) {
            throw new RuntimeException("Destination is an unknown type. Must be a FsDirectoryResource, is a: "
                + newParent.getClass());
        }
        if (isRootDirectory()) {
            throw new RuntimeException("Cannot copy the root directory");
        }
        FileSystemResourceFactory.validateName(newName);
        File dest = factory.resolveChild(newFsParent.getFile(), newName);
        if (Files.exists(dest.toPath())) {
            throw new ConflictException(this, "Destination already exists: " + dest.getAbsolutePath());
        }
        doCopy(dest);
        factory.getWsManager().ifPresent(wsManager -> wsManager.notifyCreated(factory.toResourcePath(dest)));
    }

    /**
     * 失败信息带上具体路径；目录资源的递归删除由 FsDirectoryResource 重写实现。
     */
    public void delete() {
        boolean ok = file.delete();
        if (!ok) {
            throw new RuntimeException("Failed to delete: " + file.getAbsolutePath());
        }
        factory.getWsManager().ifPresent(wsManager -> wsManager.notifyDeleted(factory.toResourcePath(file)));
    }

    protected boolean isRootDirectory() {
        try {
            return this.file.getCanonicalFile().equals(factory.getRoot().getCanonicalFile());
        } catch (IOException e) {
            return false;
        }
    }
}
