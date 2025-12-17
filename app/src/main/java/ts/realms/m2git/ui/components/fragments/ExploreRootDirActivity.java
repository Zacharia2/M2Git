package ts.realms.m2git.ui.components.fragments;

import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AdapterView;

import java.io.File;
import java.io.FileFilter;

import ts.realms.m2git.R;
import ts.realms.m2git.core.models.Repo;

public class ExploreRootDirActivity extends FileExplorerActivity {


    @Override
    protected File getRootFolder() {
        return Environment.getExternalStorageDirectory();
    }

    @Override
    protected FileFilter getExplorerFileFilter() {
        return file -> {
            String filename = file.getName();
            return !filename.startsWith(".") && file.isDirectory();
        };
    }

    @Override
    protected AdapterView.OnItemClickListener getOnListItemClickListener() {
        return (adapterView, view, position, id) -> {
            File file = mFilesListAdapter.getItem(position);
            if (file.isDirectory()) {
                setCurrentDir(file);
            }
        };
    }

    @Override
    protected AdapterView.OnItemLongClickListener getOnListItemLongClickListener() {
        return null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.select_root, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_select_root) {
            Repo.setLocalRepoRoot(this, getCurrentDir());
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}
