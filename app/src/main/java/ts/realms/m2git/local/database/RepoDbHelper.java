package ts.realms.m2git.local.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.StringCharacterIterator;

/**
 * Created by sheimi on 8/6/13.
 * Modify by 霆枢 on 2025/12/04
 * 创建一个数据库repo.db，并包含两个表，repo表和credentials表。
 */
public class RepoDbHelper extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "repo.db";

    public RepoDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * 此方法暂未使用。
     */
    public static String addSlashes(String text) {
        final StringBuilder stringBuilder = new StringBuilder(text.length() * 2);
        final StringCharacterIterator iterator = new StringCharacterIterator(text);

        char character = iterator.current();

        while (character != StringCharacterIterator.DONE) {
            if (character == '"') stringBuilder.append("\\\"");
            else if (character == '\'') stringBuilder.append("''");
            else if (character == '\\') stringBuilder.append("\\\\");
            else if (character == '\n') stringBuilder.append("\\n");
            else if (character == '{') stringBuilder.append("\\{");
            else if (character == '}') stringBuilder.append("\\}");
            else stringBuilder.append(character);

            character = iterator.next();
        }

        return stringBuilder.toString();
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL(RepoContract.REPO_ENTRY_CREATE);
        sqLiteDatabase.execSQL(RepoContract.REPO_CREDENTIALS_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i2) {
        sqLiteDatabase.execSQL(RepoContract.REPO_ENTRY_DROP);
        sqLiteDatabase.execSQL(RepoContract.REPO_CREDENTIALS_DROP);
        onCreate(sqLiteDatabase);
    }
}
