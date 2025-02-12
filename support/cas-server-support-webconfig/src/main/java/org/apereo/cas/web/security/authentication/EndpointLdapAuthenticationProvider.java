package org.apereo.cas.web.security.authentication;

import org.apereo.cas.authorization.LdapUserAttributesToRolesAuthorizationGenerator;
import org.apereo.cas.authorization.LdapUserGroupsToRolesAuthorizationGenerator;
import org.apereo.cas.configuration.model.core.monitor.LdapSecurityActuatorEndpointsMonitorProperties;
import org.apereo.cas.util.CollectionUtils;
import org.apereo.cas.util.LdapUtils;
import org.apereo.cas.util.LoggingUtils;
import org.apereo.cas.util.http.HttpRequestUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.ldaptive.ConnectionFactory;
import org.ldaptive.Credential;
import org.ldaptive.ReturnAttributes;
import org.ldaptive.SearchOperation;
import org.ldaptive.auth.AuthenticationRequest;
import org.ldaptive.auth.Authenticator;
import org.pac4j.core.authorization.authorizer.RequireAnyRoleAuthorizer;
import org.pac4j.core.authorization.generator.AuthorizationGenerator;
import org.pac4j.core.authorization.generator.DefaultRolesAuthorizationGenerator;
import org.pac4j.core.context.CallContext;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.jee.context.JEEContext;
import org.pac4j.jee.context.session.JEESessionStore;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * This is {@link EndpointLdapAuthenticationProvider}.
 *
 * @author Misagh Moayyed
 * @since 5.1.0
 */
@Slf4j
@RequiredArgsConstructor
public class EndpointLdapAuthenticationProvider implements AuthenticationProvider {
    private final LdapSecurityActuatorEndpointsMonitorProperties ldapProperties;

    private final SecurityProperties securityProperties;

    private final ConnectionFactory connectionFactory;

    private final Authenticator authenticator;

    private static Authentication generateAuthenticationToken(final Authentication authentication,
                                                              final List<SimpleGrantedAuthority> authorities) {
        val username = authentication.getPrincipal().toString();
        val credentials = authentication.getCredentials();
        return new UsernamePasswordAuthenticationToken(username, credentials, authorities);
    }

    /**
     * Destroy.
     */
    public void destroy() {
        this.connectionFactory.close();
        this.authenticator.close();
    }

    @Override
    public Authentication authenticate(final Authentication authentication) throws AuthenticationException {
        try {
            val username = authentication.getPrincipal().toString();
            val credentials = authentication.getCredentials();
            val password = Optional.ofNullable(credentials).map(Object::toString).orElse(null);
            if (StringUtils.isBlank(password)) {
                throw new IllegalArgumentException("Password cannot be blank");
            }
            LOGGER.debug("Preparing LDAP authentication request for user [{}]", username);
            val request = new AuthenticationRequest(username, new Credential(password), ReturnAttributes.ALL.value());
            LOGGER.debug("Executing LDAP authentication request for user [{}]", username);

            val response = authenticator.authenticate(request);
            LOGGER.debug("LDAP response: [{}]", response);

            if (response.isSuccess()) {
                val roles = securityProperties.getUser()
                    .getRoles()
                    .stream()
                    .map(role -> StringUtils.prependIfMissing(role, ldapProperties.getLdapAuthz().getRolePrefix()))
                    .map(String::toUpperCase)
                    .collect(Collectors.toList());
                LOGGER.debug("Required roles are [{}]", roles);
                if (roles.isEmpty()) {
                    LOGGER.info("No user security roles are defined to enable authorization. User [{}] is considered authorized", username);
                    return generateAuthenticationToken(authentication, new ArrayList<>(0));
                }

                val entry = response.getLdapEntry();
                val profile = new CommonProfile();
                profile.setId(username);
                entry.getAttributes().forEach(attribute -> profile.addAttribute(attribute.getName(), attribute.getStringValues()));

                LOGGER.debug("Collected user profile [{}]", profile);

                val context = new JEEContext(HttpRequestUtils.getHttpServletRequestFromRequestAttributes(),
                    HttpRequestUtils.getHttpServletResponseFromRequestAttributes());
                val authZGen = buildAuthorizationGenerator();

                val callContext = new CallContext(context, JEESessionStore.INSTANCE);
                authZGen.generate(callContext, profile);
                LOGGER.debug("Assembled user profile with roles after generating authorization claims [{}]", profile);

                val authorities = profile.getRoles()
                    .stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toCollection(ArrayList::new));
                LOGGER.debug("List of authorities remapped from profile roles are [{}]", authorities);
                val authorizer = new RequireAnyRoleAuthorizer(roles);
                LOGGER.debug("Executing authorization for expected roles [{}]", authorizer.getElements());

                if (authorizer.isAllAuthorized(context, JEESessionStore.INSTANCE, CollectionUtils.wrap(profile))) {
                    return generateAuthenticationToken(authentication, authorities);
                }
                LOGGER.warn("User [{}] is not authorized to access the requested resource allowed to roles [{}]",
                    username, authorizer.getElements());
            } else {
                LOGGER.warn("LDAP authentication response produced no results for [{}]", username);
            }

        } catch (final Exception e) {
            LoggingUtils.error(LOGGER, e);
            throw new InsufficientAuthenticationException("Unexpected LDAP error", e);
        }
        throw new BadCredentialsException("Could not authenticate provided credentials");
    }

    @Override
    public boolean supports(final Class<?> aClass) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(aClass);
    }

    @SuppressWarnings("java:S2095")
    private AuthorizationGenerator buildAuthorizationGenerator() {
        val ldapAuthz = this.ldapProperties.getLdapAuthz();

        if (isGroupBasedAuthorization()) {
            LOGGER.debug("Handling LDAP authorization based on groups");
            return new LdapUserGroupsToRolesAuthorizationGenerator(
                ldapAuthorizationGeneratorUserSearchOperation(this.connectionFactory),
                ldapAuthz.isAllowMultipleResults(),
                ldapAuthz.getGroupAttribute(),
                ldapAuthz.getGroupPrefix(),
                ldapAuthorizationGeneratorGroupSearchOperation(connectionFactory));
        }
        if (isUserBasedAuthorization()) {
            LOGGER.debug("Handling LDAP authorization based on attributes and roles");
            return new LdapUserAttributesToRolesAuthorizationGenerator(
                ldapAuthorizationGeneratorUserSearchOperation(this.connectionFactory),
                ldapAuthz.isAllowMultipleResults(),
                ldapAuthz.getRoleAttribute(),
                ldapAuthz.getRolePrefix());
        }
        val roles = securityProperties.getUser().getRoles();
        LOGGER.info("Could not determine authorization generator based on users or groups. Authorization will generate static roles based on [{}]", roles);
        return new DefaultRolesAuthorizationGenerator(roles);
    }

    private boolean isGroupBasedAuthorization() {
        val ldapAuthz = this.ldapProperties.getLdapAuthz();
        return StringUtils.isNotBlank(ldapAuthz.getGroupFilter()) && StringUtils.isNotBlank(ldapAuthz.getGroupAttribute());
    }

    private boolean isUserBasedAuthorization() {
        val ldapAuthz = this.ldapProperties.getLdapAuthz();
        return StringUtils.isNotBlank(ldapAuthz.getBaseDn()) && StringUtils.isNotBlank(ldapAuthz.getSearchFilter());
    }

    private SearchOperation ldapAuthorizationGeneratorUserSearchOperation(final ConnectionFactory factory) {
        val ldapAuthz = this.ldapProperties.getLdapAuthz();
        val searchOperation = LdapUtils.newLdaptiveSearchOperation(ldapAuthz.getBaseDn(), ldapAuthz.getSearchFilter(),
            new ArrayList<>(0), CollectionUtils.wrap(ldapAuthz.getRoleAttribute()));
        searchOperation.setConnectionFactory(factory);
        return searchOperation;
    }

    private SearchOperation ldapAuthorizationGeneratorGroupSearchOperation(final ConnectionFactory factory) {
        val ldapAuthz = this.ldapProperties.getLdapAuthz();
        val searchOperation = LdapUtils.newLdaptiveSearchOperation(ldapAuthz.getGroupBaseDn(), ldapAuthz.getGroupFilter(),
            new ArrayList<>(0), CollectionUtils.wrap(ldapAuthz.getGroupAttribute()));
        searchOperation.setConnectionFactory(factory);
        return searchOperation;
    }
}

