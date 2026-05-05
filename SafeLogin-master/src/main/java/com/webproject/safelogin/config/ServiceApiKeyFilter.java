package com.webproject.safelogin.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class ServiceApiKeyFilter extends OncePerRequestFilter {

    @Value("${security.service-api-key}")
    private String serviceApiKey;

    private static final String HEADER = "X-SERVICE-KEY";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        if (request.getRequestURI().startsWith("/api/history")) {

            String key = request.getHeader(HEADER);

            if (!serviceApiKey.equals(key)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }


            var auth = new UsernamePasswordAuthenticationToken(
                    "SERVICE_1",
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_SERVICE"))
            );

            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}