package ts.realms.m2git.ui.common.errors;

/**
 * Exception in SecurePrefs processing.
 */

public class SecurePrefsException extends Exception {

    public SecurePrefsException(String s) {
        super(s);
    }

    public SecurePrefsException(Exception e) {
        super(e);
    }
}
