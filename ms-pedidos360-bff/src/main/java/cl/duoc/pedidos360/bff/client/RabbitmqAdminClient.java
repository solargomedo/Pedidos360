package cl.duoc.pedidos360.bff.client;

import cl.duoc.pedidos360.bff.dto.RabbitmqMonitor;
import java.net.http.HttpClient;
import java.time.Instant;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RabbitmqAdminClient {
    private final RestClient client;

    public RabbitmqAdminClient(
            @Value("${rabbitmq.admin.service.url}") String url,
            @Value("${rabbitmq.admin.connect-timeout:2s}") Duration connectTimeout,
            @Value("${rabbitmq.admin.read-timeout:40s}") Duration readTimeout) {
        var http = HttpClient.newBuilder().connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(readTimeout);
        client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    public ResponseEntity<RabbitmqMonitor> monitor(String token) {
        try {
            return client.get().uri("/api/admin/rabbitmq/monitor")
                    .headers(headers -> headers.setBearerAuth(token))
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status != 200 && status != 503) return unavailable();
                        var body = response.bodyTo(RabbitmqMonitor.class);
                        if (body == null || body.status() == null
                                || !(body.status().equals("available") || body.status().equals("partial")
                                || body.status().equals("unavailable"))) return unavailable();
                        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
                    });
        } catch (Exception exception) {
            // Never return upstream bodies, URLs, exception messages or tokens.
            return unavailable();
        }
    }

    private ResponseEntity<RabbitmqMonitor> unavailable() {
        return ResponseEntity.status(503).cacheControl(CacheControl.noStore())
                .body(new RabbitmqMonitor(Instant.now(), "unavailable", null, null, null, null));
    }
}
