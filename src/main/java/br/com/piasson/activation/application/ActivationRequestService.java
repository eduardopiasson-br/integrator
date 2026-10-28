package br.com.piasson.activation.application;

import br.com.piasson.activation.domain.ActivationRequest;
import br.com.piasson.activation.domain.ActivationRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ActivationRequestService {

    private final ActivationRequestRepository repository;

    public ActivationRequestService(ActivationRequestRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ActivationRequest create(CreateActivationRequestCommand command) {
        ActivationRequest request = ActivationRequest.create(
                command.externalId(), command.customerId(), command.productCode());
        return repository.save(request);
    }

    @Transactional(readOnly = true)
    public ActivationRequest findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ActivationRequestNotFoundException(id));
    }
}
