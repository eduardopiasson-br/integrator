package br.com.piasson.activation.application;

import br.com.piasson.activation.domain.ActivationRequest;
import br.com.piasson.activation.domain.ActivationRequestRepository;
import br.com.piasson.activation.domain.ActivationStatus;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ActivationRequestServiceTest {

    private static final CreateActivationRequestCommand COMMAND =
            new CreateActivationRequestCommand("EXT-1", "CUST-1", "INTERNET-500");

    @Mock
    private ActivationRequestRepository repository;

    @InjectMocks
    private ActivationRequestService service;

    @Test
    void createPersistsPendingRequestWhenExternalIdIsNew() {
        given(repository.findByExternalId("EXT-1")).willReturn(Optional.empty());
        given(repository.save(any(ActivationRequest.class))).willAnswer(inv -> inv.getArgument(0));

        CreationResult result = service.create(COMMAND);

        assertThat(result).isInstanceOf(CreationResult.Created.class);
        assertThat(result.request().getExternalId()).isEqualTo("EXT-1");
        assertThat(result.request().getStatus()).isEqualTo(ActivationStatus.PENDING);
    }

    @Test
    void identicalRetryReturnsExistingWithoutInserting() {
        ActivationRequest existing = ActivationRequest.create("EXT-1", "CUST-1", "INTERNET-500");
        given(repository.findByExternalId("EXT-1")).willReturn(Optional.of(existing));

        CreationResult result = service.create(COMMAND);

        assertThat(result).isInstanceOf(CreationResult.AlreadyExists.class);
        assertThat(result.request()).isSameAs(existing);
        verify(repository, never()).save(any());
    }

    @Test
    void sameExternalIdWithDifferentContentIsRejected() {
        ActivationRequest existing = ActivationRequest.create("EXT-1", "CUST-1", "TV-100");
        given(repository.findByExternalId("EXT-1")).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(COMMAND))
                .isInstanceOf(DuplicateActivationRequestException.class)
                .satisfies(e -> assertThat(((DuplicateActivationRequestException) e).existingId())
                        .isEqualTo(existing.getId()));
        verify(repository, never()).save(any());
    }

    @Test
    void losingTheInsertRaceFallsBackToTheWinningRecord() {
        ActivationRequest winner = ActivationRequest.create("EXT-1", "CUST-1", "INTERNET-500");
        given(repository.findByExternalId("EXT-1"))
                .willReturn(Optional.empty())      // antes do INSERT: ainda não existia
                .willReturn(Optional.of(winner));  // depois da violação: o vencedor já está lá
        given(repository.save(any(ActivationRequest.class)))
                .willThrow(uniqueViolation(ActivationRequestRepository.UK_EXTERNAL_ID));

        CreationResult result = service.create(COMMAND);

        assertThat(result).isInstanceOf(CreationResult.AlreadyExists.class);
        assertThat(result.request()).isSameAs(winner);
    }

    @Test
    void losingTheRaceToDifferentContentIsRejected() {
        ActivationRequest winner = ActivationRequest.create("EXT-1", "CUST-9", "TV-100");
        given(repository.findByExternalId("EXT-1"))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(winner));
        given(repository.save(any(ActivationRequest.class)))
                .willThrow(uniqueViolation(ActivationRequestRepository.UK_EXTERNAL_ID));

        assertThatThrownBy(() -> service.create(COMMAND))
                .isInstanceOf(DuplicateActivationRequestException.class);
    }

    @Test
    void otherIntegrityViolationsAreNotSwallowed() {
        given(repository.findByExternalId("EXT-1")).willReturn(Optional.empty());
        DataIntegrityViolationException other = uniqueViolation("some_other_constraint");
        given(repository.save(any(ActivationRequest.class))).willThrow(other);

        assertThatThrownBy(() -> service.create(COMMAND)).isSameAs(other);
    }

    @Test
    void findByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        given(repository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(ActivationRequestNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    private static DataIntegrityViolationException uniqueViolation(String constraintName) {
        return new DataIntegrityViolationException("duplicate key",
                new ConstraintViolationException("duplicate key", new SQLException("23505"), constraintName));
    }
}
