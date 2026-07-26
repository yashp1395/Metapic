package com.metapic.dto;

/** POST /api/photographer/login */
public record PhotographerLoginRequest(String email, String password) {}
