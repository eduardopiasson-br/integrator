package br.com.piasson.activation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * Solicitação de ativação de um produto para um cliente.
 *
 * <p>O status só muda pelos métodos de transição ({@link #startProcessing()},
 * {@link #complete()}, {@link #fail()}); não há setter de status. O id é gerado
 * pela aplicação para que seja conhecido antes do INSERT (útil como chave de
 * idempotência na integração com o provedor).
 */
@Entity
@Table(name = "activation_request")
public class ActivationRequest {

    @Id
    private UUID id;

    @Column(name = "external_id", nullable = false, length = 64)
    private String externalId;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Column(name = "product_code", nullable = false, length = 64)
    private String productCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActivationStatus status;

    /** Lock otimista: duas escritas concorrentes na mesma linha não se sobrescrevem silenciosamente. */
    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ActivationRequest() {
        // exigido pelo JPA
    }

    private ActivationRequest(String externalId, String customerId, String productCode) {
        this.id = UUID.randomUUID();
        this.externalId = Objects.requireNonNull(externalId, "externalId");
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.productCode = Objects.requireNonNull(productCode, "productCode");
        this.status = ActivationStatus.PENDING;
        Instant now = now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static ActivationRequest create(String externalId, String customerId, String productCode) {
        return new ActivationRequest(externalId, customerId, productCode);
    }

    public void startProcessing() {
        transitionTo(ActivationStatus.PROCESSING);
    }

    public void complete() {
        transitionTo(ActivationStatus.COMPLETED);
    }

    public void fail() {
        transitionTo(ActivationStatus.FAILED);
    }

    private void transitionTo(ActivationStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(status, target);
        }
        this.status = target;
        this.updatedAt = now();
    }

    /** Mesmo externalId com os mesmos dados de negócio: caracteriza um retry legítimo. */
    public boolean hasSameContent(String customerId, String productCode) {
        return this.customerId.equals(customerId) && this.productCode.equals(productCode);
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = now();
    }

    /** Milissegundos: precisão suficiente e idêntica antes e depois de persistir (o banco guarda micros). */
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    public UUID getId() {
        return id;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getProductCode() {
        return productCode;
    }

    public ActivationStatus getStatus() {
        return status;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
