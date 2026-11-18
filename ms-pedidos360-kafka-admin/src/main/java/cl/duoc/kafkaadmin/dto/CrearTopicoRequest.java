package cl.duoc.kafkaadmin.dto;

import jakarta.validation.constraints.*;
import java.util.Map;

public record CrearTopicoRequest(
        @NotBlank @Size(max = 249) @Pattern(regexp = "[a-zA-Z0-9._-]+") String nombre,
        @NotNull @Positive Integer particiones,
        @NotNull @Positive @Max(32767) Integer factorReplicacion,
        Map<@NotBlank String, @NotBlank String> configuraciones) {
}
