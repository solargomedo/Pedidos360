package cl.duoc.pedidos360.orders.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
@Configuration
public class MessagingSecurityConfig {
    @Bean
    @Order(1)
    SecurityFilterChain messagingHistorySecurity(HttpSecurity http) throws Exception {
        var roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return http.securityMatcher("/api/orders/messaging", "/api/orders/messaging/**")
                // Applies only to this bearer-only API; existing Orders routes bypass this chain.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.GET, "/api/orders/messaging/events").hasRole("Admin")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(r -> r.jwt(j -> j.jwtAuthenticationConverter(converter)))
                .build();
    }
    // Intentionally no fallback chain: preserve pre-existing internal Orders behavior.
}
