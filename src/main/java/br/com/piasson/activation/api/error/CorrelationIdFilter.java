package br.com.piasson.activation.api.error;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Garante um identificador de correlação por requisição.
 *
 * <p>Reaproveita o header {@code X-Correlation-Id} se o cliente enviou um valor seguro;
 * caso contrário gera um novo. O valor vai para o MDC (aparece em todas as linhas de log
 * da requisição), volta no header da resposta e é usado como {@code traceId} nos erros.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    /** Evita log injection: só aceita valores curtos e sem caracteres de controle. */
    private static final Pattern SAFE_VALUE = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = resolve(request.getHeader(HEADER));
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static String resolve(String incoming) {
        if (incoming != null && SAFE_VALUE.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** Identificador da requisição corrente, ou {@code null} fora de um contexto de requisição. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
