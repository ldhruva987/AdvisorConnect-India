package com.advisorconnect.user.application;

import com.advisorconnect.user.domain.model.UserProfile;
import com.advisorconnect.user.domain.port.out.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for user profiles.
 *
 * <p>This layer did not exist: {@code UserController} injected an {@code EntityManager} and did
 * its own {@code find}/mutate. Beyond the layering violation that had a real bug in it —
 * {@code PUT /users/me} mutated a managed entity with no transaction around it, so with
 * open-session-in-view there was nothing to flush the change and the "update" was silently
 * discarded on the next request. Everything here runs inside a transaction.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserProfileService {

    /** Keeps generated usernames to a sane display length before any collision suffix. */
    private static final int MAX_BASE_USERNAME_LENGTH = 30;

    /** Used when an email's local part contains no usable characters at all (e.g. {@code "+++@x.com"}). */
    private static final String FALLBACK_USERNAME = "user";

    /**
     * Upper bound on collision suffixing. Reaching it means something pathological is happening;
     * we fall back to a random suffix rather than spin.
     */
    private static final int MAX_SUFFIX_ATTEMPTS = 1_000;

    private final UserProfileRepository userProfileRepository;

    @Transactional(readOnly = true)
    public Optional<UserProfile> getById(UUID userId) {
        return userProfileRepository.findById(userId);
    }

    @Transactional(readOnly = true)
    public UserProfile getOrThrow(UUID userId) {
        return userProfileRepository.findById(userId)
                .orElseThrow(() -> new UserProfileNotFoundException(userId));
    }

    /**
     * Applies a partial update. A {@code null} field means "leave unchanged" — the same
     * semantics the controller implemented inline before — so a client can patch the bio without
     * having to resend the display name.
     */
    public UserProfile updateProfile(UUID userId, String displayName, String bio) {
        UserProfile profile = getOrThrow(userId);
        if (displayName != null) {
            profile.setDisplayName(displayName);
        }
        if (bio != null) {
            profile.setBio(bio);
        }
        return userProfileRepository.save(profile);
    }

    /**
     * Creates the profile for a newly registered account, driven by the {@code user.registered}
     * event. Until this existed nothing ever inserted a {@code user_profiles} row, so
     * {@code GET /users/me} answered 404 for every real user on the platform.
     *
     * <p><strong>Idempotent.</strong> Kafka delivery is at-least-once and the consumer may also be
     * replayed from the start of the topic, so a redelivery returns the existing profile untouched
     * rather than inserting a duplicate or overwriting a username the user has since edited.
     *
     * @param desiredUsername the preferred username (see {@link #usernameFromEmail}); if taken, a
     *                        numeric suffix is appended until it is not
     */
    public UserProfile createFromRegistration(UUID userId, String email, String desiredUsername) {
        Optional<UserProfile> existing = userProfileRepository.findById(userId);
        if (existing.isPresent()) {
            log.info("Profile for userId={} already exists — registration event ignored as duplicate", userId);
            return existing.get();
        }

        UserProfile profile = new UserProfile();
        profile.setId(userId);
        profile.setEmail(email);
        profile.setUsername(resolveAvailableUsername(desiredUsername));

        UserProfile saved = userProfileRepository.save(profile);
        log.info("Created profile for userId={} with username={}", userId, saved.getUsername());
        return saved;
    }

    /**
     * Derives a username from an email's local part: lowercased, with every non-alphanumeric
     * character stripped.
     *
     * <p>Usernames are not collected at registration (asking for one would mean a synchronous
     * uniqueness check against this service from auth-service, mid-signup); they are generated
     * here and the user can change theirs later via {@code PUT /users/me}.
     *
     * <p>Note this is only the <em>desired</em> name — uniqueness is settled by
     * {@link #createFromRegistration}, which is the only place that can check the database.
     */
    public static String usernameFromEmail(String email) {
        String localPart = email == null ? "" : email.split("@", 2)[0];
        String cleaned = localPart.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (cleaned.isEmpty()) {
            return FALLBACK_USERNAME;
        }
        return cleaned.length() > MAX_BASE_USERNAME_LENGTH
                ? cleaned.substring(0, MAX_BASE_USERNAME_LENGTH)
                : cleaned;
    }

    private String resolveAvailableUsername(String desired) {
        String base = (desired == null || desired.isBlank())
                ? FALLBACK_USERNAME
                : desired.trim();

        if (!userProfileRepository.existsByUsername(base)) {
            return base;
        }
        for (int suffix = 2; suffix <= MAX_SUFFIX_ATTEMPTS; suffix++) {
            String candidate = base + suffix;
            if (!userProfileRepository.existsByUsername(candidate)) {
                return candidate;
            }
        }
        // Astronomically unlikely; still better than looping forever or throwing during signup.
        return base + UUID.randomUUID().toString().substring(0, 8);
    }
}
