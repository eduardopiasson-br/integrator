package br.com.piasson.activation.api.error;

import java.net.URI;

/** Catálogo dos tipos de problema expostos pela API (campo {@code type} do RFC 9457). */
public enum ProblemType {
    VALIDATION_ERROR("validation-error", "Dados inválidos"),
    MALFORMED_REQUEST("malformed-request", "Requisição malformada"),
    NOT_FOUND("not-found", "Recurso não encontrado"),
    DUPLICATE_REQUEST("duplicate-request", "Solicitação duplicada"),
    INTERNAL_ERROR("internal-error", "Erro inesperado");

    private static final String BASE = "urn:activation-service:problem:";

    private final URI uri;
    private final String title;

    ProblemType(String slug, String title) {
        this.uri = URI.create(BASE + slug);
        this.title = title;
    }

    public URI uri() {
        return uri;
    }

    public String title() {
        return title;
    }
}
