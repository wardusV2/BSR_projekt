package com.webproject.safelogin.service;

import com.webproject.safelogin.model.User;
import com.webproject.safelogin.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class SubscriptionService {
    private final UserRepository userRepository;

    public SubscriptionService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void subscribe(Integer subscriberId, Integer targetId) {
        if (subscriberId.equals(targetId)) throw new IllegalArgumentException("Nie można subskrybować samego siebie");

        User subscriber = userRepository.findById(subscriberId).orElseThrow();
        User target = userRepository.findById(targetId).orElseThrow();

        subscriber.getSubscriptions().add(target);
        userRepository.save(subscriber);
    }

    public void unsubscribe(Integer subscriberId, Integer targetId) {
        User subscriber = userRepository.findById(subscriberId).orElseThrow();
        User target = userRepository.findById(targetId).orElseThrow();

        subscriber.getSubscriptions().remove(target);
        userRepository.save(subscriber);
    }

    public Set<User> getSubscriptions(Integer userId) {
        return userRepository.findById(userId).orElseThrow().getSubscriptions();
    }

    public Set<User> getSubscribers(Integer userId) {
        return userRepository.findById(userId).orElseThrow().getSubscribers();
    }
}
