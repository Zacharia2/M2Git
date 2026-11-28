package ts.realms.m2git.core.models;

import android.database.Cursor;

import ts.realms.m2git.local.database.RepoContract;

public class Credential {
    private final long mID;
    private final String mTokenAccount;
    private final String mTokenSecret;
    private final String[] mRelRepo;

    public Credential(Cursor cursor) {
        mID = RepoContract.getOneCredentialId(cursor);
        mTokenAccount = RepoContract.getOneTokenAccount(cursor);
        mTokenSecret = RepoContract.getOneTokenSecret(cursor);
        mRelRepo = RepoContract.getOneRelReop(cursor);
    }

    public long getID() {
        return mID;
    }

    public String getTokenAccount() {
        return mTokenAccount;
    }

    public String getTokenSecret() {
        return mTokenSecret;
    }

    public String getDisplayName() {
        return mTokenAccount + " (#" + mID + ")";
    }

    public String[] getRelRepoId() {
        return mRelRepo;
    }
}
