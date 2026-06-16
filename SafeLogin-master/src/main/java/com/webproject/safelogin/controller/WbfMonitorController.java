package com.webproject.safelogin.controller;

import com.webproject.safelogin.model.User;
import com.webproject.safelogin.service.EmailService;
import com.webproject.safelogin.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

@RestController
public class WbfMonitorController {

    @Autowired
    private UserService userService;

    @Autowired
    private EmailService emailService;

    @GetMapping("/wbfmonitor/access")
    public ResponseEntity<?> checkAccess(
            HttpServletRequest request,
            Principal principal) {

        HttpSession session = request.getSession();

        Boolean verified =
                (Boolean) session.getAttribute("wbf_verified");

        if(Boolean.TRUE.equals(verified)) {
            return ResponseEntity.ok(true);
        }

        User user = userService.findByEmail(principal.getName());

        String code = String.valueOf(
                ThreadLocalRandom.current()
                        .nextInt(100000, 999999));

        session.setAttribute("wbf_code", code);
        session.setAttribute(
                "wbf_code_expiration",
                LocalDateTime.now().plusMinutes(5));

        emailService.sendCode(user.getEmail(), code);

        return ResponseEntity.ok(false);
    }
}