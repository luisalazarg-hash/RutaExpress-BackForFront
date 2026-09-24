package cl.rutaexpress.bff;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

@Configuration
public class CorsHeadersFilter {
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    WebFilter corsHeaders(
            @Value("${app.cors.allowed-origins:http://localhost:5173}") String allowedOrigins) {
        List<String> origins = List.of(allowedOrigins.split(","));
        return (exchange, chain) -> {
            String origin = exchange.getRequest().getHeaders().getOrigin();
            if (origin != null && origins.stream().map(String::trim).anyMatch(origin::equals)) {
                exchange.getResponse().beforeCommit(() -> {
                    var headers = exchange.getResponse().getHeaders();
                    headers.set("Access-Control-Allow-Origin", origin);
                    headers.set("Access-Control-Allow-Credentials", "true");
                    headers.set("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
                    headers.set("Access-Control-Allow-Headers", "*");
                    return Mono.empty();
                });
            }
            return chain.filter(exchange);
        };
    }
}