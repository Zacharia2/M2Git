package ts.realms.m2git.local.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;

import ts.realms.m2git.R;
import ts.realms.m2git.core.models.Repo;

/**
 * Created by lee on 2015-02-01.
 */
public class Profile {

    private static final int sTheme = -1;
    private static SharedPreferences sSharedPreference;
    private static boolean sHasLastCloneFail = false;
    private static Repo sLastFailRepo;

    private static SharedPreferences getProfileSharedPreference(Context context) {
        if (sSharedPreference == null) {
            sSharedPreference = context.getSharedPreferences(
                context.getString(R.string.preference_file_key),
                Context.MODE_PRIVATE);
        }
        return sSharedPreference;
    }

    public static String getUsername(Context context) {
        String userNamePrefKey = context.getString(R.string.pref_key_git_user_name);
        return getProfileSharedPreference(context).getString(userNamePrefKey, "");
    }

    public static String getEmail(Context context) {
        String userEmailPrefKey = context.getString(R.string.pref_key_git_user_email);
        return getProfileSharedPreference(context).getString(userEmailPrefKey, "");
    }

    public static String getTokenAccount(Context context) {
        String tokenAccount = context.getString(R.string.pref_key_personal_access_tokens_account);
        return getProfileSharedPreference(context).getString(tokenAccount, "");
    }

    public static String getTokenSecretKey(Context context) {
        String tokenSecretKey = context.getString(R.string.pref_key_personal_access_tokens_secret_key);
        return getProfileSharedPreference(context).getString(tokenSecretKey, "");
    }

    public static boolean hasLastCloneFailed() {
        return sHasLastCloneFail;
    }

    public static Repo getLastCloneTryRepo() {
        return sLastFailRepo;
    }

    public static void setLastCloneFailed(Repo repo) {
        sHasLastCloneFail = true;
        sLastFailRepo = repo;
    }

    public static void setLastCloneSuccess() {
        sHasLastCloneFail = false;
    }

    public static synchronized int getTheme(Context context) {
        // silly, but Android framework want strings as value array for ListPreference
        return Integer.parseInt(getProfileSharedPreference(context).getString(context.getString(R.string.pref_key_use_theme_id), "0"));
    }

    public static int getThemeResource(Context context) {
        final int[] themes = {R.style.AppTheme, R.style.DarkAppTheme};
        return themes[getTheme(context)];
    }

    public static String getCodeMirrorTheme(Context context) {
        final String[] themes = context.getResources().getStringArray(R.array.codemirror_theme_names);
        return themes[getTheme(context)];
    }

    public static int getStyledResource(Context context, int unstyled) {
        TypedArray a = context.getTheme().obtainStyledAttributes(getThemeResource(context), new int[]{unstyled});
        int styled = a.getResourceId(0, 0);
        a.recycle();
        return styled;
    }

    public static boolean useEnglishLocale(Context context) {
        String useEnglishPrefKey = context.getString(R.string.pref_key_use_english);
        return getProfileSharedPreference(context).getBoolean(useEnglishPrefKey, false);
    }
}
