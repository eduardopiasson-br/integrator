package br.com.piasson.activation.application;

import java.util.UUID;

/** Já existe uma solicitação com o mesmo externalId, porém com conteúdo diferente. */
public class DuplicateActivationRequestException extends RuntimeException {

    private final String externalId;
    private final UUID existingId;

    public DuplicateActivationRequestException(String externalId, UUID existingId) {
        super("Já existe uma solicitação com externalId '%s' e conteúdo diferente".formatted(externalId));
        this.externalId = externalId;
        this.existingId = existingId;
    }

    public String externalId() {
        return externalId;
    }

    public UUID existingId() {
        return existingId;
    }
}
