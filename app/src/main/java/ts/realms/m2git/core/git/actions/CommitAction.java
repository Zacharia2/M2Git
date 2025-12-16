package ts.realms.m2git.core.git.actions;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import ts.realms.m2git.R;
import ts.realms.m2git.common.errors.StopTaskException;
import ts.realms.m2git.core.git.RepoAction;
import ts.realms.m2git.core.git.tasks.local.CommitChangesTask;
import ts.realms.m2git.core.models.Repo;
import ts.realms.m2git.local.preference.Profile;
import ts.realms.m2git.ui.components.dialogs.DummyDialogListener;
import ts.realms.m2git.ui.screens.fragments.RepoDetailActivity;

public class CommitAction extends RepoAction {

    public CommitAction(Repo repo, RepoDetailActivity activity) {
        super(repo, activity);
    }

    @Override
    public void execute() {
        /*
         * index.lock的作用：防止在当前运行的git过程以外去更改本地存储库的资源。
         * 避免了多个git进程同时进行操作更改导致问题！
         *
         * 每当你运行一个git进程时，git就会在.git目录创建一个index.lock文件。
         * 例如，在当前的git仓库里运行git add .来stage本地的修改点，git就会在git add执行的时候创建index.lock文件，
         * 命令执行结束后，删除该文件。
         *
         * 如果某个进程退出/结束的时候除了问题，可能会导致index.lock文件没有被清除掉，此时，你需要将index.lock文件
         * 手动移除
         *
         * https://blog.csdn.net/u010682774/article/details/115725354
         */
        try {
            // 获取当前仓库的状态
            Status status = mRepo.getGit().status().call();
            // 检查是否有更改
            if (status.hasUncommittedChanges()) {
                commit();
            } else {
                mActivity.showToastMessage(R.string.alert_uncommitted_changes);
            }
        } catch (StopTaskException | GitAPIException e) {
            throw new RuntimeException(e);
        }

        mActivity.closeOperationDrawer();
    }

    private void commit(String commitMsg, boolean isAmend, boolean stageAll, String authorName,
                        String authorEmail) {
        CommitChangesTask commitTask = new CommitChangesTask(mRepo, commitMsg, isAmend, stageAll,
            authorName, authorEmail, isSuccess -> mActivity.reset());
        commitTask.executeTask();
    }

    private void commit() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        LayoutInflater inflater = mActivity.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_commit, null);
        final EditText commitMsg = layout.findViewById(R.id.commitMsg);
        final AutoCompleteTextView commitAuthor = layout.findViewById(R.id.commitAuthor);
        final CheckBox isAmend = layout.findViewById(R.id.isAmend);
        final CheckBox autoStage = layout.findViewById(R.id.autoStage);
        HashSet<Author> authors = new HashSet<Author>();
        try {
            Iterable<RevCommit> commits = mRepo.getGit().log().setMaxCount(500).call();
            for (RevCommit commit : commits) {
                authors.add(new Author(commit.getAuthorIdent()));
            }
        } catch (Exception ignored) {
        }
        String profileUsername = Profile.getUsername(mActivity.getApplicationContext());
        String profileEmail = Profile.getEmail(mActivity.getApplicationContext());
        if (profileUsername != null && !profileUsername.isEmpty() && profileEmail != null && !profileEmail.isEmpty()) {
            authors.add(new Author(profileUsername, profileEmail));
        }
        ArrayList<Author> authorList = new ArrayList<Author>(authors);
        Collections.sort(authorList);
        AuthorsAdapter adapter = new AuthorsAdapter(mActivity, authorList);
        commitAuthor.setAdapter(adapter);
        isAmend.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                commitMsg.setText(mRepo.getLastCommitFullMsg());
            } else {
                commitMsg.setText("");
            }
        });
        final AlertDialog d =
            builder.setTitle(R.string.dialog_commit_title).setView(layout).setPositiveButton(R.string.dialog_commit_positive_label, null).setNegativeButton(R.string.label_cancel, new DummyDialogListener()).create();
        d.setOnShowListener(dialog -> {
            Button b = d.getButton(AlertDialog.BUTTON_POSITIVE);
            b.setOnClickListener(view -> {
                    String msg = commitMsg.getText().toString();
                    String author = commitAuthor.getText().toString().trim();
                    String authorName = null, authorEmail = null;
                    int ltidx;
                    if (msg.trim().isEmpty()) {
                        commitMsg.setError(mActivity.getString(R.string.error_no_commit_msg));
                        return;
                    }
                    if (!author.isEmpty()) {
                        ltidx = author.indexOf('<');
                        if (!author.endsWith(">") || ltidx == -1) {
                            commitAuthor.setError(mActivity.getString(R.string.error_invalid_author));
                            return;
                        }
                        authorName = author.substring(0, ltidx);
                        authorEmail = author.substring(ltidx + 1, author.length() - 1);
                    }
                    boolean amend = isAmend.isChecked();
                    boolean stage = autoStage.isChecked();

                    commit(msg, amend, stage, authorName, authorEmail);

                    d.dismiss();
                }

            );
        });
        d.show();
    }

    private class Author implements Comparable<Author> {
        private final String mName;
        private final String mEmail;
        private final ArrayList<String> mKeywords;
        private final String SPLIT_KEYWORDS = "[ .\\-_@]";

        Author(String username, String email) {
            mName = username;
            mEmail = email;
            mKeywords = new ArrayList<String>();
            Collections.addAll(mKeywords, mName.toLowerCase().split(SPLIT_KEYWORDS));
            Collections.addAll(mKeywords, mEmail.toLowerCase().split(SPLIT_KEYWORDS));
        }

        Author(PersonIdent personIdent) {
            this(personIdent.getName(), personIdent.getEmailAddress());
        }

        public String getEmail() {
            return mEmail;
        }

        public String getName() {
            return mName;
        }

        public String displayString() {
            return mName + " <" + mEmail + ">";
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Author)) {
                return false;
            }
            return mName.equals(((Author) o).mName) && mEmail.equals(((Author) o).mEmail);
        }

        @Override
        public int hashCode() {
            return mName.hashCode() + mEmail.hashCode() * 997;
        }

        @Override
        public int compareTo(Author another) {
            int c1;
            c1 = mName.compareTo(another.mName);
            if (c1 != 0) return c1;
            return mEmail.compareTo(another.mEmail);
        }

        public boolean matches(String constraint) {
            constraint = constraint.toLowerCase();
            if (mEmail.toLowerCase().startsWith(constraint)) {
                return true;
            }
            if (mName.toLowerCase().startsWith(constraint)) {
                return true;
            }

            for (String constraintKeyword : constraint.split(SPLIT_KEYWORDS)) {
                boolean ok = false;
                for (String keyword : mKeywords) {
                    if (keyword.startsWith(constraintKeyword)) {
                        ok = true;
                        break;
                    }
                }
                if (!ok) {
                    return false;
                }
            }
            return true;
        }
    }

    private class AuthorsAdapter extends BaseAdapter implements Filterable {
        List<Author> arrayList;
        List<Author> mOriginalValues;
        LayoutInflater inflater;

        public AuthorsAdapter(Context context, List<Author> arrayList) {
            this.arrayList = arrayList;
            inflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return arrayList.size();
        }

        @Override
        public Object getItem(int position) {
            return arrayList.get(position).displayString();
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            ViewHolder holder = null;

            if (convertView == null) {

                holder = new ViewHolder();
                convertView = inflater.inflate(android.R.layout.simple_dropdown_item_1line, null);
                holder.textView = (TextView) convertView;
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            holder.textView.setText(arrayList.get(position).displayString());
            return convertView;
        }

        @Override
        public Filter getFilter() {
            return new Filter() {

                @SuppressWarnings("unchecked")
                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    arrayList = (List<Author>) results.values; // has the filtered values
                    notifyDataSetChanged();  // notifies the data with new filtered values
                }

                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults results = new FilterResults();        // Holds the results of a
                    // filtering operation in values
                    List<Author> FilteredArrList = new ArrayList<Author>();

                    if (mOriginalValues == null) {
                        mOriginalValues = new ArrayList<Author>(arrayList); // saves the original
                        // data in mOriginalValues
                    }

                    if (constraint == null || constraint.length() == 0) {
                        results.count = mOriginalValues.size();
                        results.values = mOriginalValues;
                    } else {
                        for (int i = 0; i < mOriginalValues.size(); i++) {
                            Author data = mOriginalValues.get(i);
                            if (data.matches(constraint.toString())) {
                                FilteredArrList.add(data);
                            }
                        }
                        // set the Filtered result to return
                        results.count = FilteredArrList.size();
                        results.values = FilteredArrList;
                    }
                    return results;
                }
            };
        }

        private class ViewHolder {
            TextView textView;
        }
    }
}
