package cl.duoc.kafkaadmin.exception;

import org.apache.kafka.common.errors.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class KafkaAdminExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(KafkaAdminExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> manejar(Exception error) {
        HttpStatus status;
        String message;
        if (error instanceof TopicExistsException) {
            status = HttpStatus.CONFLICT;
            message = "El topico ya existe";
        } else if (error instanceof UnknownTopicOrPartitionException || error instanceof NoResourceFoundException) {
            status = HttpStatus.NOT_FOUND;
            message = "Recurso o topico no encontrado";
        } else if (error instanceof IllegalArgumentException || error instanceof InvalidTopicException
                || error instanceof InvalidConfigurationException || error instanceof InvalidPartitionsException
                || error instanceof InvalidReplicationFactorException || error instanceof PolicyViolationException
                || error instanceof MethodArgumentNotValidException || error instanceof HttpMessageNotReadableException) {
            status = HttpStatus.BAD_REQUEST;
            message = "Solicitud o configuracion de topico invalida";
        } else if (error instanceof TimeoutException) {
            status = HttpStatus.GATEWAY_TIMEOUT;
            message = "Tiempo de operacion Kafka agotado; verificar el estado antes de repetir una escritura";
        } else if (error instanceof RetriableException) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            message = "Kafka no esta disponible temporalmente";
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            message = "Error interno al administrar Kafka";
        }
        log.warn("Operacion Kafka REST fallida status={} causa={}", status.value(), error.toString());
        if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
            log.error("Fallo inesperado de administracion Kafka", error);
        }
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, message));
    }
}
