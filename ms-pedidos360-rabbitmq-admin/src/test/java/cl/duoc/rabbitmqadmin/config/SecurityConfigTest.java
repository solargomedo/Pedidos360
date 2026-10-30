package cl.duoc.rabbitmqadmin.config;

import cl.duoc.rabbitmqadmin.controller.RabbitmqAdminController;
import cl.duoc.rabbitmqadmin.service.RabbitmqAdminService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = RabbitmqAdminController.class,
        properties = {
                "ENTRA_JWT_ISSUER_URI=https://issuer.example.test/tenant/v2.0",
                "ENTRA_JWT_AUDIENCE=api://rabbitmq-admin-test"
        })
@Import({SecurityConfig.class, SecurityConfigTest.TestSecurityInfrastructure.class})
class SecurityConfigTest {

    private static final String PATH = "/api/admin/rabbitmq/queues";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RabbitmqAdminService rabbitmqAdminService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    // Enable the real security infrastructure in this MVC-only test slice.
    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class TestSecurityInfrastructure {
    }

    @Test
    void withoutJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(options(PATH))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(jwtDecoder, rabbitmqAdminService);
    }

    @Test
    void operatorJwtReturnsForbidden() throws Exception {
        when(jwtDecoder.decode("operator-test-marker"))
                .thenReturn(jwtWithRole("operator-test-marker", "Operador"));

        mockMvc.perform(options(PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer operator-test-marker"))
                .andExpect(status().isForbidden());

        verify(jwtDecoder).decode("operator-test-marker");
        verifyNoInteractions(rabbitmqAdminService);
    }

    @Test
    void adminJwtAllowsOptionsWithoutAdministrativeOperations() throws Exception {
        when(jwtDecoder.decode("admin-test-marker"))
                .thenReturn(jwtWithRole("admin-test-marker", "Admin"));

        mockMvc.perform(options(PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-test-marker"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("POST")));

        verify(jwtDecoder).decode("admin-test-marker");
        verifyNoInteractions(rabbitmqAdminService);
    }

    private Jwt jwtWithRole(String marker, String role) {
        Instant now = Instant.now();
        return Jwt.withTokenValue(marker)
                .header("alg", "RS256")
                .subject("test-subject")
                .issuedAt(now.minusSeconds(60))
                .expiresAt(now.plusSeconds(300))
                .claim("roles", List.of(role))
                .build();
    }
}
