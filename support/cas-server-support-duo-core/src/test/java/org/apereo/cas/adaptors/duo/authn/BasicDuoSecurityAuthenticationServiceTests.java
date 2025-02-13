package org.apereo.cas.adaptors.duo.authn;

import org.apereo.cas.adaptors.duo.DuoSecurityUserAccountStatus;
import org.apereo.cas.authentication.MultifactorAuthenticationPrincipalResolver;
import org.apereo.cas.config.CasCoreHttpConfiguration;
import org.apereo.cas.config.CasCoreUtilConfiguration;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.configuration.model.support.mfa.duo.DuoSecurityMultifactorAuthenticationProperties;
import org.apereo.cas.services.RegisteredServiceTestUtils;
import org.apereo.cas.util.MockWebServer;
import org.apereo.cas.util.http.HttpClient;
import org.apereo.cas.util.serialization.JacksonObjectMapperFactory;
import com.duosecurity.client.Http;
import com.duosecurity.duoweb.DuoWebException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.val;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import java.io.Serial;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * This is {@link BasicDuoSecurityAuthenticationServiceTests}.
 *
 * @author Misagh Moayyed
 * @since 6.2.0
 */
@SpringBootTest(classes = {
    RefreshAutoConfiguration.class,
    CasCoreHttpConfiguration.class,
    CasCoreUtilConfiguration.class
}, properties = {
    "cas.authn.mfa.duo[0].duo-secret-key=1234567890",
    "cas.authn.mfa.duo[0].duo-application-key=abcdefghijklmnop",
    "cas.authn.mfa.duo[0].duo-integration-key=QRSTUVWXYZ",
    "cas.authn.mfa.duo[0].duo-api-host=httpbin.org/post"
})
@EnableConfigurationProperties(CasConfigurationProperties.class)
@Tag("DuoSecurity")
class BasicDuoSecurityAuthenticationServiceTests {
    private static final ObjectMapper MAPPER = JacksonObjectMapperFactory.builder()
        .defaultTypingEnabled(true).build().toObjectMapper();

    @Autowired
    private CasConfigurationProperties casProperties;

    @Autowired
    @Qualifier(HttpClient.BEAN_NAME_HTTPCLIENT_TRUST_STORE)
    private HttpClient httpClient;

    @Test
    void verifySign() throws Throwable {
        val service = new BasicDuoSecurityAuthenticationService(casProperties.getAuthn().getMfa().getDuo().get(0),
            httpClient, List.of(MultifactorAuthenticationPrincipalResolver.identical()), Caffeine.newBuilder().build());
        assertTrue(service.getDuoClient().isEmpty());
        assertTrue(service.signRequestToken("casuser").isPresent());
    }

    @Test
    void verifyAuthN() throws Throwable {
        val service = new BasicDuoSecurityAuthenticationService(casProperties.getAuthn().getMfa().getDuo().get(0),
            httpClient, List.of(MultifactorAuthenticationPrincipalResolver.identical()), Caffeine.newBuilder().build());
        assertTrue(service.getDuoClient().isEmpty());
        val token = service.signRequestToken("casuser").get();
        val creds = new DuoSecurityCredential("casuser", token + ":casuser", "mfa-duo");
        assertThrows(DuoWebException.class, () -> service.authenticate(creds));
    }

    @Test
    void verifyAuthNNoToken() throws Throwable {
        val service = new BasicDuoSecurityAuthenticationService(casProperties.getAuthn().getMfa().getDuo().get(0),
            httpClient, List.of(MultifactorAuthenticationPrincipalResolver.identical()), Caffeine.newBuilder().build());
        assertTrue(service.getDuoClient().isEmpty());
        val creds = new DuoSecurityCredential("casuser", StringUtils.EMPTY, "mfa-duo");
        assertThrows(IllegalArgumentException.class, () -> service.authenticate(creds));
    }

    @Test
    void verifyAuthNDirect() throws Throwable {
        val service = new BasicDuoSecurityAuthenticationService(casProperties.getAuthn().getMfa().getDuo().get(0),
            httpClient, List.of(MultifactorAuthenticationPrincipalResolver.identical()), Caffeine.newBuilder().build());
        try (val webServer = new MockWebServer()) {
            webServer.start();
            val creds = new DuoSecurityDirectCredential(RegisteredServiceTestUtils.getAuthentication().getPrincipal(), "mfa-duo");
            assertFalse(service.authenticate(creds).isSuccess());
        }
    }

    @Test
    void verifyPasscodeFails() throws Throwable {
        val service = new BasicDuoSecurityAuthenticationService(casProperties.getAuthn().getMfa().getDuo().get(0),
            httpClient, List.of(MultifactorAuthenticationPrincipalResolver.identical()), Caffeine.newBuilder().build());
        val creds = new DuoSecurityPasscodeCredential("casuser", "046573", "mfa-duo");
        assertFalse(service.authenticate(creds).isSuccess());
    }

    @Test
    void verifyPasscode() throws Throwable {
        try (val webServer = new MockWebServer()) {
            webServer.start();

            val props = new DuoSecurityMultifactorAuthenticationProperties();
            BeanUtils.copyProperties(props, casProperties.getAuthn().getMfa().getDuo().get(0));
            props.setDuoApiHost("localhost:%s".formatted(webServer.getPort()));
            val service = new BasicDuoSecurityAuthenticationService(props,
                httpClient, List.of(MultifactorAuthenticationPrincipalResolver.identical()), Caffeine.newBuilder().build()) {
                @Serial
                private static final long serialVersionUID = 1756840642345094968L;

                @Override
                protected JSONObject executeDuoApiRequest(final Http request) {
                    return new JSONObject(Map.of("stat", "OK", "result", "allow"));
                }
            };

            val creds = new DuoSecurityPasscodeCredential("casuser", "123456", "mfa-duo");
            assertTrue(service.authenticate(creds).isSuccess());
        }
    }

    @Test
    void verifyAccountStatusDisabled() throws Throwable {
        val props = new DuoSecurityMultifactorAuthenticationProperties();
        BeanUtils.copyProperties(props, casProperties.getAuthn().getMfa().getDuo().get(0));
        props.setAccountStatusEnabled(false);
        val service = new BasicDuoSecurityAuthenticationService(props, httpClient,
            List.of(MultifactorAuthenticationPrincipalResolver.identical()),
            Caffeine.newBuilder().build());
        assertEquals(DuoSecurityUserAccountStatus.AUTH, service.getUserAccount("casuser").getStatus());
    }


    @Test
    void verifyGetAccountNoStat() throws Throwable {
        val props = casProperties.getAuthn().getMfa().getDuo().get(0);
        val service = new BasicDuoSecurityAuthenticationService(props, httpClient,
            List.of(MultifactorAuthenticationPrincipalResolver.identical()), Caffeine.newBuilder().build()) {
            @Serial
            private static final long serialVersionUID = 6245462449489284549L;

            @Override
            protected String getHttpResponse(final Http userRequest) throws Exception {
                return MAPPER.writeValueAsString(Map.of("response", "pong"));
            }
        };
        assertEquals(DuoSecurityUserAccountStatus.UNAVAILABLE, service.getUserAccount("casuser").getStatus());
        assertEquals(DuoSecurityUserAccountStatus.UNAVAILABLE, service.getUserAccount("casuser").getStatus());
    }

    @Test
    void verifyGetAccountEnroll() throws Throwable {
        val props = casProperties.getAuthn().getMfa().getDuo().get(0);
        val service = new BasicDuoSecurityAuthenticationService(props, httpClient,
            List.of(MultifactorAuthenticationPrincipalResolver.identical()),
            Caffeine.newBuilder().build()) {
            @Serial
            private static final long serialVersionUID = 6245462449489284549L;

            @Override
            protected String getHttpResponse(final Http userRequest) throws Exception {
                val response = Map.of("status_msg", "OK", "result", "ENROLL", "enroll_portal_url", "google.com");
                return MAPPER.writeValueAsString(Map.of(
                    "stat", "OK",
                    "response", response));
            }
        };
        assertEquals(DuoSecurityUserAccountStatus.ENROLL, service.getUserAccount("casuser").getStatus());
    }

    @Test
    void verifyGetAccountFail() throws Throwable {
        val props = casProperties.getAuthn().getMfa().getDuo().get(0);
        val service = new BasicDuoSecurityAuthenticationService(props, httpClient,
            List.of(MultifactorAuthenticationPrincipalResolver.identical()),
            Caffeine.newBuilder().build()) {
            @Serial
            private static final long serialVersionUID = 6245462449489284549L;

            @Override
            protected String getHttpResponse(final Http userRequest) throws Exception {
                return MAPPER.writeValueAsString(Map.of(
                    "stat", "FAIL",
                    "code", "100000"));
            }
        };
        assertEquals(DuoSecurityUserAccountStatus.UNAVAILABLE, service.getUserAccount("casuser").getStatus());
    }

    @Test
    void verifyGetAccountAuth() throws Throwable {
        val props = casProperties.getAuthn().getMfa().getDuo().get(0);
        val service = new BasicDuoSecurityAuthenticationService(props, httpClient,
            List.of(MultifactorAuthenticationPrincipalResolver.identical()),
            Caffeine.newBuilder().build()) {
            @Serial
            private static final long serialVersionUID = 6245462449489284549L;

            @Override
            protected String getHttpResponse(final Http userRequest) throws Exception {
                return MAPPER.writeValueAsString(Map.of(
                    "stat", "FAIL",
                    "code", "1000"));
            }
        };
        assertEquals(DuoSecurityUserAccountStatus.AUTH, service.getUserAccount("casuser").getStatus());
    }

    @Test
    void verifyPing() throws Throwable {
        var entity = MAPPER.writeValueAsString(Map.of("stat", "OK", "response", "pong"));
        try (val webServer = new MockWebServer(entity)) {
            webServer.start();
            val props = new DuoSecurityMultifactorAuthenticationProperties().setDuoApiHost("http://localhost:%s".formatted(webServer.getPort()));
            val service = new BasicDuoSecurityAuthenticationService(props, httpClient,
                List.of(MultifactorAuthenticationPrincipalResolver.identical()),
                Caffeine.newBuilder().build());
            assertTrue(service.ping());
        }
    }
}
