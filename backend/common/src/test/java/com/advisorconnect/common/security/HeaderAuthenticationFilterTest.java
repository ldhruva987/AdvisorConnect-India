package com.advisorconnect.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeaderAuthenticationFilterTest {

    private static final String USER_ID = "3f0a1d6e-9c2b-4f57-8a11-b5d0e2c47a93";

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private HeaderAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new HeaderAuthenticationFilter();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        // SecurityContextHolder is thread-local and JUnit reuses threads across tests —
        // without this, a populated context would leak into the next test.
        SecurityContextHolder.clearContext();
    }

    private void stubHeaders(String userId, String role) {
        when(request.getHeader(SecurityHeaders.X_USER_ID)).thenReturn(userId);
        when(request.getHeader(SecurityHeaders.X_USER_ROLE)).thenReturn(role);
    }

    private static Authentication currentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    @DisplayName("both headers present and valid -> authenticates with ROLE_-prefixed authority")
    void authenticatesWhenBothHeadersValid() throws Exception {
        stubHeaders(USER_ID, "ADVISOR");

        filter.doFilter(request, response, filterChain);

        Authentication auth = currentAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.isAuthenticated()).isTrue();
        assertThat(auth.getPrincipal()).isEqualTo(USER_ID);
        assertThat(auth.getCredentials()).isNull();
        assertThat(auth.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADVISOR");
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("principal is the raw user-id string, parseable back to the originating UUID")
    void principalRoundTripsToUuid() throws Exception {
        UUID expected = UUID.fromString(USER_ID);
        stubHeaders(USER_ID, "USER");

        filter.doFilter(request, response, filterChain);

        assertThat(UUID.fromString((String) currentAuthentication().getPrincipal())).isEqualTo(expected);
    }

    @Test
    @DisplayName("X-User-Role missing -> context stays empty")
    void doesNotAuthenticateWhenRoleHeaderMissing() throws Exception {
        stubHeaders(USER_ID, null);

        filter.doFilter(request, response, filterChain);

        assertThat(currentAuthentication()).isNull();
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("X-User-Id missing -> context stays empty")
    void doesNotAuthenticateWhenUserIdHeaderMissing() throws Exception {
        when(request.getHeader(SecurityHeaders.X_USER_ID)).thenReturn(null);

        filter.doFilter(request, response, filterChain);

        assertThat(currentAuthentication()).isNull();
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("malformed X-User-Id -> fails closed with no exception")
    void doesNotAuthenticateWhenUserIdIsNotAUuid() throws Exception {
        stubHeaders("not-a-uuid", "ADMIN");

        assertThatCode(() -> filter.doFilter(request, response, filterChain))
                .doesNotThrowAnyException();

        assertThat(currentAuthentication()).isNull();
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("blank X-User-Role -> context stays empty")
    void doesNotAuthenticateWhenRoleIsBlank() throws Exception {
        stubHeaders(USER_ID, "   ");

        filter.doFilter(request, response, filterChain);

        assertThat(currentAuthentication()).isNull();
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("blank X-User-Id -> context stays empty")
    void doesNotAuthenticateWhenUserIdIsBlank() throws Exception {
        when(request.getHeader(SecurityHeaders.X_USER_ID)).thenReturn("  ");

        filter.doFilter(request, response, filterChain);

        assertThat(currentAuthentication()).isNull();
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("the chain is continued even when authentication is rejected")
    void alwaysContinuesTheChain() throws Exception {
        stubHeaders("garbage", "ADMIN");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("header names match exactly what the gateway injects")
    void headerConstantsMatchGatewayContract() {
        assertThat(SecurityHeaders.X_USER_ID).isEqualTo("X-User-Id");
        assertThat(SecurityHeaders.X_USER_ROLE).isEqualTo("X-User-Role");
    }
}
