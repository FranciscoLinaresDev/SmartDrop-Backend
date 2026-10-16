package com.smart.drop.iam.application.services;

public interface TwoFactorCodeSender {
    void sendCode(String email, String code);
}