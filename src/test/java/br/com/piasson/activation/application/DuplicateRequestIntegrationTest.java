package br.com.piasson.activation.application;

import br.com.piasson.activation.TestcontainersConfiguration;
import br.com.piasson.activation.domain.ActivationRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Duplicidade contra o PostgreSQL real: a constraint única é a única proteção que
 * funciona quando várias requisições idênticas chegam ao mesmo tempo.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DuplicateRequestIntegrationTest {

    @Autowired
    private ActivationRequestService service;

    @Autowired
    private ActivationRequestRepository repository;

    @Test
    void sequentialRetryReturnsTheSameRecord() {
        var command = new CreateActivationRequestCommand("EXT-DUP-SEQ", "CUST-1", "INTERNET-500");

        CreationResult first = service.create(command);
        CreationResult second = service.create(command);

        assertThat(first).isInstanceOf(CreationResult.Created.class);
        assertThat(second).isInstanceOf(CreationResult.AlreadyExists.class);
        assertThat(second.request().getId()).isEqualTo(first.request().getId());
        assertThat(repository.findAll()).filteredOn(r -> r.getExternalId().equals("EXT-DUP-SEQ")).hasSize(1);
    }

    @Test
    void concurrentIdenticalRequestsCreateExactlyOneRecord() throws Exception {
        int threads = 20;
        var command = new CreateActivationRequestCommand("EXT-DUP-RACE", "CUST-1", "INTERNET-500");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<CreationResult>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<CreationResult> task = () -> {
                    start.await(5, TimeUnit.SECONDS); // todas as threads largam juntas
                    return service.create(command);
                };
                futures.add(pool.submit(task));
            }
            start.countDown();

            List<CreationResult> results = new ArrayList<>();
            for (Future<CreationResult> f : futures) {
                results.add(f.get(30, TimeUnit.SECONDS)); // propaga qualquer exceção inesperada
            }

            assertThat(results).hasSize(threads);
            assertThat(results).filteredOn(CreationResult.Created.class::isInstance).hasSize(1);
            assertThat(results).filteredOn(CreationResult.AlreadyExists.class::isInstance).hasSize(threads - 1);
            assertThat(results).extracting(r -> r.request().getId()).containsOnly(results.get(0).request().getId());
            assertThat(repository.findAll()).filteredOn(r -> r.getExternalId().equals("EXT-DUP-RACE")).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void differentContentForSameExternalIdIsRejectedAndNothingElseIsStored() {
        service.create(new CreateActivationRequestCommand("EXT-DUP-DIFF", "CUST-1", "INTERNET-500"));

        var divergent = new CreateActivationRequestCommand("EXT-DUP-DIFF", "CUST-1", "TV-100");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.create(divergent))
                .isInstanceOf(DuplicateActivationRequestException.class);

        assertThat(repository.findByExternalId("EXT-DUP-DIFF")).get()
                .extracting(r -> r.getProductCode()).isEqualTo("INTERNET-500");
    }
}
