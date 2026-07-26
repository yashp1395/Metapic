package com.metapic.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps to Mongoose User schema in models/user.js.
 * Collection: "users" (Mongoose default pluralisation).
 */
@Document(collection = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String name;

    private String email;

    /** BCrypt-hashed password (stored as "password" in Mongoose schema). */
    private String password;

    private String selfieUrl;

    /** Password reset OTP and its expiration time */
    private String resetOtp;
    private java.util.Date resetOtpExpiry;

    /** ObjectId hex-strings referencing Group documents. */
    private List<String> joinedGroups = new ArrayList<>();
}
