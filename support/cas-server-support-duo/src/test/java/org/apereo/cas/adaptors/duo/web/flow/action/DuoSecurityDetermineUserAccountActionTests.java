package org.apereo.cas.adaptors.duo.web.flow.action;

import org.apereo.cas.BaseCasWebflowMultifactorAuthenticationTests;
import org.apereo.cas.adaptors.duo.BaseDuoSecurityTests;
import org.apereo.cas.adaptors.duo.DuoSecurityUserAccount;
import org.apereo.cas.adaptors.duo.DuoSecurityUserAccountStatus;
import org.apereo.cas.adaptors.duo.authn.DuoSecurityAuthenticationService;
import org.apereo.cas.adaptors.duo.authn.DuoSecurityMultifactorAuthenticationProvider;
import org.apereo.cas.authentication.CoreAuthenticationTestUtils;
import org.apereo.cas.authentication.MultifactorAuthenticationProvider;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.configuration.model.support.mfa.duo.DuoSecurityMultifactorAuthenticationRegistrationProperties;
import org.apereo.cas.services.RegisteredServiceTestUtils;
import org.apereo.cas.util.MockRequestContext;
import org.apereo.cas.web.flow.CasWebflowConstants;
import org.apereo.cas.web.support.WebUtils;
import lombok.val;
import org.apache.hc.core5.net.URIBuilder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.webflow.execution.Action;
import org.springframework.webflow.execution.RequestContext;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * This is {@link DuoSecurityDetermineUserAccountActionTests}.
 *
 * @author Misagh Moayyed
 * @since 6.2.0
 */
@SpringBootTest(classes = {
    DuoSecurityDetermineUserAccountActionTests.MultifactorTestConfiguration.class,
    BaseDuoSecurityTests.SharedTestConfiguration.class
}, properties = {
    "cas.authn.mfa.duo[0].id=mfa-default",
    "cas.authn.mfa.duo[0].duo-secret-key=1234567890",
    "cas.authn.mfa.duo[0].duo-application-key=abcdefghijklmnop",
    "cas.authn.mfa.duo[0].duo-integration-key=QRSTUVWXYZ",
    "cas.authn.mfa.duo[0].duo-api-host=theapi.duosecurity.com"
})
@EnableConfigurationProperties(CasConfigurationProperties.class)
@Tag("DuoSecurity")
class DuoSecurityDetermineUserAccountActionTests extends BaseCasWebflowMultifactorAuthenticationTests {

    @Autowired
    @Qualifier("determineDuoUserAccountAction")
    private Action determineDuoUserAccountAction;

    @Autowired
    @Qualifier("allowProvider")
    private MultifactorAuthenticationProvider allowProvider;

    @Autowired
    @Qualifier("denyProvider")
    private MultifactorAuthenticationProvider denyProvider;

    @Autowired
    @Qualifier("authProvider")
    private MultifactorAuthenticationProvider authProvider;

    @Autowired
    @Qualifier("unavailableProvider")
    private MultifactorAuthenticationProvider unavailableProvider;

    @Autowired
    @Qualifier("enrollProvider")
    private MultifactorAuthenticationProvider enrollProvider;

    private RequestContext verifyOperation(final MultifactorAuthenticationProvider provider, final String eventId) throws Exception {
        val context = MockRequestContext.create(applicationContext);

        WebUtils.putServiceIntoFlowScope(context, CoreAuthenticationTestUtils.getWebApplicationService());
        val authentication = CoreAuthenticationTestUtils.getAuthentication();
        WebUtils.putAuthentication(authentication, context);

        servicesManager.save(RegisteredServiceTestUtils.getRegisteredService("registration.duo.com"));
        WebUtils.putMultifactorAuthenticationProvider(context, provider);

        val event = determineDuoUserAccountAction.execute(context);
        assertEquals(eventId, event.getId());
        return context;
    }

    @Test
    void verifyOperationEnroll() throws Throwable {
        val context = verifyOperation(enrollProvider, CasWebflowConstants.TRANSITION_ID_ENROLL);
        val url = context.getFlowScope().get("duoRegistrationUrl", String.class);
        assertNotNull(url);
        assertTrue("principal".equalsIgnoreCase(new URIBuilder(url).getQueryParams().get(0).getName()));
    }

    @Test
    void verifyOperationAllow() throws Throwable {
        verifyOperation(allowProvider, CasWebflowConstants.TRANSITION_ID_BYPASS);
    }

    @Test
    void verifyOperationDeny() throws Throwable {
        verifyOperation(denyProvider, CasWebflowConstants.TRANSITION_ID_DENY);
    }

    @Test
    void verifyOperationUnavailable() throws Throwable {
        verifyOperation(unavailableProvider, CasWebflowConstants.TRANSITION_ID_UNAVAILABLE);
    }

    @Test
    void verifyOperationAuth() throws Throwable {
        verifyOperation(authProvider, CasWebflowConstants.TRANSITION_ID_SUCCESS);
    }

    @TestConfiguration(value = "MultifactorTestConfiguration", proxyBeanMethods = false)
    static class MultifactorTestConfiguration {
        @Bean
        public MultifactorAuthenticationProvider allowProvider() {
            return buildProvider(DuoSecurityUserAccountStatus.ALLOW);
        }

        @Bean
        public MultifactorAuthenticationProvider denyProvider() {
            return buildProvider(DuoSecurityUserAccountStatus.DENY);
        }

        @Bean
        public MultifactorAuthenticationProvider unavailableProvider() {
            return buildProvider(DuoSecurityUserAccountStatus.UNAVAILABLE);
        }

        @Bean
        public MultifactorAuthenticationProvider authProvider() {
            return buildProvider(DuoSecurityUserAccountStatus.AUTH);
        }

        @Bean
        public MultifactorAuthenticationProvider enrollProvider() {
            return buildProvider(DuoSecurityUserAccountStatus.ENROLL);
        }

        private static DuoSecurityMultifactorAuthenticationProvider buildProvider(
            final DuoSecurityUserAccountStatus status) {
            val id = "mfa-%s".formatted(UUID.randomUUID().toString());

            val authentication = CoreAuthenticationTestUtils.getAuthentication();
            val provider = mock(DuoSecurityMultifactorAuthenticationProvider.class);
            val account = new DuoSecurityUserAccount(authentication.getPrincipal().getId());
            account.setStatus(status);
            account.setEnrollPortalUrl("https://example.org");
            val registration = new DuoSecurityMultifactorAuthenticationRegistrationProperties()
                .setRegistrationUrl("https://registration.duo.com");
            registration.getCrypto().setEnabled(true);
            when(provider.getRegistration()).thenReturn(registration);

            when(provider.matches(anyString())).thenAnswer(answer -> answer.getArgument(0, String.class).equalsIgnoreCase(id));
            val duoService = mock(DuoSecurityAuthenticationService.class);
            when(duoService.getUserAccount(anyString())).thenReturn(account);
            when(provider.getId()).thenReturn(id);
            when(provider.getDuoAuthenticationService()).thenReturn(duoService);
            return provider;
        }
    }
}
