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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Abstraction for storing and retrieving
 *
 * @author brad
 */
public interface FileContentService {
    /**
     * SimpleFileContentService 原子覆盖写使用的临时文件前缀/后缀，
     * 目录列表必须过滤，否则上传期间客户端会看到 tmp 文件（还可能下载到半截内容）。
     */
    String TMP_PREFIX = ".milton-";
    String TMP_SUFFIX = ".tmp";

    /**
     * 原子替换目标文件内容：写临时文件 + ATOMIC_MOVE。
     * 实现方可以在写入钩子里拿到真实的最终路径 target。
     */
    void replaceFileContent(File target, InputStream in) throws IOException;

    void setFileContent(File file, InputStream in) throws IOException;

    InputStream getFileContent(File file) throws FileNotFoundException;
}
