package security;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Creates and verifies versioned, salted PBKDF2 password hashes. */
public final class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "pbkdf2-sha256";
    private static final int ITERATIONS = 600_000;
    private static final int MAX_ACCEPTED_ITERATIONS = 2_000_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private PasswordHasher() {}

    /** Hashes the supplied password and clears the supplied character array. */
    public static String hash(char[] password) {
        if (password == null) {
            throw new IllegalArgumentException("Password must not be empty.");
        }
        try {
            if (password.length == 0) {
                throw new IllegalArgumentException("Password must not be empty.");
            }
            byte[] salt = new byte[SALT_BYTES];
            RANDOM.nextBytes(salt);
            byte[] hash = derive(password, salt, ITERATIONS);

            return PREFIX + "$" + ITERATIONS + "$"
                    + ENCODER.encodeToString(salt) + "$"
                    + ENCODER.encodeToString(hash);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    /** Verifies the supplied password and clears the supplied character array. */
    public static boolean verify(char[] password, String encodedHash) {
        if (password == null) {
            return false;
        }
        try {
            if (password.length == 0 || !isEncodedHash(encodedHash)) {
                return false;
            }

            String[] parts = encodedHash.split("\\$", -1);
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = DECODER.decode(parts[2]);
            byte[] expectedHash = DECODER.decode(parts[3]);
            byte[] actualHash = derive(password, salt, iterations);
            return MessageDigest.isEqual(expectedHash, actualHash);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public static boolean isEncodedHash(String encodedHash) {
        if (encodedHash == null) {
            return false;
        }

        String[] parts = encodedHash.split("\\$", -1);
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }

        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = DECODER.decode(parts[2]);
            byte[] hash = DECODER.decode(parts[3]);
            return iterations >= ITERATIONS
                    && iterations <= MAX_ACCEPTED_ITERATIONS
                    && salt.length == SALT_BYTES
                    && hash.length == HASH_BITS / 8;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec keySpec = new PBEKeySpec(password, salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM)
                    .generateSecret(keySpec)
                    .getEncoded();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Password hashing is unavailable.", ex);
        } finally {
            keySpec.clearPassword();
        }
    }
}
