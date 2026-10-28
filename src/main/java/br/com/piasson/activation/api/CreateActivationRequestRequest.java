package br.com.piasson.activation.api;

import br.com.piasson.activation.application.CreateActivationRequestCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Corpo do POST /api/v1/requests.
 *
 * <p>Regras: todos os campos obrigatórios, até 64 caracteres, apenas letras, dígitos,
 * hífen e underscore (identificadores vindos de outros sistemas, sem espaços ou acentos).
 */
public record CreateActivationRequestRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = IDENTIFIER) String externalId,
        @NotBlank @Size(max = 64) @Pattern(regexp = IDENTIFIER) String customerId,
        @NotBlank @Size(max = 64) @Pattern(regexp = IDENTIFIER) String productCode) {

    static final String IDENTIFIER = "^[A-Za-z0-9_-]+$";

    public CreateActivationRequestCommand toCommand() {
        return new CreateActivationRequestCommand(externalId, customerId, productCode);
    }
}
