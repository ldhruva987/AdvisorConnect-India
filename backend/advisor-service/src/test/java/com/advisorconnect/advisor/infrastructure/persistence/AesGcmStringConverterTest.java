package com.advisorconnect.advisor.infrastructure.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The columns this converter protects held legal names, dates of birth and home addresses in
 * clear text while the entity's Javadoc claimed pgcrypto was encrypting them. These tests pin
 * the properties that make the claim true now.
 */
class AesGcmStringConverterTest {

    /** Test-only, fixed so failures are reproducible. 32 bytes, as AES-256 requires. */
    private static final String KEY = "K9x2Lq7vZ3mR8sT1wY6bN4jH0aC5dF2gP7uE9iO3kM0=";
    private static final String OTHER_KEY = "Zm9vYmFyYmF6cXV1eDEyMzQ1Njc4OTBhYmNkZWZnaGk=";

    private final AesGcmStringConverter converter = new AesGcmStringConverter(KEY, false);

    // ─────────────────────────────────────────────────────────────── round trip

    @Test
    @DisplayName("a value survives encrypt then decrypt unchanged")
    void roundTrips() {
        String plaintext = "Wilhelmina O'Brien-Ndlovu";

        String stored = converter.convertToDatabaseColumn(plaintext);

        assertThat(stored).isNotEqualTo(plaintext);
        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("non-ASCII plaintext round trips — the converter is byte-safe, not ASCII-safe")
    void roundTripsUnicode() {
        String plaintext = "Ana María Nováková-Þórsdóttir, 東京都渋谷区";

        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(plaintext)))
                .isEqualTo(plaintext);
    }

    @Test
    @DisplayName("the stored column is base64 and long enough to hold the IV and GCM tag")
    void storedFormatCarriesIvAndTag() {
        String stored = converter.convertToDatabaseColumn("x");

        byte[] raw = Base64.getDecoder().decode(stored);
        // 12-byte IV + 1 byte of ciphertext + 16-byte tag.
        assertThat(raw).hasSize(12 + 1 + 16);
    }

    // ──────────────────────────────────────────────────────────── IV uniqueness

    /**
     * The whole security argument for GCM collapses under IV reuse with a fixed key: two
     * messages under the same IV leak their XOR and let the authentication key be recovered. A
     * deterministic converter — the obvious implementation, and the one that makes columns
     * searchable — would do exactly that.
     */
    @Test
    @DisplayName("the same plaintext encrypts to a different column every time")
    void everyEncryptionUsesAFreshIv() {
        String plaintext = "1985-03-14";

        String first = converter.convertToDatabaseColumn(plaintext);
        String second = converter.convertToDatabaseColumn(plaintext);

        assertThat(first).isNotEqualTo(second);
        assertThat(converter.convertToEntityAttribute(first)).isEqualTo(plaintext);
        assertThat(converter.convertToEntityAttribute(second)).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("200 encryptions of one value yield 200 distinct IVs")
    void ivsDoNotRepeatAcrossManyEncryptions() {
        Set<String> ivs = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            byte[] raw = Base64.getDecoder().decode(converter.convertToDatabaseColumn("same"));
            ivs.add(Base64.getEncoder().encodeToString(java.util.Arrays.copyOf(raw, 12)));
        }
        assertThat(ivs).hasSize(200);
    }

    // ─────────────────────────────────────────────────────────────── tampering

    @Test
    @DisplayName("flipping a bit in the ciphertext fails the GCM tag instead of decrypting")
    void tamperedCiphertextIsRejected() {
        byte[] raw = Base64.getDecoder().decode(converter.convertToDatabaseColumn("42 Privet Drive"));
        // Byte 12 is the first byte of ciphertext, past the IV.
        raw[12] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> converter.convertToEntityAttribute(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("decrypt");
    }

    @Test
    @DisplayName("editing the IV also fails — the IV is authenticated, not just carried")
    void tamperedIvIsRejected() {
        byte[] raw = Base64.getDecoder().decode(converter.convertToDatabaseColumn("42 Privet Drive"));
        raw[0] ^= 0x01;

        String tampered = Base64.getEncoder().encodeToString(raw);
        assertThatThrownBy(() -> converter.convertToEntityAttribute(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("truncating the tag is rejected rather than silently accepted")
    void truncatedCiphertextIsRejected() {
        byte[] raw = Base64.getDecoder().decode(converter.convertToDatabaseColumn("42 Privet Drive"));
        byte[] truncated = java.util.Arrays.copyOf(raw, raw.length - 4);

        String bad = Base64.getEncoder().encodeToString(truncated);
        assertThatThrownBy(() -> converter.convertToEntityAttribute(bad))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a column encrypted under a different key does not decrypt")
    void wrongKeyIsRejected() {
        String stored = new AesGcmStringConverter(OTHER_KEY, false)
                .convertToDatabaseColumn("someone else's name");

        assertThatThrownBy(() -> converter.convertToEntityAttribute(stored))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a column that is not base64 at all is rejected, not silently returned")
    void nonBase64ColumnIsRejected() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("not base64 !!!"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a column too short to contain an IV is rejected")
    void tooShortColumnIsRejected() {
        String tooShort = Base64.getEncoder().encodeToString(new byte[8]);

        assertThatThrownBy(() -> converter.convertToEntityAttribute(tooShort))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IV");
    }

    // ───────────────────────────────────────────────────────── null and blank

    /**
     * Encrypting {@code ""} would turn "this optional field was never filled in" into an opaque
     * 44-character blob, making it unanswerable in SQL and non-idempotent besides.
     */
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    @DisplayName("blank values pass through both directions untouched")
    void blankPassesThrough(String blank) {
        assertThat(converter.convertToDatabaseColumn(blank)).isEqualTo(blank);
        assertThat(converter.convertToEntityAttribute(blank)).isEqualTo(blank);
    }

    @Test
    @DisplayName("null passes through as null in both directions")
    void nullPassesThrough() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    // ───────────────────────────────────────────────────── key configuration

    /**
     * Booting with encryption silently disabled is the exact state this class exists to end, and
     * it is invisible until a breach makes it visible. Refusing to start is the correct outcome.
     */
    @Test
    @DisplayName("a blank key outside the test profile aborts startup")
    void blankKeyOutsideTestProfileFailsFast() {
        assertThatThrownBy(() -> new AesGcmStringConverter("", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PII_ENCRYPTION_KEY");
    }

    @Test
    @DisplayName("a blank key under the test profile degrades to pass-through instead")
    void blankKeyUnderTestProfileIsPassThrough() {
        AesGcmStringConverter passThrough = new AesGcmStringConverter(null, true);

        assertThat(passThrough.convertToDatabaseColumn("plain")).isEqualTo("plain");
        assertThat(passThrough.convertToEntityAttribute("plain")).isEqualTo("plain");
    }

    @Test
    @DisplayName("a key of the wrong length is rejected — 16 bytes is not AES-256")
    void shortKeyIsRejected() {
        String sixteenBytes = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new AesGcmStringConverter(sixteenBytes, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("a key that is not valid base64 is rejected with a message that says so")
    void nonBase64KeyIsRejected() {
        assertThatThrownBy(() -> new AesGcmStringConverter("this is not base64 %%%", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base64");
    }

    /** Neither the key nor any derivative of it may appear in a message that reaches a log. */
    @Test
    @DisplayName("configuration failures never echo the key material")
    void failuresDoNotLeakTheKey() {
        String wrongLength = Base64.getEncoder().encodeToString("secret-material!".getBytes());

        assertThatThrownBy(() -> new AesGcmStringConverter(wrongLength, false))
                .hasMessageNotContaining(wrongLength)
                .hasMessageNotContaining("secret-material");
    }

    // ───────────────────────────────────────────────── Spring wiring contract

    @Test
    @DisplayName("the Spring constructor treats an active 'test' profile as the test profile")
    void springConstructorDetectsTestProfile() {
        MockEnvironment testEnv = new MockEnvironment();
        testEnv.setActiveProfiles("test");

        assertThatCode(() -> new AesGcmStringConverter("", testEnv)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the Spring constructor fails fast when no profile is active and no key is set")
    void springConstructorFailsFastWithoutProfile() {
        assertThatThrownBy(() -> new AesGcmStringConverter("", new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("the Spring constructor encrypts for real when a key is configured")
    void springConstructorUsesConfiguredKey() {
        AesGcmStringConverter wired = new AesGcmStringConverter(KEY, new MockEnvironment());

        String stored = wired.convertToDatabaseColumn("Grace Hopper");
        assertThat(stored).isNotEqualTo("Grace Hopper");
        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo("Grace Hopper");
    }
}
