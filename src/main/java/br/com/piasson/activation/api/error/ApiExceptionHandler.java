package br.com.piasson.activation.api.error;

import br.com.piasson.activation.application.ActivationRequestNotFoundException;
import br.com.piasson.activation.application.DuplicateActivationRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.List;

/**
 * Converte toda exceção em uma resposta {@code application/problem+json} (RFC 9457).
 *
 * <p>Estende {@link ResponseEntityExceptionHandler} para aproveitar o tratamento padrão do
 * Spring MVC (405, 406, 415, rota inexistente etc.) e personaliza os casos relevantes para a
 * API. Nenhuma resposta carrega stack trace ou mensagem interna; o {@code traceId} permite
 * localizar o detalhe completo no log.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    public record FieldViolation(String field, String message) {
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(ApiExceptionHandler::toViolation)
                .sorted((a, b) -> a.field().compareTo(b.field()))
                .toList();
        ProblemDetail problem = problem(ProblemType.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                "Um ou mais campos não passaram na validação.", request);
        problem.setProperty("errors", violations);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail problem = problem(ProblemType.MALFORMED_REQUEST, HttpStatus.BAD_REQUEST,
                "O corpo da requisição não pôde ser lido. Verifique se o JSON é válido.", request);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /** Demais exceções já mapeadas pelo Spring (404 de rota, 405, 415, 400 de tipo...): só anexa o traceId. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers,
                                                          HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            problem.setProperty("traceId", CorrelationIdFilter.current());
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    @ExceptionHandler(ActivationRequestNotFoundException.class)
    ProblemDetail handleNotFound(ActivationRequestNotFoundException ex, WebRequest request) {
        return problem(ProblemType.NOT_FOUND, HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateActivationRequestException.class)
    ProblemDetail handleDuplicate(DuplicateActivationRequestException ex, WebRequest request) {
        ProblemDetail problem = problem(ProblemType.DUPLICATE_REQUEST, HttpStatus.CONFLICT, ex.getMessage(), request);
        problem.setProperty("externalId", ex.externalId());
        problem.setProperty("existingId", ex.existingId());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex, WebRequest request) {
        log.error("Erro inesperado ao processar a requisição [traceId={}]", CorrelationIdFilter.current(), ex);
        return problem(ProblemType.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocorreu um erro inesperado. Informe o traceId ao suporte.", request);
    }

    private static ProblemDetail problem(ProblemType type, HttpStatus status, String detail, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type.uri());
        problem.setTitle(type.title());
        problem.setInstance(URI.create(((ServletWebRequest) request).getRequest().getRequestURI()));
        problem.setProperty("traceId", CorrelationIdFilter.current());
        return problem;
    }

    private static FieldViolation toViolation(FieldError error) {
        return new FieldViolation(error.getField(), error.getDefaultMessage());
    }
}
