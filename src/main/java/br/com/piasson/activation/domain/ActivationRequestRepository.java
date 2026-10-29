package br.com.piasson.activation.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ActivationRequestRepository extends JpaRepository<ActivationRequest, UUID> {

    /** Nome da constraint única de externalId (ver migração V1). */
    String UK_EXTERNAL_ID = "uk_activation_request_external_id";

    Optional<ActivationRequest> findByExternalId(String externalId);
}
