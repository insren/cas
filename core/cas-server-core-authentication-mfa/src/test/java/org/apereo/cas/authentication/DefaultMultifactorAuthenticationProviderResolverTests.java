package org.apereo.cas.authentication;

import org.apereo.cas.authentication.mfa.MultifactorAuthenticationTestUtils;
import org.apereo.cas.authentication.mfa.TestMultifactorAuthenticationProvider;
import org.apereo.cas.authentication.mfa.trigger.AuthenticationAttributeMultifactorAuthenticationTrigger;
import org.apereo.cas.authentication.mfa.trigger.PrincipalAttributeMultifactorAuthenticationTrigger;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.ticket.UnsatisfiedAuthenticationContextTicketValidationException;
import org.apereo.cas.util.CollectionUtils;

import lombok.val;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.binding.expression.support.LiteralExpression;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.webflow.context.servlet.ServletExternalContext;
import org.springframework.webflow.engine.Transition;
import org.springframework.webflow.engine.support.DefaultTargetStateResolver;
import org.springframework.webflow.engine.support.DefaultTransitionCriteria;
import org.springframework.webflow.test.MockRequestContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is {@link DefaultMultifactorAuthenticationProviderResolverTests}.
 *
 * @author Misagh Moayyed
 * @since 6.1.0
 */

@Tag("MFA")
class DefaultMultifactorAuthenticationProviderResolverTests {
    private static void assertProviderResolutionFromManyProviders(final MultifactorAuthenticationTrigger trigger,
                                                                  final ConfigurableApplicationContext applicationContext,
                                                                  final boolean assertPresence) throws Throwable {
        val context = new MockRequestContext();
        val request = new MockHttpServletRequest();
        val response = new MockHttpServletResponse();
        context.setExternalContext(new ServletExternalContext(new MockServletContext(), request, response));

        val provider1 = new TestMultifactorAuthenticationProvider();
        provider1.setOrder(10);
        registerProviderInApplicationContext(applicationContext, context, provider1);

        val provider2 = new TestMultifactorAuthenticationProvider("mfa-other");
        provider2.setOrder(1);
        registerProviderInApplicationContext(applicationContext, context, provider2);

        val principal = CoreAuthenticationTestUtils.getPrincipal("casuser",
            CollectionUtils.wrap("mfa-principal", List.of(provider2.getId())));
        val result = trigger.isActivated(CoreAuthenticationTestUtils.getAuthentication(principal,
                CollectionUtils.wrap("mfa-authn", List.of(provider2.getId()))),
            CoreAuthenticationTestUtils.getRegisteredService(), request,
            new MockHttpServletResponse(),
            CoreAuthenticationTestUtils.getService());
        if (assertPresence) {
            assertTrue(result.isPresent());
            assertEquals(provider2.getId(), result.get().getId());
        } else {
            assertTrue(result.isEmpty());
        }
    }

    private static TestMultifactorAuthenticationProvider registerProviderInApplicationContext(final ConfigurableApplicationContext applicationContext,
                                                                                              final MockRequestContext context,
                                                                                              final TestMultifactorAuthenticationProvider candidateProvider) {
        val provider = TestMultifactorAuthenticationProvider.registerProviderIntoApplicationContext(applicationContext, candidateProvider);
        val targetResolver = new DefaultTargetStateResolver(provider.getId());
        val transition = new Transition(new DefaultTransitionCriteria(new LiteralExpression(provider.getId())), targetResolver);
        context.getRootFlow().getGlobalTransitionSet().add(transition);
        return (TestMultifactorAuthenticationProvider) provider;
    }

    @Test
    void verifyMultipleProvidersWithPrincipalAttributes() throws Throwable {
        val applicationContext = new StaticApplicationContext();
        applicationContext.refresh();

        val casProperties = new CasConfigurationProperties();
        casProperties.getAuthn().getMfa().getTriggers().getPrincipal().setGlobalPrincipalAttributeNameTriggers("mfa-principal");

        val resolver = new DefaultMultifactorAuthenticationProviderResolver(MultifactorAuthenticationPrincipalResolver.identical());
        val trigger = new PrincipalAttributeMultifactorAuthenticationTrigger(casProperties, resolver, applicationContext);
        assertProviderResolutionFromManyProviders(trigger, applicationContext, true);

        assertThrows(UnsatisfiedAuthenticationContextTicketValidationException.class, () -> {
            throw new UnsatisfiedAuthenticationContextTicketValidationException(
                MultifactorAuthenticationTestUtils.getService("id"));
        });
    }

    @Test
    void verifyInvalidPrincipalAttributes() throws Throwable {
        val applicationContext = new StaticApplicationContext();
        applicationContext.refresh();
        val casProperties = new CasConfigurationProperties();
        casProperties.getAuthn().getMfa().getTriggers().getPrincipal().setGlobalPrincipalAttributeNameTriggers("does-not-exist");
        val resolver = new DefaultMultifactorAuthenticationProviderResolver(MultifactorAuthenticationPrincipalResolver.identical());
        val trigger = new PrincipalAttributeMultifactorAuthenticationTrigger(casProperties, resolver, applicationContext);
        assertProviderResolutionFromManyProviders(trigger, applicationContext, false);
    }

    @Test
    void verifyMultipleProvidersWithAuthenticationAttributes() throws Throwable {
        val applicationContext = new StaticApplicationContext();
        applicationContext.refresh();

        val casProperties = new CasConfigurationProperties();
        casProperties.getAuthn().getMfa()
            .getTriggers().getAuthentication().setGlobalAuthenticationAttributeNameTriggers("mfa-authn");

        val resolver = new DefaultMultifactorAuthenticationProviderResolver(MultifactorAuthenticationPrincipalResolver.identical());
        val trigger = new AuthenticationAttributeMultifactorAuthenticationTrigger(casProperties, resolver, applicationContext);
        assertProviderResolutionFromManyProviders(trigger, applicationContext, true);
    }

    @Test
    void verifyResolutionByAuthenticationAttribute() throws Throwable {
        val applicationContext = new StaticApplicationContext();
        applicationContext.refresh();

        val context = new MockRequestContext();
        val request = new MockHttpServletRequest();
        val response = new MockHttpServletResponse();
        context.setExternalContext(new ServletExternalContext(new MockServletContext(), request, response));

        val provider = registerProviderInApplicationContext(applicationContext, context, new TestMultifactorAuthenticationProvider());
        val resolver = new DefaultMultifactorAuthenticationProviderResolver(MultifactorAuthenticationPrincipalResolver.identical());

        val authentication = CoreAuthenticationTestUtils.getAuthentication("casuser",
            CollectionUtils.wrap("authlevel", List.of(provider.getId())));
        val results = resolver.resolveEventViaAuthenticationAttribute(authentication,
            List.of("authlevel"), CoreAuthenticationTestUtils.getRegisteredService(),
            CoreAuthenticationTestUtils.getService(),
            Optional.of(context), List.of(provider), (input, __) -> input.equalsIgnoreCase(provider.getId()));
        assertNotNull(results);
        assertEquals(provider.getId(), results.iterator().next().getId());
    }

    @Test
    void verifyResolutionByPrincipalAttribute() throws Throwable {
        val applicationContext = new StaticApplicationContext();
        applicationContext.refresh();

        val context = new MockRequestContext();
        val request = new MockHttpServletRequest();
        val response = new MockHttpServletResponse();
        context.setExternalContext(new ServletExternalContext(new MockServletContext(), request, response));

        val provider = registerProviderInApplicationContext(applicationContext, context, new TestMultifactorAuthenticationProvider());
        val resolver = new DefaultMultifactorAuthenticationProviderResolver(MultifactorAuthenticationPrincipalResolver.identical());

        val principal = CoreAuthenticationTestUtils.getPrincipal("casuser", CollectionUtils.wrap("authlevel", List.of(provider.getId())));
        var results = resolver.resolveEventViaPrincipalAttribute(principal,
            List.of("authlevel"), CoreAuthenticationTestUtils.getRegisteredService(),
            CoreAuthenticationTestUtils.getService(),
            Optional.of(context), List.of(provider), (input, __) -> input.equalsIgnoreCase(provider.getId()));
        assertNotNull(results);
        assertEquals(provider.getId(), results.iterator().next().getId());

        results = resolver.resolveEventViaPrincipalAttribute(principal,
            List.of("authlevel"), CoreAuthenticationTestUtils.getRegisteredService(),
            CoreAuthenticationTestUtils.getService(),
            Optional.of(context), List.of(), (input, __) -> input.equalsIgnoreCase(provider.getId()));
        assertNull(results);

        results = resolver.resolveEventViaPrincipalAttribute(principal,
            List.of(), CoreAuthenticationTestUtils.getRegisteredService(),
            CoreAuthenticationTestUtils.getService(),
            Optional.of(context), List.of(), (input, __) -> input.equalsIgnoreCase(provider.getId()));
        assertNull(results);
    }

    @Test
    void verifyNoProvider() throws Throwable {
        val context = new MockRequestContext();
        val request = new MockHttpServletRequest();
        val response = new MockHttpServletResponse();
        context.setExternalContext(new ServletExternalContext(new MockServletContext(), request, response));
        val resolver = new DefaultMultifactorAuthenticationProviderResolver(MultifactorAuthenticationPrincipalResolver.identical());
        val principal = CoreAuthenticationTestUtils.getPrincipal("casuser");
        val results = resolver.resolveEventViaAttribute(principal,
            Map.of("authlevel", List.of("strong")),
            List.of(), CoreAuthenticationTestUtils.getRegisteredService(),
            CoreAuthenticationTestUtils.getService(),
            Optional.of(context), List.of(), (__, mfaProvider) -> false);
        assertNull(results);
    }

    @Test
    void verifyNoMatch() throws Throwable {
        val applicationContext = new StaticApplicationContext();
        applicationContext.refresh();

        val context = new MockRequestContext();
        val request = new MockHttpServletRequest();
        val response = new MockHttpServletResponse();
        context.setExternalContext(new ServletExternalContext(new MockServletContext(), request, response));
        val provider = TestMultifactorAuthenticationProvider.registerProviderIntoApplicationContext(applicationContext);
        val resolver = new DefaultMultifactorAuthenticationProviderResolver(MultifactorAuthenticationPrincipalResolver.identical());
        val principal = CoreAuthenticationTestUtils.getPrincipal("casuser");
        val results = resolver.resolveEventViaAttribute(principal,
            Map.of("authlevel", List.of("strong")),
            List.of(), CoreAuthenticationTestUtils.getRegisteredService(),
            CoreAuthenticationTestUtils.getService(),
            Optional.of(context), List.of(provider), (__, mfaProvider) -> false);
        assertNull(results);
    }
}
