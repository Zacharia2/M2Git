package ts.realms.m2git.ui.components.adapters;

import android.content.Context;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Set;

import ts.realms.m2git.R;
import ts.realms.m2git.core.command.tasks.local.GetCommitTask;
import ts.realms.m2git.core.models.Repo;
import ts.realms.m2git.ui.screens.main.BaseCompatActivity;
import ts.realms.m2git.utils.BasicFunctions;


/**
 * Created by sheimi on 8/18/13.
 */
public class CommitsListAdapter extends BaseAdapter {

    private final Repo mRepo;
    private final DateFormat mCommitDateFormatter;
    private final Set<Integer> mChosenItems;
    private final Context mContext;
    private final String mFile;
    private final Object mProgressLock = new Object();
    private String mFilter;
    private ArrayList<RevCommit> mAll;
    private ArrayList<Integer> mFiltered;
    private BackgroundUpdate mUpdate;
    private int mPosted;
    private boolean mIsIncomplete;
    private int mProgressCursor;
    private long mPostAtTime;

    public CommitsListAdapter(Context context, Set<Integer> chosenItems,
                              Repo repo, String file) {
        super();
        mFile = file;
        mContext = context;
        mChosenItems = chosenItems;
        mRepo = repo;
        mAll = new ArrayList<RevCommit>();
        mFiltered = null;
        mFilter = null;
        mCommitDateFormatter = android.text.format.DateFormat.getDateFormat(mContext);
    }

    private boolean isAccepted(RevCommit in) {
        if (mFilter == null) {
            return true;
        }
        if (in.getId().toString().startsWith("commit " + mFilter.toLowerCase())) {
            return true;
        }
        /* Search in raw buffer is fast but it may find the string in
         * e.g. parents field or as part of keyword. So first search in
         * raw buffer and then look in parsed components if raw buffer
         * contains needle. */
        if (!new String(in.getRawBuffer()).contains(mFilter)) {
            return false;
        }
        return (in.getAuthorIdent().getName().contains(mFilter)
            || in.getAuthorIdent().getEmailAddress().contains(mFilter)
            || in.getCommitterIdent().getName().contains(mFilter)
            || in.getCommitterIdent().getEmailAddress().contains(mFilter)
            || in.getFullMessage().contains(mFilter));
    }

    private void stopFiltering() {
        try {
            mUpdate.cancel(true);
            mUpdate = null;
        } catch (Exception e) {
        }
    }

    private void doFiltering() {
        mFiltered = null;
        if (mFilter != null) {
            mUpdate = new BackgroundUpdate();
            mPosted = 0;
            mIsIncomplete = true;
            mFiltered = new ArrayList<>();
            mProgressCursor = 0;
            // Show first result after 100 ms
            mPostAtTime = System.nanoTime() + 100000000;
            mUpdate.execute();
        } else {
            notifyDataSetChanged();
        }
    }

    public void setFilter(String query) {
        synchronized (mProgressLock) {
            stopFiltering();
            if (query == null || query.isEmpty()) {
                mFilter = null;
            } else {
                mFilter = query;
            }
            doFiltering();
        }
    }

    @Override
    public int getCount() {
        if (mFilter == null)
            return mAll.size();
        if (mIsIncomplete)
            return mPosted + 1;
        return mFiltered.size();
    }

    @Override
    public long getItemId(int position) {
        if (mIsIncomplete && position >= mPosted) {
            return -1;
        }
        if (mFilter == null) {
            return position;
        } else {
            try {
                return mFiltered.get(position);
            } catch (Exception e) {
                return -1;
            }
        }
    }

    public RevCommit getItem(int position) {
        if (mIsIncomplete && position >= mPosted) {
            return null;
        }
        try {
            return (mFilter == null) ? mAll.get(position) : mAll.get(mFiltered.get(position));
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isProgressBar(int position) {
        if (mFilter == null)
            return position >= mAll.size();
        if (mIsIncomplete)
            return position >= mPosted;
        return position >= mFiltered.size();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        LayoutInflater inflater = LayoutInflater.from(mContext);
        if (isProgressBar(position)) {
            return new ProgressBar(mContext, null, android.R.attr.progressBarStyleLarge);
        }
        CommitsListItemHolder holder = null;
        if (convertView != null) {
            holder = (CommitsListItemHolder) convertView.getTag();
        }
        if (holder == null) {
            convertView = inflater.inflate(R.layout.list_item_commits, parent,
                false);
            holder = new CommitsListItemHolder();
            holder.commitsTitle = convertView
                .findViewById(R.id.commitTitle);
            holder.commitsIcon = convertView
                .findViewById(R.id.commitIcon);
            holder.commitAuthor = convertView
                .findViewById(R.id.commitAuthor);
            holder.commitsMsg = convertView
                .findViewById(R.id.commitMsg);
            holder.commitTime = convertView
                .findViewById(R.id.commitTime);
            convertView.setTag(holder);
        }
        RevCommit commit = getItem(position);
        PersonIdent person = commit.getAuthorIdent();
        Date date = person.getWhen();
        String email = person.getEmailAddress();

        holder.commitsTitle
            .setText(Repo.getCommitDisplayName(commit.getName()));
        holder.commitAuthor.setText(person.getName());
        holder.commitsMsg.setText(commit.getShortMessage());
        holder.commitTime.setText(mCommitDateFormatter.format(date));

        BasicFunctions.setAvatarImage(holder.commitsIcon, email);

        int color, colorResId;
        if (mChosenItems.contains(position)) {
            colorResId = R.color.pressed_sgit;
        } else {
            colorResId = android.R.color.transparent;
        }
        if (mContext instanceof BaseCompatActivity) {
            color = mContext.getResources().getColor(colorResId);
            convertView.setBackgroundColor(color);
        }
        return convertView;
    }

    public void clear() {
        synchronized (mProgressLock) {
            stopFiltering();
            mAll = new ArrayList<>();
            if (mFilter == null) {
                mFiltered = null;
            } else {
                mFiltered = new ArrayList<>();
            }
        }
    }

    public void resetCommit() {
        clear();
        GetCommitTask getCommitTask = new GetCommitTask(mRepo, mFile,
            commits -> {
                if (commits != null) {
                    // TODO why == null
                    synchronized (mProgressLock) {
                        stopFiltering();
                        mAll = new ArrayList<>(commits);
                        doFiltering();
                    }
                }
            });
        getCommitTask.executeTask();
    }

    private static class CommitsListItemHolder {
        public ImageView commitsIcon;
        public TextView commitsTitle;
        public TextView commitsMsg;
        public TextView commitAuthor;
        public TextView commitTime;
    }

    private class BackgroundUpdate extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            int i;
            for (i = mProgressCursor; i < mAll.size(); i++) {
                if (mFiltered.size() != mPosted && System.nanoTime() > mPostAtTime) {
                    synchronized (mProgressLock) {
                        mProgressCursor = i;
                        mPosted = mFiltered.size();
                        return null;
                    }
                }
                if (isCancelled()) {
                    return null;
                }
                if (isAccepted(mAll.get(i)))
                    mFiltered.add(i);
            }
            synchronized (mProgressLock) {
                mPosted = mFiltered.size();
                mIsIncomplete = false;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            if (isCancelled()) {
                return;
            }
            synchronized (mProgressLock) {
                notifyDataSetChanged();
                if (mIsIncomplete) {
                    // Updates after 1 s
                    mPostAtTime = System.nanoTime() + 1000000000;
                    mUpdate = new BackgroundUpdate();
                    mUpdate.execute();
                }
            }
        }
    }
}
