package ts.realms.m2git.ui.components.lists;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import timber.log.Timber;
import ts.realms.m2git.R;
import ts.realms.m2git.local.database.RepoContract;
import ts.realms.m2git.core.models.Repo;
import ts.realms.m2git.local.database.RepoDbManager;
import ts.realms.m2git.ui.screens.main.BaseCompatActivity;
import ts.realms.m2git.ui.screens.fragments.RepoDetailActivity;
import ts.realms.m2git.ui.screens.main.RepoListActivity;
import ts.realms.m2git.utils.BasicFunctions;

/**
 * Created by sheimi on 8/6/13.
 */
public class RepoListAdapter extends ArrayAdapter<Repo> implements RepoDbManager.RepoDbObserver,
    AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {

    private static final int QUERY_TYPE_SEARCH = 0;
    private static final int QUERY_TYPE_QUERY = 1;
    private static final String TAG = RepoListAdapter.class.getSimpleName();
    private final DateFormat mCommitDateFormatter;
    private final RepoListActivity mActivity;
    private int mQueryType = QUERY_TYPE_QUERY;
    private String mSearchQueryString;

    public RepoListAdapter(Context context) {
        super(context, 0);
        RepoDbManager.registerDbObserver(RepoContract.RepoEntry.TABLE_NAME, this);
        mActivity = (RepoListActivity) context;
        mCommitDateFormatter = android.text.format.DateFormat.getDateFormat(context);
    }

    public void searchRepo(String query) {
        mQueryType = QUERY_TYPE_SEARCH;
        mSearchQueryString = query;
        requery();
    }

    public void queryAllRepo() {
        mQueryType = QUERY_TYPE_QUERY;
        requery();
    }

    private void requery() {
        Cursor cursor = null;
        switch (mQueryType) {
            case QUERY_TYPE_SEARCH:
                cursor = RepoDbManager.searchRepo(mSearchQueryString);
                break;
            case QUERY_TYPE_QUERY:
                cursor = RepoDbManager.queryAllRepo();
                break;
        }
        List<Repo> repo = Repo.getRepoList(cursor);
        Collections.sort(repo);
        cursor.close();
        clear();
        addAll(repo);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = newView(getContext(), parent);
        }
        bindView(convertView, position);
        return convertView;
    }

    public View newView(Context context, ViewGroup parent) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.repo_listitem, parent, false);
        RepoListItemHolder holder = new RepoListItemHolder();
        holder.repoTitle = view.findViewById(R.id.repoTitle);
        holder.repoRemote = view.findViewById(R.id.repoRemote);
        holder.commitAuthor = view.findViewById(R.id.commitAuthor);
        holder.commitMsg = view.findViewById(R.id.commitMsg);
        holder.commitTime = view.findViewById(R.id.commitTime);
        holder.authorIcon = view.findViewById(R.id.authorIcon);
        holder.progressContainer = view.findViewById(R.id.progressContainer);
        holder.commitMsgContainer = view.findViewById(R.id.commitMsgContainer);
        holder.progressMsg = view.findViewById(R.id.progressMsg);
        holder.cancelBtn = view.findViewById(R.id.cancelBtn);
        view.setTag(holder);
        return view;
    }

    public void bindView(View view, int position) {
        RepoListItemHolder holder = (RepoListItemHolder) view.getTag();
        final Repo repo = getItem(position);

        holder.repoTitle.setText(repo.getDiaplayName());
        holder.repoRemote.setText(repo.getRemoteURL());

        if (!repo.getRepoStatus().equals(RepoContract.REPO_STATUS_NULL)) {
            holder.commitMsgContainer.setVisibility(View.GONE);
            holder.progressContainer.setVisibility(View.VISIBLE);
            holder.progressMsg.setText(repo.getRepoStatus());
            holder.cancelBtn.setOnClickListener(v -> {
                repo.deleteRepo(true);
                repo.cancelTask();
            });
        } else if (repo.getLastCommitter() != null) {
            holder.commitMsgContainer.setVisibility(View.VISIBLE);
            holder.progressContainer.setVisibility(View.GONE);

            String date = "";
            if (repo.getLastCommitDate() != null) {
                date = mCommitDateFormatter.format(repo.getLastCommitDate());
            }
            holder.commitTime.setText(date);
            holder.commitMsg.setText(repo.getLastCommitMsg());
            holder.commitAuthor.setText(repo.getLastCommitter());
            holder.authorIcon.setVisibility(View.VISIBLE);
            BasicFunctions.setAvatarImage(holder.authorIcon, repo.getLastCommitterEmail());
        }
    }

    @Override
    public void notifyChanged() {
        mActivity.runOnUiThread(this::requery);
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
        Repo repo = getItem(position);
        if (repo.isExternal() && mActivity.checkAndRequestAccessAllFilesPermission(0)) {
            return;
        }
        Intent intent = new Intent(mActivity, RepoDetailActivity.class);
        intent.putExtra(Repo.TAG, repo);
        mActivity.startActivity(intent);
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long id) {
        final Repo repo = getItem(position);
        if (!repo.getRepoStatus().equals(RepoContract.REPO_STATUS_NULL)) return false;
        Context context = getContext();
        if (context instanceof BaseCompatActivity) {
            showRepoOptionsDialog((BaseCompatActivity) context, repo);
        }
        return true;
    }

    private void showRepoOptionsDialog(final BaseCompatActivity context, final Repo repo) {

        BaseCompatActivity.onOptionDialogClicked[] dialog =
            new BaseCompatActivity.onOptionDialogClicked[]{() -> showRenameRepoDialog(context
                , repo), () -> showRemoveRepoDialog(context, repo), null};
        // 区分大小写
        final String remoteRaw = repo.getRemoteURL();
        final String remoteRawLowerCase = repo.getRemoteURL().toLowerCase();
        boolean repoHasHttpRemote =
            !remoteRawLowerCase.equals("local repository") && remoteRawLowerCase.contains("http");
        if (repoHasHttpRemote) {
            //TODO : Transform ssh uri in http?
            dialog[2] = () -> {

                //remove git extension if present
                String repoUrl = remoteRaw.endsWith(context.getString(R.string.git_extension)) ?
                    remoteRaw.substring(0, remoteRaw.lastIndexOf('.')) : remoteRaw;

                Intent openUrlIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(repoUrl));

                //get activities that open this url
                List<ResolveInfo> activitiesToOpenUrlIntentList =
                    context.getPackageManager().queryIntentActivities(openUrlIntent,
                        PackageManager.MATCH_ALL);

                List<Intent> intentList = new ArrayList<Intent>();

                //Get application info to exclude it from the intent chooser
                ApplicationInfo applicationInfo = context.getApplicationInfo();
                int stringId = applicationInfo.labelRes;
                String applicationName = (stringId == 0) ?
                    applicationInfo.nonLocalizedLabel.toString().toLowerCase() :
                    context.getString(stringId).toLowerCase();

                if (!activitiesToOpenUrlIntentList.isEmpty()) {
                    for (ResolveInfo resolveInfo : activitiesToOpenUrlIntentList) {
                        String packageName = resolveInfo.activityInfo.packageName;
                        //create and add intent from other applications
                        //this way MGit doesn't show up to open the remote url
                        if (!packageName.toLowerCase().contains(applicationName)) {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(repoUrl));
                            intent.setComponent(new ComponentName(packageName,
                                resolveInfo.activityInfo.name));
                            intent.setAction(Intent.ACTION_VIEW);
                            intent.setPackage(packageName);
                            intentList.add(intent);
                        }
                    }
                    if (!intentList.isEmpty()) {
                        String title =
                            String.format(context.getString(R.string.dialog_open_remote_title),
                                repo.getDiaplayName());
                        Intent chooserIntent = Intent.createChooser(intentList.remove(0), title);
                        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS,
                            intentList.toArray(new Parcelable[intentList.size()]));
                        context.startActivity(chooserIntent);
                    } else {
                        Timber.tag(TAG).i(context.getString(R.string.dialog_open_remote_no_app_available));
                        Toast.makeText(context, R.string.dialog_open_remote_no_app_available,
                            Toast.LENGTH_LONG).show();
                    }
                }
            };
        }

        if (repoHasHttpRemote) {
            List<String> stringList = new ArrayList<>(3);
            stringList.addAll(Arrays.asList(context.getResources().getStringArray(R.array.dialog_choose_repo_action_items)));
            stringList.add(context.getString(R.string.dialog_open_remote));
            String[] options_values = stringList.toArray(new String[0]);

            context.showOptionsDialog(R.string.dialog_choose_option, options_values, dialog);
        } else {
            context.showOptionsDialog(R.string.dialog_choose_option,
                R.array.dialog_choose_repo_action_items, dialog);
        }
    }

    private void showRemoveRepoDialog(BaseCompatActivity context, final Repo repo) {
        context.showMessageDialog(R.string.dialog_delete_repo_title,
            R.string.dialog_delete_repo_msg, R.string.label_delete, (dialogInterface, i) -> {
                repo.deleteRepo(true);
                repo.cancelTask();
            });
    }

    private void showRenameRepoDialog(final BaseCompatActivity context, final Repo repo) {
        context.showEditTextDialog(R.string.dialog_rename_repo_title,
            R.string.dialog_rename_repo_hint, R.string.label_rename, newRepoName -> {
                if (!repo.renameRepo(newRepoName)) {
                    context.showToastMessage(R.string.error_rename_repo_fail);
                }
            });
    }

    private class RepoListItemHolder {
        public TextView repoTitle;
        public TextView repoRemote;
        public TextView commitAuthor;
        public TextView commitMsg;
        public TextView commitTime;
        public ImageView authorIcon;
        public View progressContainer;
        public View commitMsgContainer;
        public TextView progressMsg;
        public ImageView cancelBtn;
    }

}
