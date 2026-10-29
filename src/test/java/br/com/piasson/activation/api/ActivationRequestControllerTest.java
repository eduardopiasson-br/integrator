package br.com.piasson.activation.api;

import br.com.piasson.activation.application.ActivationRequestNotFoundException;
import br.com.piasson.activation.application.ActivationRequestService;
import br.com.piasson.activation.application.CreateActivationRequestCommand;
import br.com.piasson.activation.application.CreationResult;
import br.com.piasson.activation.application.DuplicateActivationRequestException;
import br.com.piasson.activation.domain.ActivationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fatia web: só o controller, validação e serialização; o service é mockado. */
@WebMvcTest(ActivationRequestController.class)
class ActivationRequestControllerTest {

    private static final String VALID_BODY = """
            {"externalId": "EXT-12345", "customerId": "CUST-10001", "productCode": "INTERNET-500"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ActivationRequestService service;

    @Test
    void createReturns201WithLocationAndBody() throws Exception {
        ActivationRequest created = ActivationRequest.create("EXT-12345", "CUST-10001", "INTERNET-500");
        given(service.create(new CreateActivationRequestCommand("EXT-12345", "CUST-10001", "INTERNET-500")))
                .willReturn(new CreationResult.Created(created));

        mockMvc.perform(post("/api/v1/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/requests/" + created.getId())))
                .andExpect(jsonPath("$.id").value(created.getId().toString()))
                .andExpect(jsonPath("$.externalId").value("EXT-12345"))
                .andExpect(jsonPath("$.customerId").value("CUST-10001"))
                .andExpect(jsonPath("$.productCode").value("INTERNET-500"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.updatedAt").isString());
    }

    @Test
    void identicalRetryReturns200WithExistingRecordAndNoLocation() throws Exception {
        ActivationRequest existing = ActivationRequest.create("EXT-12345", "CUST-10001", "INTERNET-500");
        given(service.create(any())).willReturn(new CreationResult.AlreadyExists(existing));

        mockMvc.perform(post("/api/v1/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.id").value(existing.getId().toString()));
    }

    @Test
    void conflictingContentReturns409ProblemWithExistingId() throws Exception {
        UUID existingId = UUID.randomUUID();
        given(service.create(any())).willThrow(new DuplicateActivationRequestException("EXT-12345", existingId));

        mockMvc.perform(post("/api/v1/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:activation-service:problem:duplicate-request"))
                .andExpect(jsonPath("$.title").value("Solicitação duplicada"))
                .andExpect(jsonPath("$.externalId").value("EXT-12345"))
                .andExpect(jsonPath("$.existingId").value(existingId.toString()))
                .andExpect(jsonPath("$.traceId").isString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"externalId\": \"\", \"customerId\": \"CUST-1\", \"productCode\": \"INTERNET-500\"}",
            "{\"externalId\": \"EXT-1\", \"customerId\": \"   \", \"productCode\": \"INTERNET-500\"}",
            "{\"externalId\": \"EXT-1\", \"customerId\": \"CUST-1\"}",
            "{\"externalId\": \"EXT 1\", \"customerId\": \"CUST-1\", \"productCode\": \"INTERNET-500\"}",
            "{\"externalId\": \"EXT-1\", \"customerId\": \"CUST-1\", \"productCode\": \"ÇODE\"}",
    })
    void createRejectsInvalidBodyWith400(String body) throws Exception {
        mockMvc.perform(post("/api/v1/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(service, never()).create(any());
    }

    @Test
    void createRejectsFieldLongerThan64Chars() throws Exception {
        String tooLong = "X".repeat(65);
        mockMvc.perform(post("/api/v1/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\": \"%s\", \"customerId\": \"CUST-1\", \"productCode\": \"P\"}"
                                .formatted(tooLong)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRejectsMalformedJsonWith400() throws Exception {
        mockMvc.perform(post("/api/v1/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void findByIdReturnsExistingRequest() throws Exception {
        ActivationRequest existing = ActivationRequest.create("EXT-1", "CUST-1", "INTERNET-500");
        given(service.findById(existing.getId())).willReturn(existing);

        mockMvc.perform(get("/api/v1/requests/{id}", existing.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(existing.getId().toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void findByIdReturns404WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        given(service.findById(id)).willThrow(new ActivationRequestNotFoundException(id));

        mockMvc.perform(get("/api/v1/requests/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void findByIdReturns400ForNonUuidId() throws Exception {
        mockMvc.perform(get("/api/v1/requests/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }
}
