package com.advisorconnect.admin.adapter.out.persistence;

import com.advisorconnect.admin.domain.model.PlatformCounters;
import com.advisorconnect.admin.domain.port.out.PlatformCountersRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.IntSupplier;

/**
 * Applies counter deltas as atomic UPDATEs, seeding the singleton row on first use.
 *
 * <p>Seeding is lazy rather than an {@code ApplicationRunner}: Kafka listener containers are
 * started during context refresh, i.e. <em>before</em> runners execute, so a runner would leave a
 * window in which the first few events find no row and are silently dropped. Instead an UPDATE
 * that reports zero affected rows is taken as "the row does not exist yet" — the row is inserted
 * and the UPDATE retried once.
 */
@Repository
@Slf4j
public class JpaPlatformCountersRepository implements PlatformCountersRepository {

    private final SpringDataPlatformCountersRepository repo;

    /**
     * The insert runs in its own transaction so that losing the insert race does not poison the
     * caller's transaction: a {@code DataIntegrityViolationException} marks its transaction
     * rollback-only, and the caller still has an increment to apply.
     */
    private final TransactionTemplate newTransaction;

    public JpaPlatformCountersRepository(SpringDataPlatformCountersRepository repo,
                                         PlatformTransactionManager transactionManager) {
        this.repo = repo;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public PlatformCounters load() {
        return repo.findById(PlatformCounters.SINGLETON_ID).orElseGet(PlatformCounters::new);
    }

    @Override
    public void addTotalUsers(long delta) {
        applyWithSeeding(() -> repo.addTotalUsers(delta));
    }

    @Override
    public void addPendingApplications(long delta) {
        applyWithSeeding(() -> repo.addPendingApplications(delta));
    }

    @Override
    public void addApprovedAdvisors(long delta) {
        applyWithSeeding(() -> repo.addApprovedAdvisors(delta));
    }

    @Override
    public void addPlatformRevenueCents(long deltaCents) {
        applyWithSeeding(() -> repo.addPlatformRevenueCents(deltaCents));
    }

    private void applyWithSeeding(IntSupplier update) {
        if (update.getAsInt() > 0) {
            return;
        }
        seedSingletonRow();
        if (update.getAsInt() == 0) {
            // Only reachable if the row vanished between the insert and the retry.
            log.warn("Counter update affected no rows even after seeding the platform_counters row");
        }
    }

    private void seedSingletonRow() {
        try {
            newTransaction.executeWithoutResult(status -> {
                if (!repo.existsById(PlatformCounters.SINGLETON_ID)) {
                    repo.saveAndFlush(new PlatformCounters());
                }
            });
        } catch (DataIntegrityViolationException race) {
            // Another listener thread inserted it first. Its row is as good as ours.
            log.debug("platform_counters singleton row was created concurrently");
        }
    }
}
