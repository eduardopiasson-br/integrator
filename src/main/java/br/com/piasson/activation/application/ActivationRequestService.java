package br.com.piasson.activation.application;

import br.com.piasson.activation.domain.ActivationRequest;
import br.com.piasson.activation.domain.ActivationRequestRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ActivationRequestService {

    private static final Logger log = LoggerFactory.getLogger(ActivationRequestService.class);

    private final ActivationRequestRepository repository;

    public ActivationRequestService(ActivationRequestRepository repository) {
        this.repository = repository;
    }

    /**
     * Cria a solicitação ou reconhece uma repetição.
     *
     * <p>Deliberadamente <em>não</em> é transacional como um todo: o {@code INSERT} roda na
     * transação curta do repositório. Se a constraint única disparar, essa transação já foi
     * abortada e revertida, e podemos consultar o registro vencedor em uma transação nova.
     * Dentro de uma única transação, o PostgreSQL rejeitaria qualquer comando após o erro.
     */
    public CreationResult create(CreateActivationRequestCommand command) {
        var existing = repository.findByExternalId(command.externalId());
        if (existing.isPresent()) {
            return resolveDuplicate(existing.get(), command);
        }
        try {
            ActivationRequest request = ActivationRequest.create(
                    command.externalId(), command.customerId(), command.productCode());
            return new CreationResult.Created(repository.save(request));
        } catch (DataIntegrityViolationException e) {
            if (!isExternalIdViolation(e)) {
                throw e;
            }
            // Perdeu a corrida: outra requisição inseriu o mesmo externalId entre o SELECT e o INSERT.
            log.info("Inserção concorrente detectada para externalId={}", command.externalId());
            ActivationRequest winner = repository.findByExternalId(command.externalId())
                    .orElseThrow(() -> e);
            return resolveDuplicate(winner, command);
        }
    }

    @Transactional(readOnly = true)
    public ActivationRequest findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ActivationRequestNotFoundException(id));
    }

    private static CreationResult resolveDuplicate(ActivationRequest existing, CreateActivationRequestCommand command) {
        if (existing.hasSameContent(command.customerId(), command.productCode())) {
            return new CreationResult.AlreadyExists(existing);
        }
        throw new DuplicateActivationRequestException(command.externalId(), existing.getId());
    }

    private static boolean isExternalIdViolation(DataIntegrityViolationException e) {
        return e.getCause() instanceof ConstraintViolationException cve
                && ActivationRequestRepository.UK_EXTERNAL_ID.equalsIgnoreCase(cve.getConstraintName());
    }
}
