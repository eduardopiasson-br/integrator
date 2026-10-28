package br.com.piasson.activation.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Teste unitário puro (sem Spring) das regras de transição de status. */
class ActivationRequestTest {

    private static ActivationRequest newRequest() {
        return ActivationRequest.create("EXT-1", "CUST-1", "INTERNET-500");
    }

    @Test
    void newRequestStartsAsPendingWithTimestamps() {
        ActivationRequest request = newRequest();

        assertThat(request.getId()).isNotNull();
        assertThat(request.getStatus()).isEqualTo(ActivationStatus.PENDING);
        assertThat(request.getCreatedAt()).isNotNull();
        assertThat(request.getUpdatedAt()).isEqualTo(request.getCreatedAt());
    }

    @Test
    void happyPathPendingProcessingCompleted() {
        ActivationRequest request = newRequest();

        request.startProcessing();
        assertThat(request.getStatus()).isEqualTo(ActivationStatus.PROCESSING);

        request.complete();
        assertThat(request.getStatus()).isEqualTo(ActivationStatus.COMPLETED);
    }

    @Test
    void processingCanFail() {
        ActivationRequest request = newRequest();
        request.startProcessing();

        request.fail();

        assertThat(request.getStatus()).isEqualTo(ActivationStatus.FAILED);
    }

    @Test
    void pendingCannotCompleteOrFailDirectly() {
        assertThatThrownBy(() -> newRequest().complete())
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("PENDING -> COMPLETED");

        assertThatThrownBy(() -> newRequest().fail())
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("PENDING -> FAILED");
    }

    @Test
    void completedIsFinalAndCannotGoBackToProcessing() {
        ActivationRequest request = newRequest();
        request.startProcessing();
        request.complete();

        assertThatThrownBy(request::startProcessing)
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("COMPLETED -> PROCESSING");
        assertThatThrownBy(request::fail)
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void failedIsFinal() {
        ActivationRequest request = newRequest();
        request.startProcessing();
        request.fail();

        assertThatThrownBy(request::startProcessing).isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(request::complete).isInstanceOf(InvalidStatusTransitionException.class);
    }

    @ParameterizedTest
    @EnumSource(ActivationStatus.class)
    void noStatusTransitionsToItself(ActivationStatus status) {
        assertThat(status.canTransitionTo(status)).isFalse();
    }

    @Test
    void sameContentMeansSameCustomerAndProduct() {
        ActivationRequest request = newRequest();

        assertThat(request.hasSameContent("CUST-1", "INTERNET-500")).isTrue();
        assertThat(request.hasSameContent("CUST-1", "INTERNET-1000")).isFalse();
        assertThat(request.hasSameContent("CUST-2", "INTERNET-500")).isFalse();
    }
}
