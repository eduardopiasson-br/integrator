package br.com.piasson.activation.application;

/** Entrada do caso de uso de criação, já validada pela camada de API. */
public record CreateActivationRequestCommand(String externalId, String customerId, String productCode) {
}
