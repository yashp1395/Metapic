package com.kwikpic.dto;

/** POST /api/photographer/signup */
public record PhotographerSignupRequest(String name, String businessName, String email, String password) {}
