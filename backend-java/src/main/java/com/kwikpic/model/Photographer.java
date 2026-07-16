package com.kwikpic.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Maps to Mongoose Photographer schema in models/Photographer.js.
 * Mongoose schema uses { timestamps: true }, so createdAt/updatedAt are auto-managed.
 */
@Document(collection = "photographers")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Photographer {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    /** BCrypt-hashed password. Field name is "passwordHash" in Mongoose schema. */
    private String passwordHash;

    private String name;

    private String businessName;

    private String avatarUrl;

    /** Password reset OTP and its expiration time */
    private String resetOtp;
    private Date resetOtpExpiry;

    /** ObjectId hex-strings referencing Group documents. */
    private List<String> groups = new ArrayList<>();

    @CreatedDate
    private Date createdAt;

    @LastModifiedDate
    private Date updatedAt;
}
