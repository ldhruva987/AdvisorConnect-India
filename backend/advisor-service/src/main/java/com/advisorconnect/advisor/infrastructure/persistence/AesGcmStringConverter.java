package com.advisorconnect.advisor.infrastructure.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Application-level AES-256-GCM encryption for the PII columns on {@code AdvisorApplication}.
 *
 * <p>The entity previously claimed these columns were "encrypted at rest via pgcrypto". They were
 * not. Nothing in this service, in any migration, or in the container setup ever called
 * {@code pgcrypto} — the {@code _enc} suffix was the only encryption present, and legal names,
 * dates of birth and home addresses were written to Postgres in clear text. This converter is
 * what makes the comment true.
 *
 * <p>Wire format is {@code Base64(iv || ciphertext||tag)}: a fresh 12-byte IV is generated per
 * encryption and prefixed to the output. GCM is catastrophically broken by IV reuse under a fixed
 * key, so the IV is never derived from the plaintext or from a counter — the same value encrypted
 * twice yields two different columns. That also means these columns are not equality-searchable,
 * which is the intended trade: nothing queries on a date of birth.
 *
 * <p>The 128-bit GCM tag authenticates the ciphertext, so a row edited directly in the database
 * fails to decrypt rather than silently yielding attacker-chosen plaintext.
 *
 * <p>Applied explicitly via {@code @Convert} rather than {@code autoApply}: auto-applying to every
 * {@code String} in the persistence unit would encrypt usernames and bios too, breaking the
 * queries that legitimately search them.
 */
@Component
@Converter(autoApply = false)
@Slf4j
public class AesGcmStringConverter implements AttributeConverter<String, String> {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String ALGORITHM = "AES";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    /** Null only when running under the {@code test} profile with no key configured. */
    private final SecretKey key;

    @Autowired
    public AesGcmStringConverter(
            @Value("${pii.encryption.key:}") String base64Key,
            Environment environment) {
        this(base64Key, environment != null && environment.acceptsProfiles(Profiles.of("test")));
    }

    /**
     * @param testProfile when true, a missing key degrades to pass-through instead of aborting
     *                    startup. Test slices that touch none of the PII columns should not be
     *                    forced to invent a key; anything else must have one.
     */
    AesGcmStringConverter(String base64Key, boolean testProfile) {
        if (base64Key == null || base64Key.isBlank()) {
            if (!testProfile) {
                // Failing to start is the correct outcome. The alternative — booting with
                // encryption silently disabled — is exactly the state this class was written to
                // end, and it is invisible until a breach makes it visible.
                throw new IllegalStateException(
                        "pii.encryption.key is not configured. advisor-service stores legal names, "
                                + "dates of birth and home addresses; it will not start and write "
                                + "them in clear text. Set PII_ENCRYPTION_KEY to a base64-encoded "
                                + "32-byte key.");
            }
            log.warn("pii.encryption.key is blank under the test profile — PII columns will be "
                    + "stored unencrypted. This is never acceptable outside tests.");
            this.key = null;
            return;
        }

        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("pii.encryption.key must be valid base64", e);
        }
        if (raw.length != KEY_LENGTH_BYTES) {
            // Length is reported because it is the one detail that makes this diagnosable; the
            // key material itself is never logged or echoed.
            throw new IllegalStateException(
                    "pii.encryption.key must decode to exactly " + KEY_LENGTH_BYTES
                            + " bytes for AES-256, got " + raw.length);
        }
        this.key = new SecretKeySpec(raw, ALGORITHM);
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        // Null and blank pass through untouched: encrypting "" would turn an absent optional
        // field into an opaque blob and make "is this set?" unanswerable in SQL.
        if (attribute == null || attribute.isBlank() || key == null) {
            return attribute;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            // Never fall back to storing the plaintext.
            throw new IllegalStateException("Failed to encrypt PII attribute", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank() || key == null) {
            return dbData;
        }
        byte[] combined;
        try {
            combined = Base64.getDecoder().decode(dbData);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Encrypted PII column is not valid base64", e);
        }
        if (combined.length <= IV_LENGTH_BYTES) {
            throw new IllegalStateException("Encrypted PII column is too short to contain an IV");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(TAG_LENGTH_BITS, combined, 0, IV_LENGTH_BYTES));
            byte[] plaintext = cipher.doFinal(
                    combined, IV_LENGTH_BYTES, combined.length - IV_LENGTH_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // Includes AEADBadTagException — a tampered or wrong-key column. Surfacing it is the
            // point of using an authenticated mode.
            throw new IllegalStateException("Failed to decrypt PII attribute", e);
        }
    }
}
