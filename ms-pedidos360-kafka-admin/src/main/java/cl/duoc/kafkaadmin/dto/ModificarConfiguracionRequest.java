package cl.duoc.kafkaadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Map;

public record ModificarConfiguracionRequest(
        @NotEmpty Map<@NotBlank String, @NotBlank String> configuraciones) {
}
