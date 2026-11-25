package cl.duoc.pedidos360.bff.client;

import cl.duoc.pedidos360.bff.dto.MessagingEventsPage;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OrdersEventsClient {
    private final RestClient client;
    public OrdersEventsClient(@Value("${orders.service.url}") String url,
            @Value("${orders.events.connect-timeout:2s}") Duration connectTimeout,
            @Value("${orders.events.read-timeout:5s}") Duration readTimeout) {
        var http = HttpClient.newBuilder().connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(readTimeout);
        client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    public ResponseEntity<?> events(String token, long after, int limit, Long pedidoId) {
        try {
            return client.get().uri(builder -> {
                builder.path("/api/orders/messaging/events").queryParam("after", after).queryParam("limit", limit);
                if (pedidoId != null) builder.queryParam("pedidoId", pedidoId);
                return builder.build();
            }).headers(headers -> headers.setBearerAuth(token))
              .exchange((request, response) -> {
                  int status = response.getStatusCode().value();
                  if (status == 400) return error(400, "INVALID_PARAMETERS");
                  if (status == 401) return error(401, "UNAUTHORIZED");
                  if (status == 403) return error(403, "FORBIDDEN");
                  if (status != 200) return error(503, "ORDERS_UNAVAILABLE");
                  var body = response.bodyTo(MessagingEventsPage.class);
                  if (body == null || body.instanceId() == null || body.capacity() == null
                          || body.earliestSequence() == null || body.lastSequence() == null
                          || body.nextAfter() == null || body.truncated() == null || body.events() == null) {
                      return error(503, "ORDERS_UNAVAILABLE");
                  }
                  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
              });
        } catch (Exception exception) {
            // Do not log or return tokens, upstream bodies or internal exception details.
            return error(503, "ORDERS_UNAVAILABLE");
        }
    }

    public static ResponseEntity<Map<String, String>> error(int status, String code) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("error", code));
    }
}
