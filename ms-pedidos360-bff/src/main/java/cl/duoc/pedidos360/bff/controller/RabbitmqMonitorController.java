package cl.duoc.pedidos360.bff.controller;

import cl.duoc.pedidos360.bff.client.RabbitmqAdminClient;
import cl.duoc.pedidos360.bff.dto.RabbitmqMonitor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RabbitmqMonitorController {
    private final RabbitmqAdminClient client;

    public RabbitmqMonitorController(RabbitmqAdminClient client) {
        this.client = client;
    }

    @GetMapping("/api/bff/rabbitmq/monitor")
    public ResponseEntity<RabbitmqMonitor> monitor(@AuthenticationPrincipal Jwt jwt) {
        return client.monitor(jwt.getTokenValue());
    }
}
