package com.advisorconnect.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the two gateway profiles against drifting apart.
 *
 * <p>Every route in this system is declared twice — once in {@code application.yml} for local dev
 * and once in {@code application-docker.yml} for the deployed stack — and nothing previously
 * compared the two. Three separate bugs had accumulated in that gap: the local profile was missing
 * the notification-service route outright, its {@code chat-service-ws} route carried a
 * {@code StripPrefix=1} that rewrote {@code /ws/chat} to {@code /chat} and broke every handshake,
 * and the docker profile silently dropped the rate limiter that local applied to user-service.
 * None of these are visible to a compiler, a unit test, or a single-profile smoke run; they only
 * surface in whichever environment happens not to be the one you are looking at.
 *
 * <h2>Why parse the YAML rather than start the app</h2>
 * The obvious alternative — {@code @SpringBootTest} per profile, asserting on the live
 * {@code RouteLocator} — proves less and costs more. It boots a reactive server and a Redis
 * connection factory per profile (the docker profile points at hosts like {@code redis} and
 * {@code chat-service} that do not resolve on a build machine), and having done so it still
 * inspects the same route definitions this test reads directly. What actually needs asserting is a
 * property <em>of the config files</em>, so the config files are what this test reads.
 *
 * <p>It reads them through {@link YamlPropertySourceLoader} and {@link Binder} into the real
 * {@link RouteDefinition} type, which is the same machinery Spring Cloud Gateway itself uses at
 * startup. So this is not a text diff: a filter written with bad syntax, a malformed URI, or a
 * shorthand that fails to bind fails here too, and the assertions run against parsed
 * {@link FilterDefinition} names rather than substrings.
 *
 * <p>Each file is loaded standalone, which mirrors runtime behaviour: Spring Boot does not merge
 * collections across property sources, so under the {@code docker} profile the route list in
 * {@code application-docker.yml} replaces the base list wholesale rather than appending to it.
 * That is precisely why the two lists have to be kept in lockstep by hand — and why this test
 * exists.
 */
class RouteConfigConsistencyTest {

    private static final String LOCAL_YML = "application.yml";
    private static final String DOCKER_YML = "application-docker.yml";

    /** Every route the gateway is expected to expose, in either profile. */
    private static final Set<String> EXPECTED_ROUTE_IDS = Set.of(
            "auth-service",
            "user-service",
            "advisor-service",
            "booking-service",
            "chat-service-rest",
            "chat-service-ws",
            "admin-service",
            "notification-service");

    private static final String WS_ROUTE_ID = "chat-service-ws";
    private static final String STRIP_PREFIX = "StripPrefix";

    private final Map<String, RouteDefinition> local = routesById(LOCAL_YML);
    private final Map<String, RouteDefinition> docker = routesById(DOCKER_YML);

    // ------------------------------------------------------------------ route inventory

    @Test
    @DisplayName("Both profiles declare exactly the same set of route ids")
    void profilesDeclareTheSameRoutes() {
        assertThat(local.keySet())
                .as("local profile (%s) route ids", LOCAL_YML)
                .containsExactlyInAnyOrderElementsOf(EXPECTED_ROUTE_IDS);
        assertThat(docker.keySet())
                .as("docker profile (%s) route ids", DOCKER_YML)
                .containsExactlyInAnyOrderElementsOf(EXPECTED_ROUTE_IDS);
    }

    @Test
    @DisplayName("Regression: the local profile routes /api/notifications/** (it used to route nowhere)")
    void localProfileRoutesNotifications() {
        RouteDefinition route = localRoute("notification-service");

        assertThat(pathPredicateOf(route)).isEqualTo("/api/notifications/**");
        assertThat(filterNamesOf(route)).containsExactly(STRIP_PREFIX);
    }

    @Test
    @DisplayName("A route's Path predicate is identical in both profiles")
    void pathPredicatesMatchAcrossProfiles() {
        for (String id : EXPECTED_ROUTE_IDS) {
            assertThat(pathPredicateOf(dockerRoute(id)))
                    .as("Path predicate for route '%s' must match between profiles", id)
                    .isEqualTo(pathPredicateOf(localRoute(id)));
        }
    }

    // ------------------------------------------------------------------ websocket prefix

    @Test
    @DisplayName("Regression: neither profile strips the prefix off the /ws/** route")
    void webSocketRouteNeverStripsItsPrefix() {
        // chat-service registers its handler at the literal path /ws/chat, so a StripPrefix=1
        // here forwards /chat instead and the upgrade 404s before a socket is ever opened.
        assertThat(filterNamesOf(localRoute(WS_ROUTE_ID)))
                .as("local %s must not strip the /ws prefix", WS_ROUTE_ID)
                .doesNotContain(STRIP_PREFIX);
        assertThat(filterNamesOf(dockerRoute(WS_ROUTE_ID)))
                .as("docker %s must not strip the /ws prefix", WS_ROUTE_ID)
                .doesNotContain(STRIP_PREFIX);
    }

    @Test
    @DisplayName("Every REST route does strip its /api prefix, in both profiles")
    void restRoutesStripTheirPrefix() {
        for (String id : EXPECTED_ROUTE_IDS) {
            if (WS_ROUTE_ID.equals(id)) {
                continue;
            }
            assertThat(filterNamesOf(localRoute(id)))
                    .as("local route '%s'", id)
                    .contains(STRIP_PREFIX);
            assertThat(filterNamesOf(dockerRoute(id)))
                    .as("docker route '%s'", id)
                    .contains(STRIP_PREFIX);
        }
    }

    // ------------------------------------------------------------------ filter parity

    @Test
    @DisplayName("Regression: user-service is rate limited in the deployed profile too, not just local")
    void userServiceIsRateLimitedInBothProfiles() {
        // Rate limiting used to be local-only, so the one environment actually exposed to the
        // internet was the one environment with no limit.
        assertThat(filterNamesOf(localRoute("user-service"))).contains("RequestRateLimiter");
        assertThat(filterNamesOf(dockerRoute("user-service"))).contains("RequestRateLimiter");

        assertThat(rateLimiterArgsOf(dockerRoute("user-service")))
                .as("docker rate limiter must be configured identically to local")
                .isEqualTo(rateLimiterArgsOf(localRoute("user-service")));
    }

    @Test
    @DisplayName("No route carries a filter in one profile and not the other")
    void filterSetsMatchAcrossProfiles() {
        for (String id : EXPECTED_ROUTE_IDS) {
            assertThat(filterNamesOf(dockerRoute(id)))
                    .as("filters on route '%s' must match between profiles", id)
                    .containsExactlyInAnyOrderElementsOf(filterNamesOf(localRoute(id)));
        }
    }

    // ------------------------------------------------------------------------- helpers

    /**
     * Looks a route up by id, failing with the missing id named rather than with the
     * {@code NullPointerException} a bare map lookup would throw two frames later. A route
     * vanishing from one profile is the single most likely drift this test has to report — it is
     * exactly what happened to notification-service — so it is worth saying so plainly.
     */
    private static RouteDefinition require(
            Map<String, RouteDefinition> routes, String id, String profile) {
        RouteDefinition route = routes.get(id);
        assertThat(route)
                .as("%s profile declares no route '%s' (declared: %s)", profile, id, routes.keySet())
                .isNotNull();
        return route;
    }

    private RouteDefinition localRoute(String id) {
        return require(local, id, "local");
    }

    private RouteDefinition dockerRoute(String id) {
        return require(docker, id, "docker");
    }

    /**
     * Binds {@code spring.cloud.gateway.routes} out of one YAML file using Spring Boot's own
     * loader and binder, so the assertions run against the same parsed types the running gateway
     * would build.
     *
     * <p>The {@link PropertySourcesPlaceholdersResolver} is required, not incidental: the local
     * profile writes its URIs as {@code ${AUTH_SERVICE_URL:http://localhost:8081}}, and a
     * {@code Binder} built without a resolver hands that string straight to the {@code URI}
     * converter, which rejects it ("illegal character in scheme name"). Resolving against only
     * the loaded file means every placeholder falls through to its own default, so a stray
     * environment variable on the build machine cannot change what this test sees.
     */
    private static Map<String, RouteDefinition> routesById(String yamlFile) {
        MutablePropertySources sources = new MutablePropertySources();
        try {
            List<PropertySource<?>> loaded =
                    new YamlPropertySourceLoader().load(yamlFile, new ClassPathResource(yamlFile));
            assertThat(loaded).as("%s must exist and be non-empty", yamlFile).isNotEmpty();
            loaded.forEach(sources::addLast);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + yamlFile, e);
        }

        Binder binder = new Binder(
                ConfigurationPropertySources.from(sources),
                new PropertySourcesPlaceholdersResolver(sources));

        List<RouteDefinition> routes = binder
                .bind("spring.cloud.gateway.routes", Bindable.listOf(RouteDefinition.class))
                .orElseThrow(() -> new AssertionError(
                        yamlFile + " declares no spring.cloud.gateway.routes"));

        Map<String, RouteDefinition> byId = routes.stream()
                .collect(Collectors.toMap(RouteDefinition::getId, Function.identity()));
        assertThat(byId).as("%s must not declare duplicate route ids", yamlFile).hasSameSizeAs(routes);
        return byId;
    }

    /** Filter names only — argument values are compared separately where they matter. */
    private static List<String> filterNamesOf(RouteDefinition route) {
        return route.getFilters().stream().map(FilterDefinition::getName).toList();
    }

    /** The single argument of the route's {@code Path} predicate, e.g. {@code /api/users/**}. */
    private static String pathPredicateOf(RouteDefinition route) {
        PredicateDefinition path = route.getPredicates().stream()
                .filter(p -> "Path".equals(p.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "route '" + route.getId() + "' has no Path predicate"));
        assertThat(path.getArgs()).as("Path predicate of '%s'", route.getId()).hasSize(1);
        return path.getArgs().values().iterator().next();
    }

    private static Map<String, String> rateLimiterArgsOf(RouteDefinition route) {
        return route.getFilters().stream()
                .filter(f -> "RequestRateLimiter".equals(f.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "route '" + route.getId() + "' has no RequestRateLimiter filter"))
                .getArgs();
    }
}
