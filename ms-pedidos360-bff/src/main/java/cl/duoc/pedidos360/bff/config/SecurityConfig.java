package cl.duoc.pedidos360.bff.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.DelegatingJwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/bff/orders")
                        .hasAnyRole("Cliente", "Operador", "Admin")
                        .requestMatchers(HttpMethod.GET, "/api/bff/orders/**")
                        .hasAnyRole("Admin", "Operador")
                        .requestMatchers(HttpMethod.PUT, "/api/bff/orders/**")
                        .hasAnyRole("Admin", "Operador")
                        .requestMatchers(HttpMethod.PATCH, "/api/bff/orders/**")
                        .hasAnyRole("Admin", "Operador")
                        .requestMatchers(HttpMethod.DELETE, "/api/bff/orders/**")
                        .hasAnyRole("Admin", "Operador")
                        .requestMatchers(HttpMethod.GET, "/api/bff/catalog/**")
                        .hasAnyRole("Admin", "Operador", "Cliente")
                        .requestMatchers(HttpMethod.POST, "/api/bff/catalog")
                        .hasRole("Admin")
                        .requestMatchers(HttpMethod.PUT, "/api/bff/catalog/**")
                        .hasRole("Admin")
                        .requestMatchers(HttpMethod.DELETE, "/api/bff/catalog/**")
                        .hasRole("Admin")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:4200"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        // El filtro CORS resuelve el preflight antes de la autenticacion JWT.
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        scopes.setAuthoritiesClaimName("scp");
        scopes.setAuthorityPrefix("SCOPE_");

        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(
                new DelegatingJwtGrantedAuthoritiesConverter(scopes, roles));
        return converter;
    }
}
