package fr.traqueur.victor.security;

import java.util.Arrays;

/**
 * Secure storage for database credentials using char[] instead of String.
 *
 * <p>This class provides better security for password handling by:
 * <ul>
 *   <li>Storing passwords as char[] which can be cleared from memory</li>
 *   <li>Not appearing in heap dumps or memory dumps as plaintext</li>
 *   <li>Implementing AutoCloseable for automatic cleanup</li>
 *   <li>Preventing accidental logging via toString()</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * try (SecureCredentials creds = new SecureCredentials("user", "pass")) {
 *     // Use credentials
 *     String user = creds.getUsername();
 *     char[] pass = creds.getPassword();
 * } // Password automatically cleared
 * }</pre>
 *
 * @since 2.0.0
 */
public final class SecureCredentials implements AutoCloseable {

    private final String username;
    private final char[] password;
    private volatile boolean cleared = false;

    /**
     * Creates secure credentials from username and password.
     *
     * @param username the username (can be null)
     * @param password the password (will be copied, original should be cleared by caller)
     */
    public SecureCredentials(String username, char[] password) {
        this.username = username;
        this.password = password != null ? Arrays.copyOf(password, password.length) : null;
    }

    /**
     * Convenience constructor that accepts String password.
     * Note: String passwords are less secure than char[] as they cannot be cleared from memory.
     *
     * @param username the username
     * @param password the password as String
     */
    public SecureCredentials(String username, String password) {
        this.username = username;
        this.password = password != null ? password.toCharArray() : null;
    }

    /**
     * Gets the username.
     *
     * @return the username, may be null
     */
    public String getUsername() {
        checkNotCleared();
        return username;
    }

    /**
     * Gets the password.
     *
     * <p><b>WARNING:</b> The returned array is the internal array.
     * Do NOT modify or clear it. Do NOT store it beyond the lifecycle of this object.
     *
     * @return the password as char array, may be null
     * @throws IllegalStateException if credentials have been cleared
     */
    public char[] getPassword() {
        checkNotCleared();
        return password;
    }

    /**
     * Gets the password as a String.
     *
     * <p><b>WARNING:</b> This is less secure than using getPassword()
     * as Strings cannot be cleared from memory.
     *
     * @return the password as String, may be null
     * @throws IllegalStateException if credentials have been cleared
     */
    public String getPasswordAsString() {
        checkNotCleared();
        return password != null ? new String(password) : null;
    }

    /**
     * Checks if credentials have been cleared.
     *
     * @return true if cleared, false otherwise
     */
    public boolean isCleared() {
        return cleared;
    }

    /**
     * Clears the password from memory.
     * This method is idempotent and can be called multiple times safely.
     */
    public void clear() {
        if (!cleared && password != null) {
            Arrays.fill(password, '\0');
            cleared = true;
        }
    }

    /**
     * Automatically clears credentials when used in try-with-resources.
     */
    @Override
    public void close() {
        clear();
    }

    private void checkNotCleared() {
        if (cleared) {
            throw new IllegalStateException("Credentials have been cleared");
        }
    }

    /**
     * Prevents accidental logging of credentials.
     * Returns a safe representation without exposing password.
     */
    @Override
    public String toString() {
        return "SecureCredentials{username='" + username + "', cleared=" + cleared + "}";
    }
}