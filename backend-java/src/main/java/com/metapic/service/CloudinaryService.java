package com.metapic.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

/**
 * Cloudinary integration — replaces the Node services/cloudinary.js module.
 *
 * Uses the Cloudinary Java SDK to upload images from raw byte arrays
 * (Spring's MultipartFile.getBytes()). The Node version streams via
 * streamifier; the Java SDK accepts byte[] natively.
 */
@Service
public class CloudinaryService {

    @Value("${cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${cloudinary.api-key:}")
    private String apiKey;

    @Value("${cloudinary.api-secret:}")
    private String apiSecret;

    private Cloudinary cloudinary;

    @PostConstruct
    public void init() {
        cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret
        ));
    }

    /**
     * Upload raw image bytes to Cloudinary.
     *
     * @param fileBytes  image bytes from MultipartFile.getBytes()
     * @param folder     Cloudinary folder, e.g. "metapic/avatars" or "metapic/123456"
     * @return Cloudinary upload result map containing "secure_url", "public_id", etc.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> upload(byte[] fileBytes, String folder) throws IOException {
        return cloudinary.uploader().upload(fileBytes, ObjectUtils.asMap(
                "folder", folder,
                "resource_type", "image"
        ));
    }
}
