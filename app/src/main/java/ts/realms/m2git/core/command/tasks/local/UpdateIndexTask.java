package ts.realms.m2git.core.command.tasks.local;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.lib.FileMode;

import java.io.IOException;

import ts.realms.m2git.R;
import ts.realms.m2git.ui.common.errors.NoSuchIndexPathException;
import ts.realms.m2git.ui.common.errors.StopTaskException;
import ts.realms.m2git.core.command.tasks.MAsyncTask;
import ts.realms.m2git.core.command.tasks.RepoOpTask;
import ts.realms.m2git.core.models.Repo;

public class UpdateIndexTask extends RepoOpTask implements MAsyncTask.AsyncTaskDoCallback {

    private final String path;
    private final int newMode;

    public UpdateIndexTask(Repo repo, String path, int newMode) {
        super(repo);
        this.path = path;
        this.newMode = newMode;
    }

    public static int calculateNewMode(boolean executable) {
        // 设置类似linux文件权限。
        // 0100755 rwx-rx-rx
        // 0100644 rw-r-r
        // 0100777 rwx-rwx-rwx
        return executable ? FileMode.EXECUTABLE_FILE.getBits() : FileMode.REGULAR_FILE.getBits();
    }

    @Override
    public boolean doInBackground(Void... params) {
        return updateIndex();
    }

    private boolean updateIndex() {
        // mRepo.getGit().getRepository().lockDirCache();
        // LockFailedException: Cannot lock .git/index.
        // Ensure that no other process has an open file
        DirCache dircache;
        try {
            // 直接读吧，可能会有bug。lockDirCache无法锁.git/index抛出异常。
            dircache = mRepo.getGit().getRepository().readDirCache();
        } catch (NoWorkTreeException e) {
            setException(e, R.string.error_no_worktree);
            return false;
        } catch (CorruptObjectException e) {
            setException(e, R.string.error_invalid_index);
            return false;
        } catch (StopTaskException | IOException e) {
            throw new RuntimeException(e);
        }

        try {
            dircache.lock();
            DirCacheEntry dirCacheEntry = dircache.getEntry(path);
            if (dirCacheEntry == null) {
                setException(new NoSuchIndexPathException(path), R.string.error_file_not_found);
                return false;
            }
            int oldMode = dirCacheEntry.getFileMode().getBits();
            // 33279(10进制) = 0100777(8进制)
            // 执行位操作，先进行位与oldMode & Mode_777，得出结果在进行位或newMode，最终结果为最终权限。
            FileMode fileMode = FileMode.fromBits(newMode | (oldMode & 33279));
            dirCacheEntry.setFileMode(fileMode);
        } catch (IllegalArgumentException | IOException e) {
            setException(e);
            return false;
        } finally {
            dircache.unlock();
        }
        return true;
    }
}
