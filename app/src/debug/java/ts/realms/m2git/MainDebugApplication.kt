package ts.realms.m2git

import android.util.Log
import com.facebook.stetho.Stetho
import timber.log.Timber


/**
 * Provides debug-build specific Application.
 *
 *
 * To disable Stetho console logging change the setting in src/debug/res/values/bools.xml
 */
class MainDebugApplication : MainApplication() {
    override fun onCreate() {
        super.onCreate()

        Stetho.initializeWithDefaults(this)

        if (resources.getBoolean(R.bool.enable_stetho_timber_logging)) {
            Timber.plant(
                ConfigurableStethoTree(
                    ConfigurableStethoTree.Configuration.Builder()
                        .showTags(true)
                        .minimumPriority(Log.DEBUG)
                        .build()
                )
            )
            Timber.tag(LOGTAG).i("Using Stetho console logging")
        } else {
            Timber.plant(Timber.DebugTree())
        }
        Timber.i("Initialised Stetho")
    }

    companion object {
        private val LOGTAG: String = MainDebugApplication::class.java.simpleName
    }
}
