package com.advisorconnect.admin.adapter.out.persistence;

import com.advisorconnect.admin.domain.model.AuditLog;
import com.advisorconnect.admin.domain.model.PlatformCounters;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Parses the atomic counter statements against the real entity model, with no database.
 *
 * <p>A malformed {@code @Query} on a Spring Data repository is not a compile error — it surfaces
 * when the repository bean is created, i.e. at application startup. Every other check available
 * here (unit tests over mocked ports, {@code AdminServiceIT}) either does not touch the query
 * strings or needs Docker, so without this a typo in the SET clause would reach a running
 * environment before anything noticed.
 *
 * <p>Hibernate is bootstrapped metadata-only: the dialect is stated outright and JDBC metadata
 * access is switched off, so no connection is ever opened.
 */
class CounterQueriesParseTest {

    private static StandardServiceRegistry registry;
    private static SessionFactory sessionFactory;

    @BeforeAll
    static void bootHibernateWithoutADatabase() {
        registry = new StandardServiceRegistryBuilder()
                .applySettings(Map.of(
                        "hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect",
                        // Without this Hibernate would reach for a connection to sniff the
                        // database's capabilities at boot.
                        "hibernate.boot.allow_jdbc_metadata_access", "false",
                        "hibernate.temp.use_jdbc_metadata_defaults", "false"))
                .build();

        sessionFactory = new MetadataSources(registry)
                .addAnnotatedClass(PlatformCounters.class)
                .addAnnotatedClass(AuditLog.class)
                .buildMetadata()
                .buildSessionFactory();
    }

    @AfterAll
    static void shutDown() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
        if (registry != null) {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    /**
     * The strings are duplicated from {@link SpringDataPlatformCountersRepository} rather than
     * read off its annotations, so that a change to a query has to be made deliberately in two
     * places instead of a broken one being rubber-stamped here.
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "UPDATE PlatformCounters p SET p.totalUsers = p.totalUsers + :delta WHERE p.id = 1",
            "UPDATE PlatformCounters p SET p.pendingApplications = "
                    + "CASE WHEN p.pendingApplications + :delta < 0 THEN 0 "
                    + "ELSE p.pendingApplications + :delta END WHERE p.id = 1",
            "UPDATE PlatformCounters p SET p.approvedAdvisors = p.approvedAdvisors + :delta WHERE p.id = 1",
            "UPDATE PlatformCounters p SET p.platformRevenueCents = p.platformRevenueCents + :delta "
                    + "WHERE p.id = 1",
    })
    @DisplayName("every counter statement is valid HQL against the entity model")
    void counterStatementsAreValidHql(String hql) {
        var queryEngine = ((SessionFactoryImplementor) sessionFactory).getQueryEngine();

        assertThatCode(() -> queryEngine.getHqlTranslator().translate(hql, null))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "UPDATE PlatformCounters p SET p.noSuchCounter = p.noSuchCounter + :delta WHERE p.id = 1",
    })
    @DisplayName("the parser really would reject a counter that does not exist")
    void theCheckHasTeeth(String hql) {
        var queryEngine = ((SessionFactoryImplementor) sessionFactory).getQueryEngine();

        assertThatCode(() -> queryEngine.getHqlTranslator().translate(hql, null))
                .isInstanceOf(Exception.class);
    }
}
