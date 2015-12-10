package org.jasig.cas.authentication;

/**
 * The {@link AuthenticationTransactionManager} deals exclusively with authentication concepts
 * e.g. Credentials, Principals, producing valid Authentication objects. It is invoked repeatedly with distinct credential type(s)
 * for interactive multi-staged authn flows that would authenticate at each step as opposed
 * to gather all credentials and send them for authentication in one batch.
 *
 * @author Misagh Moayyed
 * @since 4.2
 */
public interface AuthenticationTransactionManager {
    /**
     * Authenticate boolean.
     *
     * @param credentials the credentials
     * @return the context builder
     * @throws AuthenticationException the authentication exception
     */
    AuthenticationContextBuilder processAuthenticationAttempt(final Credential... credentials) throws AuthenticationException;

    /**
     * Build authentication context authentication context.
     *
     * @return the authentication context
     */
    AuthenticationContext build();

    /**
     * Clear.
     */
    void clear();
}
