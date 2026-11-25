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

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * @author brad
 */
public class SimpleFileContentService implements FileContentService {

    /**
     * 原子覆盖写：同目录临时文件 + ATOMIC_MOVE，任一时刻目标文件都是完整内容。
     * 临时文件名前缀 .milton- / 后缀 .tmp，FsDirectoryResource.getChildren()
     * 按此规则过滤，客户端列表不可见。
     */
    @Override
    public void replaceFileContent(File target, InputStream in) throws IOException {
        Path targetPath = target.toPath();
        Path tmp = null;
        try {
            tmp = Files.createTempFile(targetPath.getParent(), FileContentService.TMP_PREFIX, FileContentService.TMP_SUFFIX);
            setFileContent(tmp.toFile(), in);
            try {
                Files.move(tmp, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                // FUSE 边界可能不支持原子 move，退回普通 move（非原子，个人局域网可接受）
                Files.move(tmp, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            tmp = null;
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignore) {
                }
            }
        }
    }

    @Override
    public void setFileContent(File file, InputStream in) throws IOException {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            IOUtils.copy(in, out);
        } finally {
            IOUtils.closeQuietly(out);
        }
    }

    @Override
    public InputStream getFileContent(File file) throws FileNotFoundException {
        return new FileInputStream(file);
    }
}
