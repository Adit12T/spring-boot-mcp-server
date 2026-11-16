package com.example.mcp.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Who is calling: taken from the validated JWT, never from tool arguments. */
public record CallerContext(String subject, String tenantId, String clientId) {

    private static final CallerContext ANONYMOUS = new CallerContext("anonymous", "anonymous", null);

    public static CallerContext current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return ANONYMOUS;
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            String subject = jwt.getSubject() != null ? jwt.getSubject() : authentication.getName();
            String tenant = jwt.getClaimAsString("tenant_id");
            String client = jwt.getClaimAsString("azp") != null
                ? jwt.getClaimAsString("azp")
                : jwt.getClaimAsString("client_id");
            return new CallerContext(subject, tenant != null ? tenant : subject, client);
        }
        return new CallerContext(authentication.getName(), authentication.getName(), null);
    }
}
