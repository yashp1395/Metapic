package com.metapic.dto;

/** POST /api/signup — user registration. */
public record SignupRequest(String name, String email, String password) {}
