package com.example.demo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

// Registra cada petición HTTP en una línea: método, ruta, código de respuesta y duración.
// Le asigna un identificador que se devuelve en la cabecera X-Request-Id y aparece en todas
// las líneas de log de esa petición, para poder seguirla.
// Corre antes que Spring Security, así también quedan registradas las respuestas 401.
// No registra cuerpos, parámetros ni cabeceras: nunca aparecen contraseñas, tokens ni emails.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    // Un identificador enviado por el cliente se acepta solo si es corto y sin caracteres raros:
    // evita líneas de log manipuladas o enormes.
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private static final String HEALTH_PATH = "/actuator/health";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        long start = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
            logRequest(request, response.getStatus(), start);
        } catch (IOException | ServletException | RuntimeException ex) {
            // Un error no controlado termina en 500: se registra con su identificador
            // antes de que el servidor arme la respuesta de error.
            log.error("HTTP {} {} 500 {}ms", request.getMethod(), request.getRequestURI(), elapsedMs(start), ex);
            throw ex;
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    // Las sondas de Azure consultan la salud cada pocos segundos: se registran en DEBUG
    // para que no tapen las peticiones reales. Si fallan, igual quedan en el log.
    private void logRequest(HttpServletRequest request, int status, long start) {
        String path = request.getRequestURI();
        long durationMs = elapsedMs(start);
        if (path.startsWith(HEALTH_PATH) && status < 500) {
            log.debug("HTTP {} {} {} {}ms", request.getMethod(), path, status, durationMs);
        } else {
            log.info("HTTP {} {} {} {}ms", request.getMethod(), path, status, durationMs);
        }
    }

    static String resolveRequestId(String received) {
        if (received != null && VALID_REQUEST_ID.matcher(received).matches()) {
            return received;
        }
        return UUID.randomUUID().toString();
    }

    private static long elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
