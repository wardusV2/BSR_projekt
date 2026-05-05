package com.webproject.safelogin.controller;

import com.webproject.safelogin.Dto.Login;
import com.webproject.safelogin.model.User;
import com.webproject.safelogin.repository.UserRepository;
import com.webproject.safelogin.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import util.TotpUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class UserController {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserService userService;

    private BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

    private boolean isPasswordValid(String password) {
        // Co najmniej 12 znaków, 1 mała litera, 1 wielka litera, 1 cyfra, 1 znak specjalny
        String regex = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&._-])[A-Za-z\\d@$!%*?&._-]{12,}$";
        return password != null && password.matches(regex);
    }

    @PostMapping("/register")
    public ResponseEntity<Object> register(@RequestBody User newUser) {
        // Sprawdzanie, czy użytkownik o podanym e-mailu już istnieje
        User existingUser = userRepository.findByEmail(newUser.getEmail());
        if (existingUser != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("The user with the specified email address already exists.");
        }

        // Weryfikacja poprawności hasła
        if (!isPasswordValid(newUser.getPassword())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("The password must be at least 12 characters long and contain at least one lowercase letter, one uppercase letter, one digit, and one special character.");
        }

        // Szyfrowanie hasła
        newUser.setPassword(encoder.encode(newUser.getPassword()));

        // Generowanie sekretu TOTP
        String secret = generateMfaSecret(newUser); // Nowa metoda – patrz niżej
        String otpAuthURL = TotpUtil.generateTotpUri("YourApp", newUser.getEmail(), secret);

        // Zapis sekretu do użytkownika
        newUser.setTotpSecret(secret);
        userRepository.save(newUser);

        // Generowanie QR kodu (opcjonalnie – np. jeśli frontend chce base64 obrazka)
        String qrCode = util.TotpUtil.generateQrCode(otpAuthURL);

        // Zwróć odpowiedź z linkiem QR do użytkownika
        Map<String, Object> response = new HashMap<>();
        response.put("message", "User registered successfully. Please scan the QR code with your TOTP application.");
        response.put("otpAuthURL", otpAuthURL);
        response.put("qrCode", qrCode); // base64 PNG
        response.put("secret", secret); // <--- DODAJ TO

        return ResponseEntity.ok(response);

    }


    @PutMapping("/editUser/{id}")
    public ResponseEntity<User> updateUser(@PathVariable Integer id, @RequestBody User updatedUser) {
        try {
            if (updatedUser.getPassword() != null && !isPasswordValid(updatedUser.getPassword())) {
                return ResponseEntity.badRequest().body(null); // Można zwrócić JSON z błędem
            }

            User updatedRecord = userService.updateUser(id, updatedUser);
            return ResponseEntity.ok(updatedRecord);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/deleteUser/{id}")
    public ResponseEntity<String> deleteUser(@PathVariable Integer id) {
        try {
            userService.deleteUser(id);
            return ResponseEntity.ok("User with id " + id + " has been deleted.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build(); // Zwracamy kod 404
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Object> login(@RequestBody Login loginRequest, HttpServletRequest request) {
        if (loginRequest.getEmail() == null || loginRequest.getPassword() == null) {
            return ResponseEntity.badRequest().body("Email and password must be provided");
        }

        User user = userService.findByEmail(loginRequest.getEmail());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Login failed: Invalid credentials");
        }

        if (user.isAccountLocked()) {
            if (userService.unlockIfTimeExpired(user)) {
                // Konto zostało odblokowane – kontynuuj logowanie
            } else {
                return ResponseEntity.status(HttpStatus.LOCKED).body("Account is locked. Try again later.");
            }
        }

        try {
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    loginRequest.getEmail(), loginRequest.getPassword());

            Authentication authentication = authenticationManager.authenticate(authToken);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            HttpSession session = request.getSession(true);
            session.setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());

            // Reset prób logowania po sukcesie
            userService.resetFailedAttempts(user);

            session.setAttribute("2fa_authenticated", false);

            if (user.getTotpSecret() != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("message", "Password correct. Please verify TOTP.");
                response.put("require2FA", true);
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> response = new HashMap<>();
                response.put("message", "Login successful.");
                response.put("require2FA", false);
                return ResponseEntity.ok(response);
            }

        } catch (BadCredentialsException e) {
            userService.increaseFailedAttempts(user);

            if (user.getFailedAttempts() >= UserService.MAX_FAILED_ATTEMPTS) {
                userService.lock(user);
                return ResponseEntity.status(HttpStatus.LOCKED).body("Account locked due to multiple failed attempts.");
            }

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Login failed: Invalid credentials");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred");
        }
    }


    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletRequest request, HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate(); // Usunięcie sesji
        }
        jakarta.servlet.http.Cookie cookie = new
                jakarta.servlet.http.Cookie("JSESSIONID", null);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        return ResponseEntity.ok("Logged out successfully");
    }


    @GetMapping("/users")
    public List<User> getUsers() {
        return null;
    }

    public String generateMfaSecret(User user) {
        String secret = util.TotpUtil.generateSecret();
        user.setTotpSecret(secret);
        return secret;

    }
    @GetMapping("/check-auth")
    public ResponseEntity<?> checkAuth(HttpSession session) {
        Boolean is2fa = (Boolean) session.getAttribute("2fa_authenticated");
        if (is2fa != null && is2fa) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                String email = auth.getName();
                User user = userRepository.findByEmail(email);
                if (user != null) {
                    Map<String, Object> userInfo = new HashMap<>();
                    userInfo.put("id", user.getId());
                    userInfo.put("nick", user.getNick());
                    userInfo.put("email", user.getEmail());
                    userInfo.put("name", user.getName());
                    userInfo.put("surname", user.getSurname());
                    return ResponseEntity.ok(userInfo);
                }
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
    }
    @GetMapping("/csrf-token")
    public ResponseEntity<?> getCsrfToken(HttpServletRequest request) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            return ResponseEntity.ok().body(new CsrfResponse(csrfToken.getToken()));
        } else {
            return ResponseEntity.badRequest().body("CSRF token not available");
        }
    }

    public static class CsrfResponse {
        private String csrfToken;

        public CsrfResponse(String csrfToken) {
            this.csrfToken = csrfToken;
        }

        public String getCsrfToken() {
            return csrfToken;
        }

        public void setCsrfToken(String csrfToken) {
            this.csrfToken = csrfToken;
        }
    }

}