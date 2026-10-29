package br.com.piasson.activation.application;

import br.com.piasson.activation.domain.ActivationRequest;

/**
 * Resultado do caso de uso de criação. Ser {@code sealed} permite que quem consome
 * (o controller) trate todos os casos em um {@code switch} exaustivo.
 */
public sealed interface CreationResult {

    ActivationRequest request();

    /** Uma solicitação nova foi registrada. */
    record Created(ActivationRequest request) implements CreationResult {
    }

    /** Já existia uma solicitação idêntica (retry legítimo); nada foi criado. */
    record AlreadyExists(ActivationRequest request) implements CreationResult {
    }
}
