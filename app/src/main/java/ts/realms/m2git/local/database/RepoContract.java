package ts.realms.m2git.local.database;

import android.database.Cursor;
import android.provider.BaseColumns;

import java.util.Date;

/**
 * Created by sheimi on 8/6/13.
 * 数据库常量类
 */
public final class RepoContract {

    public static final String REPO_STATUS_NULL = "";
    public static final String REPO_ENTRY_DROP = "DROP TABLE IF EXISTS " + RepoEntry.TABLE_NAME;
    public static final String REPO_CREDENTIALS_DROP = "DROP TABLE IF EXISTS " + RepoCredential.TABLE_NAME;
    private static final String TEXT_TYPE = " TEXT ";
    private static final String INT_TYPE = " INTEGER ";
    private static final String PRIMARY_KEY_TYPE = INT_TYPE + "PRIMARY KEY " + "AUTOINCREMENT ";
    private static final String COMMA_SEP = ",";
    public static final String REPO_ENTRY_CREATE =
        "CREATE TABLE " + RepoEntry.TABLE_NAME + " ("
            + RepoEntry._ID + PRIMARY_KEY_TYPE + COMMA_SEP
            + RepoEntry.COLUMN_NAME_LOCAL_PATH + TEXT_TYPE + COMMA_SEP
            + RepoEntry.COLUMN_NAME_REMOTE_URL + TEXT_TYPE + COMMA_SEP
            + RepoEntry.COLUMN_NAME_USERNAME + TEXT_TYPE + COMMA_SEP
            + RepoEntry.COLUMN_NAME_PASSWORD + TEXT_TYPE + COMMA_SEP
            + RepoEntry.COLUMN_NAME_REPO_STATUS + TEXT_TYPE + COMMA_SEP
            + RepoEntry.COLUMN_NAME_LATEST_COMMITTER_UNAME + TEXT_TYPE + COMMA_SEP
            + RepoEntry.COLUMN_NAME_LATEST_COMMITTER_EMAIL + TEXT_TYPE + COMMA_SEP
            + RepoEntry.COLUMN_NAME_LATEST_COMMIT_DATE + TEXT_TYPE + COMMA_SEP
            + RepoEntry.COLUMN_NAME_LATEST_COMMIT_MSG + TEXT_TYPE
            + " )";
    public static final String REPO_CREDENTIALS_CREATE =
        "CREATE TABLE " + RepoCredential.TABLE_NAME + " ("
            + RepoCredential._ID + PRIMARY_KEY_TYPE + COMMA_SEP
            + RepoCredential.COLUMN_TOKEN_ACCOUNT + TEXT_TYPE + COMMA_SEP
            + RepoCredential.COLUMN_TOKEN_SECRET + TEXT_TYPE + COMMA_SEP
            + RepoCredential.COLUMN_REL_REPO + TEXT_TYPE
            + ")";

    public RepoContract() {
    }

    public static int getRepoID(Cursor cursor) {
        int columnIndex = cursor.getColumnIndex(RepoContract.RepoEntry._ID);
        return cursor.getInt(columnIndex);
    }

    public static String getLocalPath(Cursor cursor) {
        int columnIndex = cursor.getColumnIndex(RepoContract.RepoEntry.COLUMN_NAME_LOCAL_PATH);
        return cursor.getString(columnIndex);
    }

    public static String getRemoteURL(Cursor cursor) {
        int columnIndex = cursor.getColumnIndex(RepoContract.RepoEntry.COLUMN_NAME_REMOTE_URL);
        return cursor.getString(columnIndex);
    }

    public static String getRepoStatus(Cursor cursor) {
        int columnIndex = cursor.getColumnIndex(RepoContract.RepoEntry.COLUMN_NAME_REPO_STATUS);
        return cursor.getString(columnIndex);
    }

    public static String getLatestCommitterName(Cursor cursor) {
        int columnIndex = cursor.getColumnIndex(RepoContract.RepoEntry.COLUMN_NAME_LATEST_COMMITTER_UNAME);
        return cursor.getString(columnIndex);
    }

    public static String getLatestCommitterEmail(Cursor cursor) {
        int columnIndex = cursor.getColumnIndex(RepoContract.RepoEntry.COLUMN_NAME_LATEST_COMMITTER_EMAIL);
        return cursor.getString(columnIndex);
    }

    public static Date getLatestCommitDate(Cursor cursor) {
        int columnIndex = cursor.getColumnIndex(RepoContract.RepoEntry.COLUMN_NAME_LATEST_COMMIT_DATE);
        String longStr = cursor.getString(columnIndex);
        if (longStr == null || longStr.isEmpty()) {
            return null;
        }
        long time = Long.parseLong(longStr);
        return new Date(time);
    }

    public static String getLatestCommitMsg(Cursor cursor) {
        int columnIndex = cursor.getColumnIndex(RepoContract.RepoEntry.COLUMN_NAME_LATEST_COMMIT_MSG);
        return cursor.getString(columnIndex);
    }

    public static String getUsername(Cursor cursor) {
        int columnIndex = cursor.getColumnIndex(RepoContract.RepoEntry.COLUMN_NAME_USERNAME);
        return cursor.getString(columnIndex);
    }

    public static String getPassword(Cursor cursor) {
        int columnIndex = cursor.getColumnIndex(RepoContract.RepoEntry.COLUMN_NAME_PASSWORD);
        return cursor.getString(columnIndex);
    }

    public static int getOneCredentialId(Cursor cursor) {
        if (cursor.isBeforeFirst()) {
            cursor.moveToFirst();
        }
        int columnIndex = cursor.getColumnIndex(RepoContract.RepoCredential._ID);
        return cursor.getInt(columnIndex);
    }

    public static String getOneTokenAccount(Cursor cursor) {
        if (cursor.isBeforeFirst()) {
            cursor.moveToFirst();
        }
        int columnIndex = cursor.getColumnIndex(RepoContract.RepoCredential.COLUMN_TOKEN_ACCOUNT);
        return cursor.getString(columnIndex);
    }

    public static String getOneTokenSecret(Cursor cursor) {
        if (cursor.isBeforeFirst()) {
            cursor.moveToFirst();
        }
        int columnIndex = cursor.getColumnIndex(RepoContract.RepoCredential.COLUMN_TOKEN_SECRET);
        return cursor.getString(columnIndex);
    }

    public static String[] getOneRelReop(Cursor cursor) {
        if (cursor.isBeforeFirst()) {
            cursor.moveToFirst();
        }
        int columnIndex = cursor.getColumnIndex(RepoContract.RepoCredential.COLUMN_REL_REPO);
        String relRepoString = cursor.getString(columnIndex);
        if (relRepoString == null) return null;
        return relRepoString.split(",");
    }

    public static abstract class RepoEntry implements BaseColumns {
        public static final String TABLE_NAME = "repo";
        public static final String COLUMN_NAME_LOCAL_PATH = "local_path";
        public static final String COLUMN_NAME_REMOTE_URL = "remote_url";
        public static final String COLUMN_NAME_REPO_STATUS = "repo_status";
        public static final String COLUMN_NAME_USERNAME = "username";
        public static final String COLUMN_NAME_PASSWORD = "password";
        // latest commit's committer name
        public static final String COLUMN_NAME_LATEST_COMMITTER_UNAME = "latest_committer_uname";
        public static final String COLUMN_NAME_LATEST_COMMITTER_EMAIL = "latest_committer_email";
        public static final String COLUMN_NAME_LATEST_COMMIT_DATE = "latest_commit_date";
        public static final String COLUMN_NAME_LATEST_COMMIT_MSG = "latest_commit_msg";
        public static final String[] ALL_COLUMNS = {
            _ID,
            COLUMN_NAME_LOCAL_PATH,
            COLUMN_NAME_REMOTE_URL,
            COLUMN_NAME_USERNAME,
            COLUMN_NAME_PASSWORD,
            COLUMN_NAME_REPO_STATUS,
            COLUMN_NAME_LATEST_COMMITTER_UNAME,
            COLUMN_NAME_LATEST_COMMITTER_EMAIL,
            COLUMN_NAME_LATEST_COMMIT_DATE,
            COLUMN_NAME_LATEST_COMMIT_MSG,
        };
    }

    public static abstract class RepoCredential implements BaseColumns {
        public static final String TABLE_NAME = "credentials";
        public static final String COLUMN_TOKEN_ACCOUNT = "token_account";
        public static final String COLUMN_TOKEN_SECRET = "token_secret";
        public static final String COLUMN_REL_REPO = "rel_repo"; //COLUMN_REL_REPO使用列表文本存储例如："值1,值2,值3"。
        public static final String[] ALL_COLUMNS = {
            _ID,
            COLUMN_TOKEN_ACCOUNT,
            COLUMN_TOKEN_SECRET,
            COLUMN_REL_REPO
        };
    }

}
