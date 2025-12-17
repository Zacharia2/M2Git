package ts.realms.m2git.ui.components.fragments;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import java.io.File;
import java.io.IOException;

import timber.log.Timber;
import ts.realms.m2git.R;
import ts.realms.m2git.core.models.Repo;
import ts.realms.m2git.ui.components.dialogs.RepoFileOperationDialog;
import ts.realms.m2git.ui.components.adapters.FilesListAdapter;
import ts.realms.m2git.ui.screens.main.BaseCompatActivity;
import ts.realms.m2git.ui.screens.main.BaseCompatActivity.OnBackClickListener;
import ts.realms.m2git.ui.screens.repoDetail.RepoDetailFragment;
import ts.realms.m2git.utils.FsUtils;

/**
 * Created by sheimi on 8/5/13.
 */
public class FilesFragment extends RepoDetailFragment {

    private static final String CURRENT_DIR = "current_dir";

    private ListView mFilesList;
    private FilesListAdapter mFilesListAdapter;

    private File mCurrentDir;
    private File mRootDir;

    private Repo mRepo;

    public static FilesFragment newInstance(Repo mRepo) {
        FilesFragment fragment = new FilesFragment();
        Bundle bundle = new Bundle();
        bundle.putSerializable(Repo.TAG, mRepo);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_files, container, false);
        getRawActivity().setFilesFragment(this);

        Bundle bundle = getArguments();
        mRepo = (Repo) bundle.getSerializable(Repo.TAG);
        if (mRepo == null && savedInstanceState != null) {
            mRepo = (Repo) savedInstanceState.getSerializable(Repo.TAG);
        }
        if (mRepo == null) {
            return v;
        }
        mRootDir = mRepo.getDir();

        mFilesList = v.findViewById(R.id.filesList);

        mFilesListAdapter = new FilesListAdapter(getActivity(),
            file -> {
                String name = file.getName();
                return !name.equals(".git");
            });
        mFilesList.setAdapter(mFilesListAdapter);

        mFilesList
            .setOnItemClickListener((adapterView, view, position, id) -> {
                File file = mFilesListAdapter.getItem(position);
                if (file.isDirectory()) {
                    setCurrentDir(file);
                    return;
                }
                String mime = FsUtils.getMimeType(file);
                if (FsUtils.isTextMimeType(mime)) {
                    Intent intent = new Intent(getActivity(),
                        ViewFileActivity.class);
                    intent.putExtra(ViewFileActivity.TAG_FILE_NAME,
                        file.getAbsolutePath());
                    intent.putExtra(Repo.TAG, mRepo);
                    getRawActivity().startActivity(intent);
                    return;
                }
                try {
                    FsUtils.openFile(((BaseCompatActivity) getActivity()), file);
                } catch (ActivityNotFoundException e) {
                    Timber.e(e);
                    ((BaseCompatActivity) getActivity()).showMessageDialog(R.string.dialog_error_title,
                        getString(R.string.error_can_not_open_file));
                }
            });

        mFilesList
            .setOnItemLongClickListener((adapterView, view, position, id) -> {
                File file = mFilesListAdapter.getItem(position);
                RepoFileOperationDialog dialog = new RepoFileOperationDialog();
                Bundle args = new Bundle();
                args.putString(RepoFileOperationDialog.FILE_PATH,
                    file.getAbsolutePath());
                dialog.setArguments(args);
                dialog.show(getFragmentManager(), "repo-file-op-dialog");
                return true;
            });

        if (savedInstanceState != null) {
            String currentDirPath = savedInstanceState.getString(CURRENT_DIR);
            if (currentDirPath != null) {
                mCurrentDir = new File(currentDirPath);
                setCurrentDir(mCurrentDir);
            }
        }
        reset();
        return v;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(Repo.TAG, mRepo);
        if (mCurrentDir != null) {
            outState.putString(CURRENT_DIR, mCurrentDir.getAbsolutePath());
        }
    }

    /**
     * Set the directory listing currently being displayed
     *
     * @param dir
     */
    public void setCurrentDir(File dir) {
        mCurrentDir = dir;
        if (mFilesListAdapter != null) {
            mFilesListAdapter.setDir(mCurrentDir);
        }
    }

    /**
     * If the root dir has previously been set, set the root dir to be the currently displayed
     * directory listing.
     */
    public void resetCurrentDir() {
        if (mRootDir == null)
            return;
        setCurrentDir(mRootDir);
    }

    @Override
    public void reset() {
        resetCurrentDir();
    }

    public void newDir(String name) {
        File file = new File(mCurrentDir, name);
        if (file.exists()) {
            showToastMessage(R.string.alert_file_exists);
            return;
        }
        file.mkdir();
        setCurrentDir(mCurrentDir);
    }

    /**
     * Create a new file within the currently displayed directory
     *
     * @param name
     */
    public void newFile(String name) throws IOException {
        File file = new File(mCurrentDir, name);
        if (file.exists()) {
            showToastMessage(R.string.alert_file_exists);
            return;
        }
        file.createNewFile();
        setCurrentDir(mCurrentDir);
    }

    @Override
    public OnBackClickListener getOnBackClickListener() {
        return () -> {
            if (mRootDir == null || mCurrentDir == null)
                return false;
            if (mRootDir.equals(mCurrentDir))
                return false;
            File parent = mCurrentDir.getParentFile();
            setCurrentDir(parent);
            return true;
        };
    }
}
