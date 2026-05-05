package com.webproject.safelogin.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private UserDetailsService userDetailsService;

    private static final String SERVICE_API_KEY = "SUPER_SECRET_SERVICE_KEY_123";

    /* =========================================================
        API – /api/** (STATELESS + X-SERVICE-KEY)
       ========================================================= */
    @Bean
    @Order(1)
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {

        http
                .securityMatcher("/api/**","/recommendations/**","/api/users/**",
                        "/subscriber/**",
                        "/getSubscriptions/**",
                        "/videosByUser/**",
                        "/users/**",
                        "/AllVideos/**")

                .csrf(csrf -> csrf.disable())

                .cors(Customizer.withDefaults())

                .authorizeHttpRequests(auth -> auth
                        .anyRequest().access((authentication, context) ->
                                SERVICE_API_KEY.equals(
                                        context.getRequest().getHeader("X-SERVICE-KEY")
                                )
                                        ? new AuthorizationDecision(true)
                                        : new AuthorizationDecision(false)
                        )
                )

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(
                                (req, res, e) ->
                                        res.sendError(HttpServletResponse.SC_UNAUTHORIZED)
                        )
                );

        return http.build();
    }


    /* =========================================================
       2️⃣ FRONTEND – Twoja konfiguracja
       ========================================================= */
    @Bean
    @Order(2)
    SecurityFilterChain frontendSecurity(HttpSecurity http) throws Exception {

        return http

                .securityMatcher("/**") // wszystko poza /api/**

                .csrf(csrf -> csrf
                        .csrfTokenRepository(
                                CookieCsrfTokenRepository.withHttpOnlyFalse()
                        )
                        .ignoringRequestMatchers(
                                "/ws/**",
                                "/ws",
                                "/ws/info",
                                "/ws/websocket",
                                "/api/history/**",
                                "/recommendations/**"
                        )
                )

                .cors(cors ->
                        cors.configurationSource(corsConfigurationSource())
                )

                .authorizeHttpRequests(request -> request
                        .requestMatchers(
                                "/login",
                                "/register",
                                "/logout",
                                "/csrf-token",

                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",

                                "/getVideo/**",
                                "/2fa/verify",

                                // WebSocket / SockJS
                                "/ws/**",
                                "/ws",
                                "/ws/info",
                                "/ws/websocket",
                                "/ws/xhr_streaming",
                                "/ws/xhr_send",
                                "/ws/xhr",
                                "/ws/jsonp",
                                "/ws/jsonp_send",
                                "/ws/eventsource",
                                "/ws/htmlfile",
                                "/sockjs-node/**",
                                "/api/history/**",
                                "/recommendations/**"
                        ).permitAll()

                        .anyRequest().authenticated()
                )

                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler((request, response, auth) ->
                                response.setStatus(200)
                        )
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )

                .formLogin().disable()

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.ALWAYS
                        )
                )

                .build();
    }


    /* =========================================================
       3️⃣ CORS
       ========================================================= */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(
                List.of("http://localhost:5173")
        );

        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")
        );

        configuration.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "X-XSRF-TOKEN", "X-SERVICE-KEY")
        );

        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }


    /* =========================================================
       4️⃣ AUTH
       ========================================================= */

    @Bean
    public AuthenticationProvider authenticationProvider() {

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider();

        provider.setPasswordEncoder(
                new BCryptPasswordEncoder(10)
        );

        provider.setUserDetailsService(userDetailsService);

        return provider;
    }


    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config
    ) throws Exception {

        return config.getAuthenticationManager();
    }
}


