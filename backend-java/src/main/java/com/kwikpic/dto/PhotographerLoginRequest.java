package com.kwikpic.dto;

/** POST /api/photographer/login */
public record PhotographerLoginRequest(String email, String password) {}
