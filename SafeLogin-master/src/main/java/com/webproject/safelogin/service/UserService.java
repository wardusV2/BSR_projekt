package com.webproject.safelogin.service;

import com.webproject.safelogin.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import com.webproject.safelogin.model.User;

import java.time.LocalDateTime;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    AuthenticationManager authenticationManager;

    public static final int MAX_FAILED_ATTEMPTS = 3;
    public static final int LOCK_TIME_DURATION = 1; // minuty

    private BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
    public User updateUser(Integer id, User updatedUser) {
        User existingUser = userRepository.findById(id).orElseThrow(() ->
                new IllegalArgumentException("User with id " + id + " not found.")
        );

        existingUser.setNick(updatedUser.getNick());
        existingUser.setEmail(updatedUser.getEmail());
        existingUser.setName(updatedUser.getName());
        existingUser.setSurname(updatedUser.getSurname());
        existingUser.setPassword(encoder.encode(updatedUser.getPassword()));

        return userRepository.save(existingUser);
    }
    public void deleteUser(Integer id) {
        User user = userRepository.findById(id).orElseThrow(() ->
                new IllegalArgumentException("User with id " + id + " not found.")
        );

        userRepository.delete(user);
    }
    public User findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
    public User findUserById(Integer id) {
        return userRepository.findById(id).orElse(null);
    }

    public void increaseFailedAttempts(User user) {
        user.setFailedAttempts(user.getFailedAttempts() + 1);
        userRepository.save(user);
    }

    public void resetFailedAttempts(User user) {
        user.setFailedAttempts(0);
        userRepository.save(user);
    }

    public void lock(User user) {
        user.setAccountLocked(true);
        user.setLockTime(LocalDateTime.now());
        userRepository.save(user);
    }
    public boolean unlockIfTimeExpired(User user) {
        if (user.getLockTime() == null) return false;

        LocalDateTime unlockTime = user.getLockTime().plusMinutes(LOCK_TIME_DURATION);
        if (LocalDateTime.now().isAfter(unlockTime)) {
            user.setAccountLocked(false);
            user.setLockTime(null);
            user.setFailedAttempts(0);
            userRepository.save(user);
            return true;
        }
        return false;
    }

}
