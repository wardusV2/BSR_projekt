package com.webproject.safelogin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendCode(String email, String code) {

        SimpleMailMessage message =
                new SimpleMailMessage();

        message.setTo(email);
        message.setSubject(
                "Kod weryfikacyjny WBF Monitor");

        message.setText(
                "Twój kod dostępu: " + code);

        mailSender.send(message);
    }
}