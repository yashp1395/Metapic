package com.metapic.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendPasswordResetOtp(String toEmail, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("noreply@metapic.com"); // Configured default sender
            message.setTo(toEmail);
            message.setSubject("Password Reset Request - Metapic");
            message.setText("You have requested to reset your password.\n\n" +
                    "Your One-Time Password (OTP) is: " + otp + "\n\n" +
                    "This OTP is valid for 15 minutes. If you did not request a password reset, please ignore this email.");

            mailSender.send(message);
            log.info("Sent password reset OTP to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Failed to send email");
        }
    }
}
