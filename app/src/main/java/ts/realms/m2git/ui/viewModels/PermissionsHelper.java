package ts.realms.m2git.ui.viewModels;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build.VERSION;
import android.os.Environment;

import androidx.core.content.ContextCompat;

public final class PermissionsHelper {
    public static final Companion Companion = new Companion();

    public static final class Companion {
        private Companion() {
        }

        public boolean isExternalStorageManager() {
            return VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager();
        }

        public boolean canReadStorage(Context context) {
            return ContextCompat.checkSelfPermission(context, "android.permission.READ_EXTERNAL_STORAGE")
                == PackageManager.PERMISSION_GRANTED || this.isExternalStorageManager();
        }
    }
}
