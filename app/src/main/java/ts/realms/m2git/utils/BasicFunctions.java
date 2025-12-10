package ts.realms.m2git.utils;

import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.nostra13.universalimageloader.core.ImageLoader;

import org.jetbrains.annotations.NotNull;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import timber.log.Timber;
import ts.realms.m2git.ui.components.dialogs.ErrorDialog;
import ts.realms.m2git.ui.screens.main.BaseCompatActivity;

/**
 * Created by sheimi on 8/19/13.
 */
public class BasicFunctions {

    private static BaseCompatActivity mActiveActivity;

    public static String md5(final String s) {
        try {
            // Create MD5 Hash
            MessageDigest digest = java.security.MessageDigest
                .getInstance("MD5");
            digest.update(s.getBytes());
            byte[] messageDigest = digest.digest();

            // Create Hex String
            StringBuilder hexString = new StringBuilder();
            for (byte b : messageDigest) {
                StringBuilder h = new StringBuilder(Integer.toHexString(0xFF & b));
                while (h.length() < 2)
                    h.insert(0, "0");
                hexString.append(h);
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            Timber.e(e);
        }
        return "";
    }

    public static void setAvatarImage(ImageView imageView, String email) {
        String avatarUri = "";
        if (!email.isEmpty())
            avatarUri = "avatar://" + md5(email);

        ImageLoader im = BasicFunctions.getImageLoader();
        im.displayImage(avatarUri, imageView);
    }

    public static BaseCompatActivity getActiveActivity() {
        return mActiveActivity;
    }

    public static void setActiveActivity(BaseCompatActivity activity) {
        mActiveActivity = activity;
    }

    public static ImageLoader getImageLoader() {
        return getActiveActivity().getImageLoader();
    }

    public static void showError(@NonNull @NotNull BaseCompatActivity activity, @StringRes final int errorTitleRes, @StringRes final int errorRes) {
        ErrorDialog errorDialog = new ErrorDialog();
        errorDialog.setErrorRes(errorRes);
        errorDialog.setErrorTitleRes(errorTitleRes);
        errorDialog.show(activity.getSupportFragmentManager(), "error-dialog");
    }

    public static void showException(@NonNull @NotNull BaseCompatActivity activity, Throwable throwable, @StringRes final int errorTitleRes, @StringRes final int errorRes) {
        ErrorDialog errorDialog = new ErrorDialog();
        errorDialog.setThrowable(throwable);
        errorDialog.setErrorRes(errorRes);
        errorDialog.setErrorTitleRes(errorTitleRes);
        errorDialog.show(activity.getSupportFragmentManager(), "exception-dialog");
    }


    public static void showException(@NonNull @NotNull BaseCompatActivity activity, @NonNull Throwable throwable, @StringRes final int errorRes) {
        showException(activity, throwable, 0, errorRes);
    }

    public static void showException(@NonNull @NotNull BaseCompatActivity activity, @NonNull Throwable throwable) {
        showException(activity, throwable, 0);
    }
}
