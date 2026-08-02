package com.advisorconnect.auth.infrastructure.config;

import com.advisorconnect.auth.domain.model.User;
import com.advisorconnect.auth.domain.model.UserRole;
import com.advisorconnect.auth.domain.port.out.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Bootstraps the very first {@code ADMIN} account at startup.
 *
 * <p>Registration is locked to {@code role=USER}, and {@code POST /auth/admin/users} requires an
 * existing administrator — so without this there would be no way to obtain the first one. That
 * chicken-and-egg is resolved here, out of band, from the deployment environment rather than
 * over the network.
 *
 * <p>Three properties of this runner matter:
 * <ul>
 *   <li><strong>Opt-in.</strong> Both {@code admin.seed.email} and {@code admin.seed.password}
 *       default to blank. A deployment that sets neither gets no seeding and no surprise
 *       account — the safe default for every environment except a first boot.</li>
 *   <li><strong>Idempotent.</strong> It is a no-op whenever any administrator already exists, so
 *       leaving the variables set across restarts neither fails nor re-creates anything. Crucially
 *       it also will not resurrect an admin that was deliberately deleted or demoted... only
 *       because that check is "any admin at all", not "this specific email" — see below.</li>
 *   <li><strong>Non-destructive.</strong> It never rewrites an existing row. If the configured
 *       email is already taken (by a plain user, say) the seeder declines rather than silently
 *       promoting that account to administrator.</li>
 * </ul>
 *
 * <p>The password is read from configuration and never logged.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminSeeder implements ApplicationRunner {

    /** Mirrors the {@code @Size} floor on the registration DTOs; a seeded admin is held to the same bar. */
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.seed.email:}")
    private String seedEmail;

    @Value("${admin.seed.password:}")
    private String seedPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (isBlank(seedEmail) || isBlank(seedPassword)) {
            log.debug("Admin seeding disabled (admin.seed.email / admin.seed.password not set)");
            return;
        }

        String email = seedEmail.trim();

        if (seedPassword.length() < MIN_PASSWORD_LENGTH) {
            // Refusing beats seeding a weak administrator: this account is the root of every
            // other privileged account on the platform.
            log.error("Admin seeding skipped: admin.seed.password is shorter than {} characters",
                    MIN_PASSWORD_LENGTH);
            return;
        }

        if (userRepository.existsByRole(UserRole.ADMIN)) {
            log.info("Admin seeding skipped: an administrator already exists");
            return;
        }

        if (userRepository.existsByEmail(email)) {
            log.warn("Admin seeding skipped: {} is already registered as a non-admin account. "
                    + "Promote it deliberately rather than through the seeder.", email);
            return;
        }

        User admin = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(seedPassword))
                .role(UserRole.ADMIN)
                .build();
        admin = userRepository.save(admin);

        log.info("Seeded initial ADMIN account {} (id={}). Unset ADMIN_SEED_EMAIL / "
                + "ADMIN_SEED_PASSWORD and rotate this password now.", email, admin.getId());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
