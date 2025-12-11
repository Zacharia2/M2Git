package ts.realms.m2git.core.git.actions;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import org.eclipse.jgit.lib.Ref;

import java.util.List;

import ts.realms.m2git.R;
import ts.realms.m2git.core.git.RepoAction;
import ts.realms.m2git.core.models.Repo;
import ts.realms.m2git.local.preference.Profile;
import ts.realms.m2git.ui.screens.fragments.RepoDetailActivity;
import ts.realms.m2git.ui.components.dialogs.BaseDialogFragment;

public class MergeAction extends RepoAction {

    public MergeAction(Repo repo, RepoDetailActivity activity) {
        super(repo, activity);
    }

    @Override
    public void execute() {
        MergeDialog md = new MergeDialog();
        md.setArguments(mRepo.getBundle());
        md.show(mActivity.getSupportFragmentManager(), "merge-repo-dialog");
        mActivity.closeOperationDrawer();
    }

    public static class MergeDialog extends BaseDialogFragment {

        private Repo mRepo;
        private RepoDetailActivity mActivity;
        private ListView mBranchTagList;
        private Spinner mSpinner;
        private BranchTagListAdapter mAdapter;
        private CheckBox mCheckbox;

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            super.onCreateDialog(savedInstanceState);
            Bundle args = getArguments();
            if (args != null && args.containsKey(Repo.TAG)) {
                mRepo = (Repo) args.getSerializable(Repo.TAG);
            }

            mActivity = (RepoDetailActivity) getActivity();
            LayoutInflater inflater = mActivity.getLayoutInflater();
            View layout = inflater.inflate(R.layout.dialog_merge, null);
            AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);

            mBranchTagList = layout.findViewById(R.id.branchList);
            mSpinner = layout.findViewById(R.id.ffSpinner);
            mCheckbox = layout.findViewById(R.id.autoCommit);
            mAdapter = new BranchTagListAdapter(mActivity);
            mBranchTagList.setAdapter(mAdapter);
            builder.setView(layout);

            List<Ref> branches = mRepo.getLocalBranches();
            String currentBranchDisplayName = mRepo.getCurrentDisplayName();
            for (Ref branch : branches) {
                if (Repo.getCommitDisplayName(branch.getName()).equals(currentBranchDisplayName))
                    continue;
                mAdapter.add(branch);
            }

            builder.setTitle(R.string.dialog_merge_title);
            mBranchTagList.setOnItemClickListener((adapterView, view, position, id) -> {
                Ref commit = mAdapter.getItem(position);
                String mFFString = mSpinner.getSelectedItem().toString();
                mActivity.getRepoDelegate().mergeBranch(commit, mFFString, mCheckbox.isChecked());
                getDialog().cancel();
            });

            return builder.create();
        }

        private static class BranchTagListAdapter extends ArrayAdapter<Ref> {

            public BranchTagListAdapter(Context context) {
                super(context, 0);
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                LayoutInflater inflater = LayoutInflater.from(getContext());
                ListItemHolder holder;
                if (convertView == null) {
                    convertView = inflater.inflate(R.layout.list_item_dialog_choose_commit, parent
                        , false);
                    holder = new ListItemHolder();
                    holder.commitTitle = convertView.findViewById(R.id.commitTitle);
                    holder.commitIcon = convertView.findViewById(R.id.commitIcon);
                    convertView.setTag(holder);
                } else {
                    holder = (ListItemHolder) convertView.getTag();
                }
                String commitName = getItem(position).getName();
                String displayName = Repo.getCommitDisplayName(commitName);
                int commitType = Repo.getCommitType(commitName);
                switch (commitType) {
                    case Repo.COMMIT_TYPE_HEAD:
                        holder.commitIcon.setImageResource(Profile.getStyledResource(getContext()
                            , R.attr.ic_branch_l));
                        break;
                    case Repo.COMMIT_TYPE_TAG:
                        holder.commitIcon.setImageResource(Profile.getStyledResource(getContext()
                            , R.attr.ic_tag_l));
                        break;
                }
                holder.commitTitle.setText(displayName);
                return convertView;
            }

        }

        private static class ListItemHolder {
            public TextView commitTitle;
            public ImageView commitIcon;
        }

    }
}
