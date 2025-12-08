package ts.realms.m2git.local.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.KeyPairGeneratorSpec;

import com.securepreferences.SecurePreferences;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.Calendar;

import javax.security.auth.x500.X500Principal;

import timber.log.Timber;
import ts.realms.m2git.common.errors.SecurePrefsException;
import ts.realms.m2git.utils.BasicFunctions;

/**
 * Securely store sensitive data in prefs, encrypting with a key pair that is stored in
 * the Android KeyStore, thus min of API 18 (4.3) is required.
 * <p>
 * The basic idea came from:
 * Ref: https://medium.com/@ali.muzaffar/securing-sharedpreferences-in-android-a21883a9cbf8
 * <p>
 * while the actual code comes from:
 * Ref: https://medium.com/@ericfu/securely-storing-secrets-in-an-android-application-501f030ae5a3
 * <p>
 * But in this class, all we do is use the generated RSA cert as the "password" used for AES encryption
 * by the SecurePreferences library, by taking the md5 of the RSA keys certificates toString()
 */

public class SecurePrefsHelper {

    private static final String AndroidKeyStore = "AndroidKeyStore";
    private static final String KEY_ALIAS = "mgit_prefs";
    private static final String SEC_PREFS_FILE_NAME = "sec_prefs.xml";
    private static final String KEY_ALGORITHM_RSA = "RSA"; //KeyProperties.KEY_ALGORITHM_RSA is only available in API 23, so need to define it here
    private final KeyStore mKeyStore;
    SharedPreferences mSecurePrefs;

    public SecurePrefsHelper(Context context) throws SecurePrefsException {

        try {
            mKeyStore = KeyStore.getInstance(AndroidKeyStore);
            mKeyStore.load(null);

            // make sure we have a keypair in keystore
            generateKeyPair(context);

            KeyStore.PrivateKeyEntry keypair = (KeyStore.PrivateKeyEntry) mKeyStore.getEntry(KEY_ALIAS, null);
            if (keypair == null) {
                throw new SecurePrefsException("missing keypair");
            }
            String prefsPassword = BasicFunctions.md5(keypair.getCertificate().toString());
            Timber.w("pref password %s", prefsPassword);
            mSecurePrefs = new SecurePreferences(context, prefsPassword, SEC_PREFS_FILE_NAME);

        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException |
                 InvalidAlgorithmParameterException | NoSuchProviderException | IOException |
                 UnrecoverableEntryException e) {
            Timber.e(e, "keystore error");
            throw new SecurePrefsException(e);
        }
    }

    void generateKeyPair(Context context) throws NoSuchProviderException, NoSuchAlgorithmException,
        InvalidAlgorithmParameterException, KeyStoreException {
        // Generate the RSA key pairs
        if (!mKeyStore.containsAlias(KEY_ALIAS)) {
            // Generate a key pair for encryption
            Calendar start = Calendar.getInstance();
            Calendar end = Calendar.getInstance();
            end.add(Calendar.YEAR, 30);
            KeyPairGeneratorSpec spec = new KeyPairGeneratorSpec.Builder(context)
                .setAlias(KEY_ALIAS)
                .setSubject(new X500Principal("CN=" + KEY_ALIAS))
                .setSerialNumber(BigInteger.TEN)
                .setStartDate(start.getTime())
                .setEndDate(end.getTime())
                .build();
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(KEY_ALGORITHM_RSA, AndroidKeyStore);
            kpg.initialize(spec);
            kpg.generateKeyPair();
        }
    }

    /**
     * Retrieve a String value from the secured preferences.
     *
     * @param pref
     * @return value of pref or null if no such pref
     */
    public String get(String pref) {
        return mSecurePrefs.getString(pref, null);
    }

    /**
     * Store a String value into secured preferences.
     *
     * @param name
     * @param value
     */
    public void set(String name, String value) {
        mSecurePrefs.edit().putString(name, value).apply();
    }
}
