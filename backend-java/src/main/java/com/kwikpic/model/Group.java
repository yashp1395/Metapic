package com.kwikpic.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Maps to Mongoose Group schema in models/Group.js.
 * Central hub connecting Photographer → Photos → Participants.
 */
@Document(collection = "groups")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Group {

    @Id
    private String id;

    private String name;

    /** Unique 6-digit event code clients use to join. */
    @Indexed(unique = true)
    private String code;

    /** ObjectId hex-string referencing the owning Photographer. */
    private String photographer;

    /** ObjectId hex-strings referencing Photo documents. */
    private List<String> photos = new ArrayList<>();

    /** ObjectId hex-strings referencing User (participant) documents. */
    private List<String> participants = new ArrayList<>();

    private Date createdAt;
}
