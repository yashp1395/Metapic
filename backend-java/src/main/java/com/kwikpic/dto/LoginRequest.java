package com.kwikpic.dto;

/**
 * POST /api/login — user login.
 * Node code accepts both name and email: User.findOne({ $or: [{name}, {email}] }).
 * All fields are nullable; the controller uses whichever is provided.
 */
public record LoginRequest(String name, String email, String password) {}
