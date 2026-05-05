package com.webproject.safelogin.TestService;

import com.webproject.safelogin.service.UserService;
import com.webproject.safelogin.model.User;
import com.webproject.safelogin.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        user = new User();
        user.setId(1);
        user.setNick("nick");
        user.setEmail("test@example.com");
        user.setName("Test");
        user.setSurname("User");
        user.setPassword("password");
        user.setFailedAttempts(0);
        user.setAccountLocked(false);
    }

    @Test
    void updateUser_ShouldUpdateAndEncodePassword() {
        User updatedUser = new User();
        updatedUser.setNick("newNick");
        updatedUser.setEmail("new@example.com");
        updatedUser.setName("New");
        updatedUser.setSurname("Name");
        updatedUser.setPassword("newPassword");

        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.updateUser(1, updatedUser);

        assertEquals("newNick", result.getNick());
        assertEquals("new@example.com", result.getEmail());
        assertEquals("New", result.getName());
        assertEquals("Name", result.getSurname());
        assertTrue(new BCryptPasswordEncoder(10).matches("newPassword", result.getPassword()));

        verify(userRepository).save(any(User.class));
    }

    @Test
    void updateUser_WhenNotFound_ShouldThrow() {
        when(userRepository.findById(999)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.updateUser(999, new User()));

        assertEquals("User with id 999 not found.", ex.getMessage());
    }

    @Test
    void deleteUser_ShouldDeleteUser() {
        when(userRepository.findById(1)).thenReturn(Optional.of(user));

        userService.deleteUser(1);

        verify(userRepository).delete(user);
    }

    @Test
    void deleteUser_WhenNotFound_ShouldThrow() {
        when(userRepository.findById(999)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.deleteUser(999));

        assertEquals("User with id 999 not found.", ex.getMessage());
    }

    @Test
    void increaseFailedAttempts_ShouldIncrementAndSave() {
        user.setFailedAttempts(1);

        userService.increaseFailedAttempts(user);

        assertEquals(2, user.getFailedAttempts());
        verify(userRepository).save(user);
    }

    @Test
    void resetFailedAttempts_ShouldResetAndSave() {
        user.setFailedAttempts(5);

        userService.resetFailedAttempts(user);

        assertEquals(0, user.getFailedAttempts());
        verify(userRepository).save(user);
    }

    @Test
    void lock_ShouldSetLockedAndLockTime() {
        userService.lock(user);

        assertTrue(user.isAccountLocked());
        assertNotNull(user.getLockTime());
        verify(userRepository).save(user);
    }

    @Test
    void unlockIfTimeExpired_WhenLockTimeNull_ShouldReturnFalse() {
        user.setLockTime(null);

        boolean result = userService.unlockIfTimeExpired(user);

        assertFalse(result);
        verify(userRepository, never()).save(any());
    }

    @Test
    void unlockIfTimeExpired_WhenTimeNotExpired_ShouldReturnFalse() {
        user.setLockTime(LocalDateTime.now().plusMinutes(-0)); // teraz

        boolean result = userService.unlockIfTimeExpired(user);

        assertFalse(result);
        verify(userRepository, never()).save(any());
    }

    @Test
    void unlockIfTimeExpired_WhenTimeExpired_ShouldUnlockAndReset() {
        user.setLockTime(LocalDateTime.now().minusMinutes(2)); // minęło więcej niż LOCK_TIME_DURATION

        boolean result = userService.unlockIfTimeExpired(user);

        assertTrue(result);
        assertFalse(user.isAccountLocked());
        assertNull(user.getLockTime());
        assertEquals(0, user.getFailedAttempts());
        verify(userRepository).save(user);
    }

    @Test
    void findByEmail_ShouldReturnUser() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(user);

        User result = userService.findByEmail("test@example.com");

        assertEquals(user, result);
    }

    @Test
    void findUserById_ShouldReturnUserOrNull() {
        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        User found = userService.findUserById(1);
        assertEquals(user, found);

        when(userRepository.findById(2)).thenReturn(Optional.empty());
        User notFound = userService.findUserById(2);
        assertNull(notFound);
    }
}
