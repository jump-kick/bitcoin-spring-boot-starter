package org.tbk.spring.lnurl.security.wallet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Slf4j
final class LnurlAuthWalletAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        if (log.isDebugEnabled()) {
            log.debug("Received successful lnurl-auth wallet request of user '{}'", authentication.getPrincipal());
        }
    }
}
