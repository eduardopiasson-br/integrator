package br.com.piasson.activation.domain;

import java.util.Set;

/**
 * Ciclo de vida de uma solicitação: PENDING -> PROCESSING -> COMPLETED | FAILED.
 * Cada estado conhece apenas os estados para os quais pode avançar.
 */
public enum ActivationStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED;

    public boolean canTransitionTo(ActivationStatus target) {
        return switch (this) {
            case PENDING -> target == PROCESSING;
            case PROCESSING -> target == COMPLETED || target == FAILED;
            case COMPLETED, FAILED -> false;
        };
    }

    public boolean isFinal() {
        return this == COMPLETED || this == FAILED;
    }

    public Set<ActivationStatus> allowedTransitions() {
        return switch (this) {
            case PENDING -> Set.of(PROCESSING);
            case PROCESSING -> Set.of(COMPLETED, FAILED);
            case COMPLETED, FAILED -> Set.of();
        };
    }
}
