package br.com.piasson.activation.api.error;

import br.com.piasson.activation.api.ActivationRequestController;
import br.com.piasson.activation.application.ActivationRequestNotFoundException;
import br.com.piasson.activation.application.ActivationRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ActivationRequestController.class)
class ApiExceptionHandlerTest {

    private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ActivationRequestService service;

    @Test
    void validationErrorListsFieldsAndTraceId() throws Exception {
        mockMvc.perform(post("/api/v1/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\": \"EXT 1\", \"productCode\": \"INTERNET-500\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:activation-service:problem:validation-error"))
                .andExpect(jsonPath("$.title").value("Dados inválidos"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/api/v1/requests"))
                .andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{32}")))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0].field").value("customerId"))
                .andExpect(jsonPath("$.errors[0].message").value("não deve estar em branco"))
                .andExpect(jsonPath("$.errors[1].field").value("externalId"));
    }

    @Test
    void malformedJsonIsReportedWithoutParserInternals() throws Exception {
        mockMvc.perform(post("/api/v1/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:activation-service:problem:malformed-request"))
                .andExpect(jsonPath("$.detail").value(not(matchesPattern("(?s).*(Jackson|JsonParse|Unexpected character).*"))))
                .andExpect(jsonPath("$.traceId").isString());
    }

    @Test
    void notFoundIsProblemDetail() throws Exception {
        UUID id = UUID.randomUUID();
        given(service.findById(id)).willThrow(new ActivationRequestNotFoundException(id));

        mockMvc.perform(get("/api/v1/requests/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:activation-service:problem:not-found"))
                .andExpect(jsonPath("$.title").value("Recurso não encontrado"))
                .andExpect(jsonPath("$.detail").value("Solicitação não encontrada: " + id))
                .andExpect(jsonPath("$.instance").value("/api/v1/requests/" + id))
                .andExpect(jsonPath("$.traceId").isString());
    }

    @Test
    void unexpectedErrorHidesInternalDetails() throws Exception {
        given(service.findById(any())).willThrow(new IllegalStateException("senha=segredo conexão caiu"));

        mockMvc.perform(get("/api/v1/requests/{id}", UUID.randomUUID()))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:activation-service:problem:internal-error"))
                .andExpect(jsonPath("$.title").value("Erro inesperado"))
                .andExpect(jsonPath("$.detail").value(not(matchesPattern(".*(segredo|IllegalState|conexão).*"))))
                .andExpect(jsonPath("$.traceId").isString())
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void springHandledErrorsAlsoCarryTraceId() throws Exception {
        mockMvc.perform(get("/api/v1/requests/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.traceId").isString());

        mockMvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.traceId").isString());
    }

    @Test
    void incomingCorrelationIdIsEchoedAndUsedAsTraceId() throws Exception {
        UUID id = UUID.randomUUID();
        given(service.findById(id)).willThrow(new ActivationRequestNotFoundException(id));

        mockMvc.perform(get("/api/v1/requests/{id}", id).header(CorrelationIdFilter.HEADER, "client-req-42"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(CorrelationIdFilter.HEADER, "client-req-42"))
                .andExpect(jsonPath("$.traceId").value("client-req-42"));
    }

    @Test
    void unsafeCorrelationIdIsReplaced() throws Exception {
        UUID id = UUID.randomUUID();
        given(service.findById(id)).willThrow(new ActivationRequestNotFoundException(id));

        mockMvc.perform(get("/api/v1/requests/{id}", id).header(CorrelationIdFilter.HEADER, "evil\nvalue <script>"))
                .andExpect(header().string(CorrelationIdFilter.HEADER, matchesPattern("[0-9a-f]{32}")))
                .andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{32}")));
    }
}
