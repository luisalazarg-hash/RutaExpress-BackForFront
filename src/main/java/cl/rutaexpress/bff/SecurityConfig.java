package cl.rutaexpress.bff;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.DelegatingJwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;

import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public ReactiveJwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.audiences[0]}") String audience) {

        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withIssuerLocation(issuerUri).build();

        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuerUri);

        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> {
            if (jwt.getAudience().contains(audience)) {
                return OAuth2TokenValidatorResult.success();
            }

            OAuth2Error error = new OAuth2Error(
                    "invalid_token",
                    "El token no contiene la audiencia requerida",
                    null);

            return OAuth2TokenValidatorResult.failure(error);
        };

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        issuerValidator,
                        audienceValidator));

        return decoder;
    }

    @Bean
    public Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {

        // Convierte:
        // scp: "access_as_user"
        // en:
        // SCOPE_access_as_user
        JwtGrantedAuthoritiesConverter scopesConverter = new JwtGrantedAuthoritiesConverter();

        // Convierte:
        // roles: ["ADMIN"]
        // en:
        // ROLE_ADMIN
        JwtGrantedAuthoritiesConverter rolesConverter = new JwtGrantedAuthoritiesConverter();

        rolesConverter.setAuthoritiesClaimName("roles");
        rolesConverter.setAuthorityPrefix("ROLE_");

        DelegatingJwtGrantedAuthoritiesConverter authoritiesConverter = new DelegatingJwtGrantedAuthoritiesConverter(
                scopesConverter,
                rolesConverter);

        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();

        jwtConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);

        return new ReactiveJwtAuthenticationConverterAdapter(jwtConverter);
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter) {

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)

                .exceptionHandling(exception -> exception

                    .authenticationEntryPoint((exchange, error) -> {
                        exchange.getResponse().setStatusCode(
                                org.springframework.http.HttpStatus.UNAUTHORIZED);

                        return exchange.getResponse().setComplete();
                    })

                    .accessDeniedHandler((exchange, error) -> {
                        exchange.getResponse().setStatusCode(
                                org.springframework.http.HttpStatus.FORBIDDEN);

                        return exchange.getResponse().setComplete();
                    }))

                .authorizeExchange(exchange -> exchange

                        // CORS preflight
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Health público
                        .pathMatchers(
                                "/actuator/health",
                                "/actuator/health/**")
                        .permitAll()

                        // Cualquier usuario con el scope de la API
                        .pathMatchers("/api/auth/**")
                        .hasAuthority("SCOPE_access_as_user")

                        // Solo ADMIN
                        .pathMatchers("/api/usuarios/**")
                        .hasRole("ADMIN")

                        // ADMIN o AUDITOR
                        .pathMatchers("/api/audit/**")
                        .hasAnyRole("ADMIN", "AUDITOR")

                        // ADMIN o DESPACHADOR
                        .pathMatchers("/api/catalog/**")
                        .hasAnyRole("ADMIN", "DESPACHADOR")

                        // ADMIN, DESPACHADOR o CLIENTE
                        .pathMatchers("/api/shipments/**")
                        .hasAnyRole("ADMIN", "DESPACHADOR", "CLIENTE")

                        .pathMatchers("/api/notifications/**")
                        .hasAnyRole("ADMIN", "DESPACHADOR", "CLIENTE")

                        // Solo ADMIN
                        .pathMatchers("/api/reports/**")
                        .hasRole("ADMIN")

                        .pathMatchers(
                                "/api/rabbitmq/**",
                                "/api/kafka/**")
                        .hasRole("ADMIN")

                        // Cualquier otra petición necesita JWT válido
                        .anyExchange().authenticated())

                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(
                        jwtAuthenticationConverter)))

                .build();
    }
}