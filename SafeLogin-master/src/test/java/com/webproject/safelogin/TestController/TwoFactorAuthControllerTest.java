package com.webproject.safelogin.TestController;

import com.webproject.safelogin.controller.TwoFactorAuthController;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.webproject.safelogin.model.TotpRequest;
import com.webproject.safelogin.model.User;
import com.webproject.safelogin.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import com.warrenstrange.googleauth.GoogleAuthenticator;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@WebMvcTest(TwoFactorAuthController.class)
class TwoFactorAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        // Ustawienie kontekstu bezpieczeństwa
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new TestingAuthenticationToken("test@example.com", null, "ROLE_USER"));
        SecurityContextHolder.setContext(context);
    }

    @Test
    void testVerifyTotp_Success() throws Exception {
        User user = new User();
        user.setTotpSecret("secret");
        user.setFailedAttempts(0);

        when(userService.findByEmail("test@example.com")).thenReturn(user);

        // Tworzymy instancję GoogleAuthenticator i podmieniamy metodę authorize za pomocą spy
        GoogleAuthenticator gAuth = Mockito.spy(new GoogleAuthenticator());
        doReturn(true).when(gAuth).authorize(eq("secret"), eq(123456));

        // Podmień w kontrolerze sposób tworzenia GoogleAuthenticator - ale to wymaga zmiany w kontrolerze
        // Dlatego w testach wykonujemy bez mockowania konstruktora - zamiast tego wywołujemy kontroler ręcznie
        // albo zmieniamy kod kontrolera by wstrzykiwać GoogleAuthenticator

        TotpRequest request = new TotpRequest(123456);

        mockMvc.perform(post("/2fa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))  // <--- dodaj to
                .andExpect(status().isOk())
                .andExpect(content().string("TOTP verified. Fully logged in."));

        verify(userService).resetFailedAttempts(user);
    }

    @Test
    void testVerifyTotp_InvalidCode() throws Exception {
        User user = new User();
        user.setTotpSecret("secret");
        user.setFailedAttempts(1);

        when(userService.findByEmail("test@example.com")).thenReturn(user);

        try (var mocked = Mockito.mockStatic(GoogleAuthenticator.class)) {
            GoogleAuthenticator mockAuth = mock(GoogleAuthenticator.class);
            mocked.when(GoogleAuthenticator::new).thenReturn(mockAuth);
            when(mockAuth.authorize(anyString(), anyInt())).thenReturn(false);

            TotpRequest request = new TotpRequest();
            request.setCode(111111);

            mockMvc.perform(post("/2fa/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().string("Invalid TOTP code"));

            verify(userService).increaseFailedAttempts(user);
        }
    }

    @Test
    void testVerifyTotp_AccountLocked() throws Exception {
        User user = new User();
        user.setAccountLocked(true);

        when(userService.findByEmail("test@example.com")).thenReturn(user);
        when(userService.unlockIfTimeExpired(user)).thenReturn(false);

        TotpRequest request = new TotpRequest();
        request.setCode(123456);

        mockMvc.perform(post("/2fa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isLocked())
                .andExpect(content().string("Account is locked. Try again later."));
    }

    @Test
    void testVerifyTotp_AccountLockedAfterMaxAttempts() throws Exception {
        User user = new User();
        user.setTotpSecret("secret");
        user.setFailedAttempts(UserService.MAX_FAILED_ATTEMPTS - 1);

        when(userService.findByEmail("test@example.com")).thenReturn(user);

        try (var mocked = Mockito.mockStatic(GoogleAuthenticator.class)) {
            GoogleAuthenticator mockAuth = mock(GoogleAuthenticator.class);
            mocked.when(GoogleAuthenticator::new).thenReturn(mockAuth);
            when(mockAuth.authorize(anyString(), anyInt())).thenReturn(false);

            TotpRequest request = new TotpRequest();
            request.setCode(999999);

            mockMvc.perform(post("/2fa/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isLocked())
                    .andExpect(content().string("Account locked due to multiple failed TOTP attempts."));

            verify(userService).lock(user);
        }
    }

    @Test
    void testVerifyTotp_UserNotFound() throws Exception {
        when(userService.findByEmail("test@example.com")).thenReturn(null);

        TotpRequest request = new TotpRequest();
        request.setCode(123456);

        mockMvc.perform(post("/2fa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("User not found"));
    }

    @Test
    void testVerifyTotp_NotAuthenticated() throws Exception {
        SecurityContextHolder.clearContext();

        TotpRequest request = new TotpRequest();
        request.setCode(123456);

        mockMvc.perform(post("/2fa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Not authenticated"));
    }
}
