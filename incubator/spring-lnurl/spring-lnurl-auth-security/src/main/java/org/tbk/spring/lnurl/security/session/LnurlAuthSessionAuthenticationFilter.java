package org.tbk.spring.lnurl.security.session;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;
import org.tbk.lnurl.auth.K1;
import org.tbk.lnurl.simple.auth.SimpleK1;
import org.tbk.spring.lnurl.security.LnurlAuthConfigurer;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

@Slf4j
public class LnurlAuthSessionAuthenticationFilter extends AbstractAuthenticationProcessingFilter {
    @FunctionalInterface
    public
    interface SuccessHandlerCustomizer {
        void customize(LnurlAuthSessionAuthenticationSuccessHandler successHandler);
    }

    @Getter
    private final String k1AttributeName;

    @Getter
    private final RequestMatcher sessionLoginUrlRequestMather;

    public LnurlAuthSessionAuthenticationFilter(RequestMatcher requiresAuthenticationRequestMatcher, String k1AttributeName, SuccessHandlerCustomizer successHandlerCustomizer) {
        super(requiresAuthenticationRequestMatcher);

        Assert.hasText(k1AttributeName, "k1AttributeName cannot be empty");
        this.k1AttributeName = k1AttributeName;

        Assert.notNull(requiresAuthenticationRequestMatcher, "requiresAuthenticationRequestMatcher cannot be null");
        this.sessionLoginUrlRequestMather = requiresAuthenticationRequestMatcher;

        Assert.notNull(successHandlerCustomizer, "successHandlerCustomizer cannot be null");
        LnurlAuthSessionAuthenticationSuccessHandler successHandler = new LnurlAuthSessionAuthenticationSuccessHandler();
        successHandlerCustomizer.customize(successHandler);
        this.setAuthenticationSuccessHandler(successHandler);

        // session must only be created by the application itself
        this.setAllowSessionCreation(false);
        // for the session to actually be persisted, a HttpSessionSecurityContextRepository needs to be configured
        this.setSecurityContextRepository(new HttpSessionSecurityContextRepository());
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
        Optional<K1> k1 = obtainK1(request);

        if (log.isDebugEnabled()) {
            log.debug("got lnurl-auth session migration request for k1 '{}'", k1.map(K1::toHex).orElse(null));
        }

        if (k1.isEmpty()) {
            // as 'k1' is deleted once the user is logged in - any attempt to migrate the session again
            // will log out the user (security context is clear if exception is thrown)
            throw new AuthenticationServiceException("'k1' is missing or invalid.");
        }

        LnurlAuthSessionToken lnurlAuthSessionToken = new LnurlAuthSessionToken(k1.get());

        setDetails(request, lnurlAuthSessionToken);

        return this.getAuthenticationManager().authenticate(lnurlAuthSessionToken);
    }

    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
                                            Authentication authResult) throws IOException, ServletException {
        super.successfulAuthentication(request, response, chain, authResult);

        // when authentication is successful and the migration is done,
        // we can delete 'k1' as the pairing is now complete.
        removeK1(request);
    }

    private Optional<K1> obtainK1(HttpServletRequest request) {
        return Optional.of(request)
                .map(it -> it.getSession(false))
                .map(it -> (String) it.getAttribute(k1AttributeName))
                .map(SimpleK1::fromHex);
    }

    private void removeK1(HttpServletRequest request) {
        Optional.of(request)
                .map(it -> it.getSession(false))
                .ifPresent(it -> it.removeAttribute(k1AttributeName));
    }

    protected void setDetails(HttpServletRequest request, LnurlAuthSessionToken authRequest) {
        authRequest.setDetails(this.authenticationDetailsSource.buildDetails(request));
    }
}