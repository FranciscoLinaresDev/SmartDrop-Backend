
package com.smart.drop.iam.application.services;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TwoFactorEmailSenderTest {

    @Test
    void shouldPrepareAndSendVerificationEmail() {

        JavaMailSender mailSender =
                mock(JavaMailSender.class);

        TwoFactorEmailSender sender =
                new TwoFactorEmailSender(
                        mailSender,
                        "no-reply@example.com"
                );

        sender.sendCode(
                "test@example.com",
                "123456"
        );

        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(
                        SimpleMailMessage.class
                );

        verify(mailSender).send(captor.capture());

        SimpleMailMessage message =
                captor.getValue();

        assertEquals(
                "no-reply@example.com",
                message.getFrom()
        );

        assertArrayEquals(
                new String[]{"test@example.com"},
                message.getTo()
        );

        assertEquals(
                "SmartDrop - Verification Code",
                message.getSubject()
        );

        assertTrue(
                message.getText().contains("123456")
        );
    }

    @Test
    void shouldRejectInvalidCode() {

        JavaMailSender mailSender =
                mock(JavaMailSender.class);

        TwoFactorEmailSender sender =
                new TwoFactorEmailSender(
                        mailSender,
                        "no-reply@example.com"
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> sender.sendCode(
                        "test@example.com",
                        "invalid"
                )
        );

        verifyNoInteractions(mailSender);
    }
}
