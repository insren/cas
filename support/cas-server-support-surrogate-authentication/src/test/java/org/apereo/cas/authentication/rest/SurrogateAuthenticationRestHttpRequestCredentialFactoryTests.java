package org.apereo.cas.authentication.rest;

import org.apereo.cas.authentication.SurrogateAuthenticationException;
import org.apereo.cas.authentication.surrogate.SimpleSurrogateAuthenticationService;
import org.apereo.cas.authentication.surrogate.SurrogateCredentialTrait;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.services.ServicesManager;

import lombok.val;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.util.LinkedMultiValueMap;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * This is {@link SurrogateAuthenticationRestHttpRequestCredentialFactoryTests}.
 *
 * @author Misagh Moayyed
 * @since 6.2.0
 */
@SpringBootTest(classes = RefreshAutoConfiguration.class)
@EnableConfigurationProperties(CasConfigurationProperties.class)
@Tag("Impersonation")
class SurrogateAuthenticationRestHttpRequestCredentialFactoryTests {

    @Autowired
    private CasConfigurationProperties casProperties;

    @Test
    void verifyUnAuthz() throws Throwable {
        val request = new MockHttpServletRequest();
        val requestBody = new LinkedMultiValueMap<String, String>();
        request.addHeader(SurrogateAuthenticationRestHttpRequestCredentialFactory.REQUEST_HEADER_SURROGATE_PRINCIPAL, "surrogate");
        requestBody.add("username", "test");
        requestBody.add("password", "password");

        val service = new SimpleSurrogateAuthenticationService(Map.of("test", List.of("other-user")), mock(ServicesManager.class));
        val factory = new SurrogateAuthenticationRestHttpRequestCredentialFactory(service, casProperties.getAuthn().getSurrogate());
        assertThrows(SurrogateAuthenticationException.class, () -> factory.fromRequest(request, requestBody));
    }

    @Test
    void verifyOperationByHeader() throws Throwable {
        val request = new MockHttpServletRequest();
        val requestBody = new LinkedMultiValueMap<String, String>();
        request.addHeader(SurrogateAuthenticationRestHttpRequestCredentialFactory.REQUEST_HEADER_SURROGATE_PRINCIPAL, "surrogate");
        requestBody.add("username", "test");
        requestBody.add("password", "password");

        val service = new SimpleSurrogateAuthenticationService(Map.of("test", List.of("surrogate")), mock(ServicesManager.class));
        val factory = new SurrogateAuthenticationRestHttpRequestCredentialFactory(service, casProperties.getAuthn().getSurrogate());
        assertTrue(factory.getOrder() > 0);
        val results = factory.fromRequest(request, requestBody);
        assertFalse(results.isEmpty());
        val credential = results.get(0);
        assertNotNull(credential);
        assertEquals("surrogate", credential.getCredentialMetadata().getTrait(SurrogateCredentialTrait.class).get().getSurrogateUsername());
        assertEquals("test", credential.getId());
    }

    @Test
    void verifyEmptyCreds() throws Throwable {
        val request = new MockHttpServletRequest();
        val requestBody = new LinkedMultiValueMap<String, String>();
        val service = new SimpleSurrogateAuthenticationService(Map.of("test", List.of("surrogate")), mock(ServicesManager.class));
        val factory = new SurrogateAuthenticationRestHttpRequestCredentialFactory(service, casProperties.getAuthn().getSurrogate());
        assertTrue(factory.fromRequest(request, requestBody).isEmpty());
    }

    @Test
    void verifyOperationByCredentialSeparator() throws Throwable {
        val request = new MockHttpServletRequest();
        val requestBody = new LinkedMultiValueMap<String, String>();
        requestBody.add("username", "surrogate+test");
        requestBody.add("password", "password");

        val service = new SimpleSurrogateAuthenticationService(Map.of("test", List.of("surrogate")), mock(ServicesManager.class));
        val factory = new SurrogateAuthenticationRestHttpRequestCredentialFactory(service, casProperties.getAuthn().getSurrogate());
        val results = factory.fromRequest(request, requestBody);
        assertFalse(results.isEmpty());
        val credential = results.get(0);
        assertNotNull(credential);
        assertEquals("surrogate", credential.getCredentialMetadata().getTrait(SurrogateCredentialTrait.class).get().getSurrogateUsername());
        assertEquals("test", credential.getId());
    }

    @Test
    void verifyBasicUsernamePasswordOperationWithoutSurrogatePrincipal() throws Throwable {
        val request = new MockHttpServletRequest();
        val requestBody = new LinkedMultiValueMap<String, String>();
        requestBody.add("username", "test");
        requestBody.add("password", "password");

        val service = new SimpleSurrogateAuthenticationService(Collections.emptyMap(), mock(ServicesManager.class));
        val factory = new SurrogateAuthenticationRestHttpRequestCredentialFactory(service, casProperties.getAuthn().getSurrogate());
        val results = factory.fromRequest(request, requestBody);
        assertFalse(results.isEmpty());
        val credential = results.get(0);
        assertNotNull(credential);
        assertTrue(credential.getCredentialMetadata().getTrait(SurrogateCredentialTrait.class).isEmpty());
        assertEquals("test", credential.getId());
    }
}
