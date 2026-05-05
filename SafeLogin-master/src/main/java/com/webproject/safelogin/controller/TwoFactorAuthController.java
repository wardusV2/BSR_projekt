package com.webproject.safelogin.controller;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.webproject.safelogin.model.TotpRequest;
import com.webproject.safelogin.model.User;
import com.webproject.safelogin.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
public class TwoFactorAuthController {
    @Autowired
    private UserService userService;

    @PostMapping("/2fa/verify")
    public ResponseEntity<?> verifyTotp(@RequestBody TotpRequest request, HttpServletRequest httpRequest) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }

        String email = auth.getName();
        User user = userService.findByEmail(email);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not found");
        }

        if (user.isAccountLocked()) {
            if (userService.unlockIfTimeExpired(user)) {
                // Konto zostało odblokowane – kontynuuj weryfikację
            } else {
                return ResponseEntity.status(HttpStatus.LOCKED).body("Account is locked. Try again later.");
            }
        }

        GoogleAuthenticator gAuth = new GoogleAuthenticator();
        boolean isCodeValid = gAuth.authorize(user.getTotpSecret(), request.getCode());

        if (isCodeValid) {
            HttpSession session = httpRequest.getSession(false);
            if (session != null) {
                session.setAttribute("2fa_authenticated", true);
            }

            userService.resetFailedAttempts(user); // Reset po sukcesie
            return ResponseEntity.ok("TOTP verified. Fully logged in.");
        } else {
            userService.increaseFailedAttempts(user);

            if (user.getFailedAttempts() >= UserService.MAX_FAILED_ATTEMPTS) {
                userService.lock(user);
                return ResponseEntity.status(HttpStatus.LOCKED).body("Account locked due to multiple failed TOTP attempts.");
            }

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid TOTP code");
        }
    }

}
