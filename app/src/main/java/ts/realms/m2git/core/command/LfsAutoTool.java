package ts.realms.m2git.core.command;

import org.eclipse.jgit.attributes.Attribute;
import org.eclipse.jgit.attributes.Attributes;
import org.eclipse.jgit.lfs.BuiltinLFS;
import org.eclipse.jgit.lfs.Lfs;
import org.eclipse.jgit.lfs.LfsPointer;
import org.eclipse.jgit.lfs.SmudgeFilter;
import org.eclipse.jgit.lfs.lib.LfsPointerFilter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.LfsFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class LfsAutoTool {

    static {
        // 激活 JGit 内置的 LFS 支持（必须，否则 applySmudgeFilter 是空操作）
        BuiltinLFS.register();
    }

    private LfsAutoTool() {
    }

    // ==================== 核心公开方法 ====================

    /**
     * 判断仓库在指定引用下是否包含 LFS 对象
     *
     * @param repoPath 本地仓库根目录路径
     * @param ref      引用名（如 "HEAD"、"refs/heads/master"）
     * @return true 表示树中存在至少一个 LFS 指针文件
     */
    public static boolean hasLfs(String repoPath, String ref) throws IOException {
        try (Repository repo = openRepo(repoPath)) {
            return hasLfsInternal(repo, ref);
        }
    }

    /**
     * 便捷重载：判断当前 HEAD 下是否有 LFS 对象
     */
    public static boolean hasLfs(String repoPath) throws IOException {
        try (Repository repo = openRepo(repoPath)) {
            return hasLfsInternal(repo, "HEAD");
        }
    }

    /**
     * 下载单个 LFS 文件（一行调用，自动完成全部逻辑）
     *
     * @param repoPath 本地仓库根目录路径（如 "/home/user/my-repo"）
     * @param filePath 文件在仓库中的相对路径（如 "data/model.bin"）
     * @return 本地 media 文件路径（.git/lfs/objects 下的真实文件）
     */
    public static Path download(String repoPath, String filePath) throws IOException {
        try (Repository repo = openRepo(repoPath)) {
            String ref = "HEAD"; // 默认从当前检出的提交开始找
            return downloadInternal(repo, ref, filePath);
        }
    }

    /**
     * 下载指定分支/引用下的单个 LFS 文件
     */
    public static Path download(String repoPath, String ref, String filePath) throws IOException {
        try (Repository repo = openRepo(repoPath)) {
            return downloadInternal(repo, ref, filePath);
        }
    }

    /**
     * 批量下载当前 HEAD 下所有 LFS 文件（自动扫描 + 批量下载）
     *
     * @param repoPath 本地仓库根目录路径
     * @return 所有成功下载的本地 media 文件路径集合
     */
    public static Collection<Path> downloadAll(String repoPath) throws IOException {
        try (Repository repo = openRepo(repoPath)) {
            return downloadAllInternal(repo, "HEAD");
        }
    }

    /**
     * 批量下载指定分支/引用下所有 LFS 文件
     */
    public static Collection<Path> downloadAll(String repoPath, String ref) throws IOException {
        try (Repository repo = openRepo(repoPath)) {
            return downloadAllInternal(repo, ref);
        }
    }

    // ==================== 内部实现 ====================

    private static Repository openRepo(String repoPath) throws IOException {
        return new FileRepositoryBuilder()
            .setGitDir(new java.io.File(repoPath, ".git"))
            .readEnvironment()
            .findGitDir()
            .build();
    }

    /**
     * 单文件下载：路径 → BlobId → ObjectLoader → applySmudgeFilter（内部自动下载）
     */
    private static Path downloadInternal(Repository repo, String ref, String filePath) throws IOException {
        ObjectId treeId = repo.resolve(ref + "^{tree}");
        if (treeId == null) {
            throw new IOException("无法解析引用: " + ref);
        }

        ObjectId blobId;
        try (TreeWalk tw = TreeWalk.forPath(repo, filePath, treeId)) {
            if (tw == null) {
                throw new IOException("文件不存在: " + filePath + " (ref=" + ref + ")");
            }
            blobId = tw.getObjectId(0);
        }

        ObjectLoader loader = repo.open(blobId);
        // 先解析指针（校验是否为 LFS 文件），解析失败返回 null
        LfsPointer ptr = parsePointer(loader);
        if (ptr == null) {
            throw new IOException("不是有效的 LFS 指针文件: " + filePath);
        }

        // 获取该路径的 attributes（必须，用于触发 LFS 逻辑）
        Attribute attr = getLfsAttribute(repo, filePath);

        // 核心一行：applySmudgeFilter 内部自动判断 + 按需下载
        LfsFactory.getInstance().applySmudgeFilter(repo, loader, attr);

        // 返回下载后的 media 文件路径
        Path mediaFile = new Lfs(repo).getMediaFile(ptr.getOid());
        if (!java.nio.file.Files.exists(mediaFile)) {
            throw new IOException("下载后 media 文件不存在: " + mediaFile);
        }

        return mediaFile;
    }

    /**
     * 批量下载：用 LfsPointerFilter 扫描整棵树，收集所有指针后批量下载
     */
    private static Collection<Path> downloadAllInternal(Repository repo, String ref) throws IOException {
        ObjectId treeId = repo.resolve(ref + "^{tree}");
        if (treeId == null) {
            throw new IOException("无法解析引用: " + ref);
        }

        List<LfsPointer> pointers = new ArrayList<>();
        try (TreeWalk tw = new TreeWalk(repo)) {
            tw.addTree(treeId);
            tw.setRecursive(true);
            LfsPointerFilter filter = new LfsPointerFilter();
            tw.setFilter(filter);
            while (tw.next()) {
                LfsPointer ptr = filter.getPointer();
                if (ptr != null) {
                    pointers.add(ptr);
                }
            }
        }

        if (pointers.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        // 批量下载（内部自动处理 Batch API、认证、写入 .git/lfs/objects）
        return SmudgeFilter.downloadLfsResource(
            new Lfs(repo), repo, pointers.toArray(new LfsPointer[0]));
    }

    /**
     * 解析 LfsPointer，非 LFS 文件返回 null
     */
    private static LfsPointer parsePointer(ObjectLoader loader) throws IOException {
        if (loader.getSize() > LfsPointer.SIZE_THRESHOLD) {
            return null;
        }
        try (InputStream in = loader.openStream()) {
            return LfsPointer.parseLfsPointer(in);
        }
    }

    /**
     * 获取指定路径的 "filter" 属性（值为 "lfs" 时才会触发下载）
     * 如果无法获取，返回一个兜底的 lfs 属性
     */
    private static Attribute getLfsAttribute(Repository repo, String filePath) throws IOException {
        Attributes attrs = LfsFactory.getAttributesForPath(repo, filePath);
        Attribute attr = attrs.get("filter"); // .gitattributes 中写的是 filter=lfs
        if (attr == null) {
            // 兜底：手动构造一个 filter=lfs 属性，确保 applySmudgeFilter 不会被跳过
            attr = new Attribute("filter", "lfs");
        }
        return attr;
    }

    private static boolean hasLfsInternal(Repository repo, String ref) throws IOException {
        ObjectId treeId = repo.resolve(ref + "^{tree}");
        if (treeId == null) {
            throw new IOException("无法解析引用: " + ref);
        }

        // 复用 LfsPointerFilter 逐个文件判断，遇到第一个指针立即返回 true
        try (TreeWalk tw = new TreeWalk(repo)) {
            tw.addTree(treeId);
            tw.setRecursive(true);
            LfsPointerFilter filter = new LfsPointerFilter();
            tw.setFilter(filter);
            while (tw.next()) {
                if (filter.getPointer() != null) {
                    return true;   // 提前退出，不再扫剩余文件
                }
            }
        }
        return false;
    }

}
