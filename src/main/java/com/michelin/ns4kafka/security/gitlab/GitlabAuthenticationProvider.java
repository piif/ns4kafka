package com.michelin.ns4kafka.security.gitlab;

import com.michelin.ns4kafka.config.SecurityConfig;
import com.michelin.ns4kafka.security.ResourceBasedSecurityRule;
import com.michelin.ns4kafka.services.RoleBindingService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.*;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.Map;


@Slf4j
@Singleton
public class GitlabAuthenticationProvider implements AuthenticationProvider<HttpRequest<?>> {
    @Inject
    GitlabAuthenticationService gitlabAuthenticationService;

    @Inject
    ResourceBasedSecurityRule resourceBasedSecurityRule;

    @Inject
    RoleBindingService roleBindingService;

    @Inject
    SecurityConfig securityConfig;

    /**
     * Perform user authentication with GitLab
     * @param httpRequest The HTTP request
     * @param authenticationRequest The authentication request
     * @return An authentication response with the user details
     */
    @Override
    public Publisher<AuthenticationResponse> authenticate(@Nullable HttpRequest<?> httpRequest, AuthenticationRequest<?,?> authenticationRequest) {
        String token = authenticationRequest.getSecret().toString();

        log.debug("Checking authentication with token {}", token);

        return gitlabAuthenticationService.findUsername(token)
                .onErrorResume(error -> Mono.error(new AuthenticationException(new AuthenticationFailed("Bad GitLab token"))))
                .flatMap(username -> gitlabAuthenticationService.findAllGroups(token).collectList()
                        .onErrorResume(error -> Mono.error(new AuthenticationException(new AuthenticationFailed("Cannot retrieve your GitLab groups"))))
                        .flatMap(groups -> {
                            if (roleBindingService.listByGroups(groups).isEmpty() && !groups.contains(securityConfig.getAdminGroup())) {
                                log.debug("Error during authentication: user groups not found in any namespace");
                                return Mono.error(new AuthenticationException(new AuthenticationFailed("No namespace matches your GitLab groups")));
                            } else {
                                return Mono.just(AuthenticationResponse.success(username, resourceBasedSecurityRule.computeRolesFromGroups(groups),
                                Map.of("groups", groups)));
                            }
                        }));
    }

}
