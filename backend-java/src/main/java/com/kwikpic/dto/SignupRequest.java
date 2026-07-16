package com.kwikpic.dto;

/** POST /api/signup — user registration. */
public record SignupRequest(String name, String email, String password) {}
