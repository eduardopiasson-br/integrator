package br.com.piasson.activation.api;

import br.com.piasson.activation.application.ActivationRequestService;
import br.com.piasson.activation.application.CreationResult;
import br.com.piasson.activation.domain.ActivationRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/requests")
public class ActivationRequestController {

    private final ActivationRequestService service;

    public ActivationRequestController(ActivationRequestService service) {
        this.service = service;
    }

    /**
     * 201 quando cria; 200 com o registro existente quando é uma repetição idêntica
     * (retry do sistema de origem). Conteúdo divergente vira 409 (ver ApiExceptionHandler).
     */
    @PostMapping
    public ResponseEntity<ActivationRequestResponse> create(@Valid @RequestBody CreateActivationRequestRequest body) {
        CreationResult result = service.create(body.toCommand());
        ActivationRequestResponse response = ActivationRequestResponse.from(result.request());
        return switch (result) {
            case CreationResult.Created created -> ResponseEntity
                    .created(locationOf(created.request()))
                    .body(response);
            case CreationResult.AlreadyExists existing -> ResponseEntity.ok(response);
        };
    }

    private static URI locationOf(ActivationRequest request) {
        return ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(request.getId())
                .toUri();
    }

    @GetMapping("/{id}")
    public ActivationRequestResponse findById(@PathVariable UUID id) {
        return ActivationRequestResponse.from(service.findById(id));
    }
}
