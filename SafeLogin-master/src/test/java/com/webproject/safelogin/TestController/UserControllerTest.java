package com.webproject.safelogin.TestController;

import com.webproject.safelogin.controller.UserController;
import com.webproject.safelogin.Dto.Login;
import com.webproject.safelogin.model.User;
import com.webproject.safelogin.repository.UserRepository;
import com.webproject.safelogin.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserControllerTest {

    @InjectMocks
    private UserController userController;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserService userService;

    @Mock
    private HttpServletRequest httpServletRequest;

    @Mock
    private HttpServletResponse httpServletResponse;

    @Mock
    private HttpSession httpSession;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testRegisterSuccess() {
        User newUser = new User();
        newUser.setEmail("test@example.com");
        newUser.setPassword("StrongPass123!");

        when(userRepository.findByEmail("test@example.com")).thenReturn(null);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        ResponseEntity<Object> response = userController.register(newUser);

        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().toString().contains("User registered successfully"));
    }

    @Test
    void testRegisterFailsWhenUserExists() {
        User existing = new User();
        existing.setEmail("test@example.com");

        User newUser = new User();
        newUser.setEmail("test@example.com");
        newUser.setPassword("StrongPass123!");

        when(userRepository.findByEmail("test@example.com")).thenReturn(existing);

        ResponseEntity<Object> response = userController.register(newUser);

        assertEquals(400, response.getStatusCodeValue());
        assertTrue(response.getBody().toString().contains("already exists"));
    }

    @Test
    void testRegisterFailsWhenWeakPassword() {
        User newUser = new User();
        newUser.setEmail("test@example.com");
        newUser.setPassword("weak");

        when(userRepository.findByEmail("test@example.com")).thenReturn(null);

        ResponseEntity<Object> response = userController.register(newUser);

        assertEquals(400, response.getStatusCodeValue());
        assertTrue(response.getBody().toString().contains("The password must be at least 12 characters long"));
    }

    @Test
    void testLoginSuccessWithout2FA() {
        Login login = new Login();
        login.setEmail("test@example.com");
        login.setPassword("StrongPass123!");

        User user = new User();
        user.setEmail("test@example.com");
        user.setPassword("encoded");
        user.setTotpSecret(null);

        when(userService.findByEmail("test@example.com")).thenReturn(user);
        when(authenticationManager.authenticate(any())).thenReturn(mock(Authentication.class));
        when(httpServletRequest.getSession(true)).thenReturn(httpSession);

        ResponseEntity<Object> response = userController.login(login, httpServletRequest);

        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().toString().contains("Login successful"));
    }

    @Test
    void testLoginFailsInvalidCredentials() {
        Login login = new Login();
        login.setEmail("test@example.com");
        login.setPassword("wrong");

        User user = new User();
        user.setEmail("test@example.com");
        user.setFailedAttempts(0);

        when(userService.findByEmail("test@example.com")).thenReturn(user);
        doThrow(new BadCredentialsException("Bad credentials")).when(authenticationManager).authenticate(any());

        ResponseEntity<Object> response = userController.login(login, httpServletRequest);

        assertEquals(401, response.getStatusCodeValue());
        assertTrue(response.getBody().toString().contains("Login failed"));
    }

    @Test
    void testUpdateUserSuccess() {
        User updated = new User();
        updated.setId(1);
        updated.setEmail("new@example.com");

        when(userService.updateUser(eq(1), any(User.class))).thenReturn(updated);

        ResponseEntity<User> response = userController.updateUser(1, updated);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals("new@example.com", response.getBody().getEmail());
    }

    @Test
    void testUpdateUserFailsInvalidPassword() {
        User updated = new User();
        updated.setPassword("weak");

        ResponseEntity<User> response = userController.updateUser(1, updated);

        assertEquals(400, response.getStatusCodeValue());
    }

    @Test
    void testDeleteUserSuccess() {
        doNothing().when(userService).deleteUser(1);

        ResponseEntity<String> response = userController.deleteUser(1);

        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().contains("deleted"));
    }

    @Test
    void testDeleteUserNotFound() {
        doThrow(new IllegalArgumentException()).when(userService).deleteUser(1);

        ResponseEntity<String> response = userController.deleteUser(1);

        assertEquals(404, response.getStatusCodeValue());
    }

    @Test
    void testLogout() {
        when(httpServletRequest.getSession(false)).thenReturn(httpSession);

        ResponseEntity<String> response = userController.logout(httpServletRequest, httpServletResponse);

        verify(httpSession, times(1)).invalidate();
        assertEquals(200, response.getStatusCodeValue());
        assertEquals("Logged out successfully", response.getBody());
    }
}
