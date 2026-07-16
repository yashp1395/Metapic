package com.kwikpic.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Maps to Mongoose Photo schema in models/Photo.js.
 *
 * The embeddings field is List&lt;List&lt;Double&gt;&gt; — each inner list is a
 * 512-float vector representing one face found in the photo.
 * Mongoose schema: embeddings: { type: [[Number]], default: [] }
 */
@Document(collection = "photos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Photo {

    @Id
    private String id;

    private String url;

    /** Cloudinary public ID for management/deletion. */
    private String publicId;

    /** ObjectId hex-string referencing Group. */
    private String group;

    /** ObjectId hex-string referencing Photographer (uploader). */
    private String uploader;

    private String filename;

    /**
     * Core AI field. Each inner List is a 512-float embedding vector for one face.
     * A photo with 3 people yields 3 inner lists.
     */
    private List<List<Double>> embeddings = new ArrayList<>();

    private Date createdAt;
}
