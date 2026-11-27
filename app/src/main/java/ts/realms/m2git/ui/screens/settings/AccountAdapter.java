package ts.realms.m2git.ui.screens.settings;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import ts.realms.m2git.R;
import ts.realms.m2git.core.models.Credential;

/**
 * 凭证列表适配器。
 * 数据源来自 RepoDbManager.queryAllCredential()，变更由 CredentialActivity 的
 * RepoDbObserver 驱动刷新。
 */
public class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.VH> {

    public interface Listener {
        void onEdit(Credential c);
        void onDelete(Credential c);
    }

    private final List<Credential> mData;
    private final Listener mListener;

    public AccountAdapter(List<Credential> data, Listener listener) {
        mData = data;
        mListener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.list_item_account, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Credential c = mData.get(position);
        h.account.setText(c.getTokenAccount());
        h.token.setText(h.itemView.getContext()
            .getString(R.string.credential_token_prefix, mask(c.getTokenSecret())));
        int n = c.getRelRepoId() == null ? 0 : c.getRelRepoId().length;
        h.meta.setText(n == 0
            ? h.itemView.getContext().getString(R.string.credential_no_related_repos)
            : h.itemView.getContext().getString(R.string.credential_related_count, n));
        h.itemView.setOnClickListener(v -> mListener.onEdit(c));
        h.delete.setOnClickListener(v -> mListener.onDelete(c));
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    /** 显示时对 secret 做掩码，避免明文暴露。 */
    private static String mask(String s) {
        if (s == null || s.isEmpty()) return "—";
        if (s.length() <= 8) return "••••••";
        return s.substring(0, 4) + "••••" + s.substring(s.length() - 4);
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView account;
        final TextView token;
        final TextView meta;
        final View delete;

        VH(View v) {
            super(v);
            account = v.findViewById(R.id.accountName);
            token   = v.findViewById(R.id.accountToken);
            meta    = v.findViewById(R.id.accountMeta);
            delete  = v.findViewById(R.id.deleteButton);
        }
    }
}