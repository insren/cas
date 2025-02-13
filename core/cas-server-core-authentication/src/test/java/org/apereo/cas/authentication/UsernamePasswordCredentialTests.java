package org.apereo.cas.authentication;

import org.apereo.cas.authentication.credential.UsernamePasswordCredential;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.util.spring.ApplicationContextProvider;

import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.binding.mapping.MappingResults;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.webflow.context.servlet.ServletExternalContext;
import org.springframework.webflow.test.MockRequestContext;
import org.springframework.webflow.validation.DefaultValidationContext;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Scott Battaglia
 * @since 3.0.0
 */
@SpringBootTest(classes = RefreshAutoConfiguration.class,
    properties = "cas.authn.policy.source-selection-enabled=true")
@EnableConfigurationProperties(CasConfigurationProperties.class)
@Tag("Authentication")
class UsernamePasswordCredentialTests {

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    @Test
    void verifyOperation() throws Throwable {
        ApplicationContextProvider.holdApplicationContext(applicationContext);
        val input = new UsernamePasswordCredential("casuser", "Mellon".toCharArray(), StringUtils.EMPTY, Map.of());
        assertTrue(input.isValid());
        assertSame(UsernamePasswordCredential.class, input.getClass());

        val context = new MockRequestContext();
        val request = new MockHttpServletRequest();
        val response = new MockHttpServletResponse();
        context.setExternalContext(new ServletExternalContext(new MockServletContext(), request, response));

        val validationContext = new DefaultValidationContext(context, "submit", mock(MappingResults.class));
        input.validate(validationContext);
        assertTrue(context.getMessageContext().hasErrorMessages());

    }

    @Test
    void verifyInvalidEvent() throws Throwable {
        ApplicationContextProvider.holdApplicationContext(applicationContext);
        val input = new UsernamePasswordCredential(null, "Mellon".toCharArray(), StringUtils.EMPTY, Map.of());

        val context = new MockRequestContext();
        val request = new MockHttpServletRequest();
        val response = new MockHttpServletResponse();
        context.setExternalContext(new ServletExternalContext(new MockServletContext(), request, response));

        val validationContext = new DefaultValidationContext(context, "whatever", mock(MappingResults.class));
        input.validate(validationContext);
        assertFalse(context.getMessageContext().hasErrorMessages());
    }

    @Test
    void verifySetGetUsername() throws Throwable {
        val credential = new UsernamePasswordCredential();
        val userName = "test";
        credential.setUsername(userName);
        assertEquals(userName, credential.getUsername());
    }

    @Test
    void verifySetGetPassword() throws Throwable {
        val credential = new UsernamePasswordCredential();
        val password = "test";

        credential.assignPassword(password);
        assertEquals(password, credential.toPassword());
    }

    @Test
    void verifyEquals() throws Throwable {
        val c1 = CoreAuthenticationTestUtils.getCredentialsWithDifferentUsernameAndPassword();
        assertNotEquals(null, c1);
        val c2 = CoreAuthenticationTestUtils.getCredentialsWithSameUsernameAndPassword("casuser");
        val c3 = CoreAuthenticationTestUtils.getCredentialsWithDifferentUsernameAndPassword("casuser", UUID.randomUUID().toString());
        assertEquals(c3, c2);
    }
}
