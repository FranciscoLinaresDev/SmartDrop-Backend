
package com.smart.drop.iam.application.services;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

public class TwoFactorEmailSender implements TwoFactorCodeSender {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public TwoFactorEmailSender(
            JavaMailSender mailSender,
            String fromAddress) {

        if (mailSender == null) {
            throw new IllegalArgumentException(
                    "Mail sender is required"
            );
        }

        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalArgumentException(
                    "Sender address is required"
            );
        }

        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendCode(String email, String code) {

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException(
                    "Recipient email is required"
            );
        }

        if (code == null || !code.matches("[0-9]{6}")) {
            throw new IllegalArgumentException(
                    "Invalid verification code"
            );
        }

        SimpleMailMessage message =
                new SimpleMailMessage();

        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("SmartDrop - Verification Code");

        message.setText(
                "Your SmartDrop verification code is: "
                + code
                + "\n\nThis code expires in 5 minutes."
                + "\n\nIf you did not request this code, "
                + "please ignore this email."
        );

        mailSender.send(message);
    }
}
