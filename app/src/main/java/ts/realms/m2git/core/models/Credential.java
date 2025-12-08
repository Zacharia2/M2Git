package ts.realms.m2git.core.models;

import android.database.Cursor;

import java.util.ArrayList;
import java.util.List;

import ts.realms.m2git.local.database.RepoContract;
import ts.realms.m2git.local.database.RepoDbManager;

public class Credential {
    private final int mID;
    private final String mTokenAccount;
    private final String mTokenSecret;
    private final String[] mRelRepo;

    public Credential(Cursor cursor) {
        mID = RepoContract.getCredentialId(cursor);
        mTokenAccount = RepoContract.getTokenAccount(cursor);
        mTokenSecret = RepoContract.getTokenSecret(cursor);
        mRelRepo = RepoContract.getRelReop(cursor);
    }

    public static long createCredential(String token_account, String token_secret) {
        return RepoDbManager.createCredential(token_account, token_secret);
    }

    public static List<Credential> getCredentialList(Cursor cursor) {
        List<Credential> credentials = new ArrayList<>();
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            credentials.add(new Credential(cursor));
            cursor.moveToNext();
        }
        return credentials;
    }

    public int getID() {
        return mID;
    }

    public String getTokenAccount() {
        return mTokenAccount;
    }

    public String getTokenSecret() {
        return mTokenSecret;
    }

    public String[] getRelReop() {
        return mRelRepo;
    }
}
