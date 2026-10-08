package com.example.demo.security;
import com.example.demo.service.UserService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserService userService;

    public JwtAuthenticationFilter(JwtService jwtService, UserService userService) {
        this.jwtService = jwtService;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        // Un token inválido no corta la petición: queda sin autenticar y la
        // regla de autorización decide (401 en rutas protegidas).
        try {
            final String email = jwtService.extractEmail(jwt);

            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userService.loadUserByUsername(email);

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } else {
                    rejectToken(request, JwtAuthenticationEntryPoint.INVALID_TOKEN);
                }
            } else if (email == null) {
                rejectToken(request, JwtAuthenticationEntryPoint.INVALID_TOKEN);
            }
        } catch (ExpiredJwtException ex) {
            rejectToken(request, JwtAuthenticationEntryPoint.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException ex) {
            rejectToken(request, JwtAuthenticationEntryPoint.INVALID_TOKEN);
        }

        filterChain.doFilter(request, response);
    }

    private void rejectToken(HttpServletRequest request, String reason) {
        SecurityContextHolder.clearContext();
        request.setAttribute(JwtAuthenticationEntryPoint.AUTH_ERROR_ATTRIBUTE, reason);
    }
}
