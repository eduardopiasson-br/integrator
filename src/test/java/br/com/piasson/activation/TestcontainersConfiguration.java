package br.com.piasson.activation;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Sobe um PostgreSQL real para os testes de integração. {@code @ServiceConnection}
 * injeta url/usuário/senha no DataSource automaticamente. O container é
 * compartilhado entre os testes do mesmo JVM (contexto cacheado pelo Spring).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }
}
