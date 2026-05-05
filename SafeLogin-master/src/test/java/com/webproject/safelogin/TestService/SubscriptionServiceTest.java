package com.webproject.safelogin.TestService;

import com.webproject.safelogin.service.SubscriptionService;
import com.webproject.safelogin.model.User;
import com.webproject.safelogin.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubscriptionServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private User subscriber;
    private User target;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        subscriber = new User();
        subscriber.setId(1);
        subscriber.setNick("subscriber");
        subscriber.setSubscriptions(new HashSet<>());
        subscriber.setSubscribers(new HashSet<>());

        target = new User();
        target.setId(2);
        target.setNick("target");
        target.setSubscriptions(new HashSet<>());
        target.setSubscribers(new HashSet<>());
    }

    @Test
    void subscribe_ShouldAddSubscription() {
        when(userRepository.findById(1)).thenReturn(Optional.of(subscriber));
        when(userRepository.findById(2)).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        subscriptionService.subscribe(1, 2);

        assertTrue(subscriber.getSubscriptions().contains(target));
        verify(userRepository).save(subscriber);
    }

    @Test
    void subscribe_WhenSubscribingSelf_ShouldThrow() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> subscriptionService.subscribe(1, 1));
        assertEquals("Nie można subskrybować samego siebie", ex.getMessage());
    }

    @Test
    void subscribe_WhenSubscriberNotFound_ShouldThrow() {
        when(userRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class,
                () -> subscriptionService.subscribe(1, 2));
    }

    @Test
    void subscribe_WhenTargetNotFound_ShouldThrow() {
        when(userRepository.findById(1)).thenReturn(Optional.of(subscriber));
        when(userRepository.findById(2)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class,
                () -> subscriptionService.subscribe(1, 2));
    }

    @Test
    void unsubscribe_ShouldRemoveSubscription() {
        subscriber.getSubscriptions().add(target);

        when(userRepository.findById(1)).thenReturn(Optional.of(subscriber));
        when(userRepository.findById(2)).thenReturn(Optional.of(target));

        subscriptionService.unsubscribe(1, 2);

        assertFalse(subscriber.getSubscriptions().contains(target));
        verify(userRepository).save(subscriber);
    }

    @Test
    void unsubscribe_WhenSubscriberNotFound_ShouldThrow() {
        when(userRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class,
                () -> subscriptionService.unsubscribe(1, 2));
    }

    @Test
    void unsubscribe_WhenTargetNotFound_ShouldThrow() {
        when(userRepository.findById(1)).thenReturn(Optional.of(subscriber));
        when(userRepository.findById(2)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class,
                () -> subscriptionService.unsubscribe(1, 2));
    }

    @Test
    void getSubscriptions_ShouldReturnSubscriptions() {
        subscriber.getSubscriptions().add(target);

        when(userRepository.findById(1)).thenReturn(Optional.of(subscriber));

        Set<User> result = subscriptionService.getSubscriptions(1);

        assertEquals(1, result.size());
        assertTrue(result.contains(target));
    }

    @Test
    void getSubscriptions_WhenUserNotFound_ShouldThrow() {
        when(userRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class,
                () -> subscriptionService.getSubscriptions(1));
    }

    @Test
    void getSubscribers_ShouldReturnSubscribers() {
        subscriber.getSubscribers().add(target);

        when(userRepository.findById(1)).thenReturn(Optional.of(subscriber));

        Set<User> result = subscriptionService.getSubscribers(1);

        assertEquals(1, result.size());
        assertTrue(result.contains(target));
    }

    @Test
    void getSubscribers_WhenUserNotFound_ShouldThrow() {
        when(userRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class,
                () -> subscriptionService.getSubscribers(1));
    }
}
