package br.com.piasson.activation.application;

import br.com.piasson.activation.domain.ActivationRequest;
import br.com.piasson.activation.domain.ActivationRequestRepository;
import br.com.piasson.activation.domain.ActivationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ActivationRequestServiceTest {

    @Mock
    private ActivationRequestRepository repository;

    @InjectMocks
    private ActivationRequestService service;

    @Test
    void createPersistsPendingRequestFromCommand() {
        given(repository.save(any(ActivationRequest.class))).willAnswer(inv -> inv.getArgument(0));

        ActivationRequest created = service.create(
                new CreateActivationRequestCommand("EXT-1", "CUST-1", "INTERNET-500"));

        ArgumentCaptor<ActivationRequest> captor = ArgumentCaptor.forClass(ActivationRequest.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(created);
        assertThat(created.getExternalId()).isEqualTo("EXT-1");
        assertThat(created.getCustomerId()).isEqualTo("CUST-1");
        assertThat(created.getProductCode()).isEqualTo("INTERNET-500");
        assertThat(created.getStatus()).isEqualTo(ActivationStatus.PENDING);
    }

    @Test
    void findByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        given(repository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(ActivationRequestNotFoundException.class)
                .hasMessageContaining(id.toString());
    }
}
