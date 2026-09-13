package com.tiendatech.inventario.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class JwtValidationFilterTest {
    private static final String SECRET = "regression-test-secret-at-least-32-characters";
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final JwtValidationFilter filter = new JwtValidationFilter(
            new MockEnvironment().withProperty("AUTH_JWT_SECRET", SECRET)
                    .withProperty("INTERNAL_SERVICE_TOKEN", "internal-test-token"), mapper);

    private String jwt(String algorithm, long expires, String signingKey) throws Exception {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString(mapper.writeValueAsBytes(Map.of("alg", algorithm)));
        String claims = encoder.encodeToString(mapper.writeValueAsBytes(Map.of("exp", expires)));
        String input = header + "." + claims;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return input + "." + encoder.encodeToString(mac.doFinal(input.getBytes(StandardCharsets.US_ASCII)));
    }

    private void assertAuthorization(String authorization, String internal, boolean allowed) throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/private");
        if (authorization != null) request.addHeader("Authorization", authorization);
        if (internal != null) request.addHeader("X-Internal-Token", internal);
        var response = new MockHttpServletResponse();
        boolean[] reached = {false};
        filter.doFilter(request, response, (req, res) -> reached[0] = true);
        assertEquals(allowed, reached[0]);
        assertEquals(allowed ? 200 : 401, response.getStatus());
    }

    @Test void acceptsValidJwt() throws Exception {
        assertAuthorization("Bearer " + jwt("HS256", Instant.now().getEpochSecond() + 300, SECRET), null, true);
    }

    @Test void acceptsOnlyMatchingInternalToken() throws Exception {
        assertAuthorization(null, "internal-test-token", true);
        assertAuthorization(null, "wrong", false);
        assertAuthorization(null, null, false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"expired", "algorithm", "signature", "malformed"})
    void rejectsInvalidTokens(String fault) throws Exception {
        long expiry = fault.equals("expired") ? 1 : Instant.now().getEpochSecond() + 300;
        String algorithm = fault.equals("algorithm") ? "none" : "HS256";
        String key = fault.equals("signature") ? "different-secret" : SECRET;
        String token = fault.equals("malformed") ? "bad.token" : jwt(algorithm, expiry, key);
        assertAuthorization("Bearer " + token, null, false);
    }
}
