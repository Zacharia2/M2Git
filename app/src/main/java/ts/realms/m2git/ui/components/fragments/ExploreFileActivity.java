package ts.realms.m2git.ui.components.fragments;

import android.app.Activity;
import android.content.Intent;
import android.os.Environment;
import android.widget.AdapterView;

import java.io.File;
import java.io.FileFilter;

public class ExploreFileActivity extends FileExplorerActivity {

    @Override
    protected File getRootFolder() {
        return Environment.getExternalStorageDirectory();
    }

    @Override
    protected FileFilter getExplorerFileFilter() {
        return file -> {
            String filename = file.getName();
            return !filename.startsWith(".");
        };
    }

    @Override
    protected AdapterView.OnItemClickListener getOnListItemClickListener() {
        return (adapterView, view, position, id) -> {
            File file = mFilesListAdapter.getItem(position);
            if (file.isDirectory()) {
                setCurrentDir(file);
                return;
            }
            Intent intent = new Intent();
            intent.putExtra(RESULT_PATH, file.getAbsolutePath());
            setResult(Activity.RESULT_OK, intent);
            finish();
        };
    }

    @Override
    protected AdapterView.OnItemLongClickListener getOnListItemLongClickListener() {
        return null;
    }

}
