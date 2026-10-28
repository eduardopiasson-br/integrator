package br.com.piasson.activation.domain;

import br.com.piasson.activation.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Testa o mapeamento JPA e o schema Flyway contra um PostgreSQL real. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class ActivationRequestRepositoryTest {

    @Autowired
    private ActivationRequestRepository repository;

    @Test
    void persistsAndFindsByExternalId() {
        ActivationRequest saved = repository.saveAndFlush(
                ActivationRequest.create("EXT-100", "CUST-1", "INTERNET-500"));

        assertThat(saved.getVersion()).isZero();
        assertThat(repository.findByExternalId("EXT-100"))
                .get()
                .extracting(ActivationRequest::getId, ActivationRequest::getStatus)
                .containsExactly(saved.getId(), ActivationStatus.PENDING);
    }

    @Test
    void externalIdIsUniqueAtDatabaseLevel() {
        repository.saveAndFlush(ActivationRequest.create("EXT-200", "CUST-1", "INTERNET-500"));

        assertThatThrownBy(() -> repository.saveAndFlush(
                ActivationRequest.create("EXT-200", "CUST-2", "TV-100")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void statusTransitionIncrementsVersion() {
        ActivationRequest saved = repository.saveAndFlush(
                ActivationRequest.create("EXT-300", "CUST-1", "INTERNET-500"));

        saved.startProcessing();
        ActivationRequest updated = repository.saveAndFlush(saved);

        assertThat(updated.getVersion()).isEqualTo(1L);
        assertThat(updated.getStatus()).isEqualTo(ActivationStatus.PROCESSING);
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(updated.getCreatedAt());
    }
}
