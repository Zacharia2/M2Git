package ts.realms.m2git.ui.screens.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileFilter;

import ts.realms.m2git.R;
import ts.realms.m2git.local.preference.Profile;
import ts.realms.m2git.ui.components.lists.FilesListAdapter;
import ts.realms.m2git.ui.screens.main.BaseCompatActivity;

public abstract class FileExplorerActivity extends BaseCompatActivity {

    public static final String RESULT_PATH = "result_path";
    private static final int RC_ACCESS_ALL_FILES = 123;
    protected FilesListAdapter mFilesListAdapter;
    private File mRootFolder;
    private File mCurrentDir;
    private ListView mFileList;
    private TextView mCurrentPathView;
    private TextView mUpDir;
    private ImageView mUpDirIcon;

    protected abstract File getRootFolder();

    protected abstract FileFilter getExplorerFileFilter();

    protected abstract AdapterView.OnItemClickListener getOnListItemClickListener();

    protected abstract AdapterView.OnItemLongClickListener getOnListItemLongClickListener();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_list);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        mRootFolder = getRootFolder();
        mCurrentDir = mRootFolder;

        mFileList = findViewById(R.id.fileList);
        mCurrentPathView = findViewById(R.id.currentPath);
        mCurrentPathView.setText(mCurrentDir.getPath());

        mUpDirIcon = findViewById(R.id.upDirIcon);
        mUpDirIcon.setImageResource(Profile.getStyledResource(this, R.attr.ic_folder_fl));

        mUpDir = findViewById(R.id.upDir);
        mUpDir.setOnClickListener(v -> {
            File parent = mCurrentDir.getParentFile();
            if (parent != null) {
                setCurrentDir(parent);
            }
        });

        mFilesListAdapter = new FilesListAdapter(this, getExplorerFileFilter());
        mFileList.setAdapter(mFilesListAdapter);
        mFilesListAdapter.setDir(mRootFolder);

        mFileList.setOnItemClickListener(getOnListItemClickListener());
        mFileList.setOnItemLongClickListener(getOnListItemLongClickListener());
        if (Environment.getExternalStorageDirectory().equals(mRootFolder)) {
            checkAndRequestAccessAllFilesPermission(RC_ACCESS_ALL_FILES);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_ACCESS_ALL_FILES) {
            mFilesListAdapter.setDir(mRootFolder);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.empty_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            final File parent = mCurrentDir.getParentFile();
            if (!mRootFolder.equals(mCurrentDir) && (parent != null)) {
                setCurrentDir(parent);
                return true;
            }
            finish();
            return true;
        }
        return false;
    }

    protected File getCurrentDir() {
        return mCurrentDir;
    }

    protected void setCurrentDir(File dir) {
        mCurrentDir = dir;
        mFilesListAdapter.setDir(mCurrentDir);
        mCurrentPathView.setText(mCurrentDir.getPath());

        if (dir.getParentFile() == null) {
            mUpDir.setVisibility(View.GONE);
            mUpDirIcon.setVisibility(View.GONE);
        } else {
            mUpDir.setVisibility(View.VISIBLE);
            mUpDirIcon.setVisibility(View.VISIBLE);
        }

    }

}
