package com.kwikpic.dto;

public record ResetPasswordRequest(String email, String otp, String newPassword) {}
