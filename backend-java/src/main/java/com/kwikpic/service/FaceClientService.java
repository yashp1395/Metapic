package com.kwikpic.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * HTTP client for the Python face-service (FastAPI / InsightFace).
 *
 * Replaces the Node services/faceClient.js module.
 *
 * <p>Face-service API contract (from face-service/app/main.py):
 * <ul>
 *   <li>{@code POST /embed}  — multipart file → {@code {embedding, embeddings, face_count}}</li>
 *   <li>{@code POST /embed-urls} — JSON {@code {urls: [...]}} → {@code {items: [{url, embedding, embeddings, face_count}]}}</li>
 * </ul>
 */
@Service
public class FaceClientService {

    private static final Logger log = LoggerFactory.getLogger(FaceClientService.class);

    @Value("${face.service.url:http://localhost:8000}")
    private String faceServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    // ─── /embed (single file) ───────────────────────────────────

    /**
     * Send a single image to face-service and get back face embeddings.
     *
     * @return map with keys: "embedding" (List), "embeddings" (List of Lists), "face_count" (int)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> computeEmbeddingFromBuffer(byte[] buffer, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        // ByteArrayResource that reports a filename — required by face-service
        ByteArrayResource resource = new ByteArrayResource(buffer) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    faceServiceUrl + "/embed",
                    HttpMethod.POST,
                    requestEntity,
                    Map.class
            );
            Map<String, Object> data = response.getBody();
            return normalizeEmbeddingResponse(data);
        } catch (Exception e) {
            String detail = e.getMessage();
            throw new RuntimeException(
                    "Face service unreachable at " + faceServiceUrl + "/embed (" + detail + ")");
        }
    }

    // ─── /embed-urls (bulk by URL) ──────────────────────────────

    /**
     * Send a list of image URLs to face-service for bulk embedding.
     *
     * @return map with key "items" — list of {url, embedding, embeddings, face_count}
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> computeBulkEmbeddingsFromUrls(List<String> urls) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = Map.of("urls", urls);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    faceServiceUrl + "/embed-urls",
                    HttpMethod.POST,
                    request,
                    Map.class
            );
            return response.getBody();
        } catch (Exception e) {
            String detail = e.getMessage();
            throw new RuntimeException(
                    "Face service unreachable at " + faceServiceUrl + "/embed-urls (" + detail + ")");
        }
    }

    // ─── Cosine similarity ──────────────────────────────────────

    /**
     * Compute cosine similarity between two embedding vectors.
     * Direct translation of the Node faceClient.cosine() function.
     */
    public double cosineSimilarity(List<Double> a, List<Double> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return 0.0;
        int len = Math.min(a.size(), b.size());
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < len; i++) {
            double ai = a.get(i);
            double bi = b.get(i);
            dot += ai * bi;
            normA += ai * ai;
            normB += bi * bi;
        }
        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // ─── Helpers ────────────────────────────────────────────────

    /**
     * Normalize the face-service response into a consistent shape.
     * Mirrors Node's normalizeEmbeddings() utility.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeEmbeddingResponse(Map<String, Object> data) {
        if (data == null) {
            data = new HashMap<>();
        }

        List<List<Double>> embeddings = new ArrayList<>();

        // Try "embeddings" key first (list of lists)
        Object embeddingsRaw = data.get("embeddings");
        if (embeddingsRaw instanceof List<?> list && !list.isEmpty()) {
            for (Object item : list) {
                if (item instanceof List<?> inner && !inner.isEmpty()) {
                    List<Double> vec = new ArrayList<>();
                    for (Object num : inner) {
                        vec.add(((Number) num).doubleValue());
                    }
                    embeddings.add(vec);
                }
            }
        }

        // Fallback to singular "embedding" key
        if (embeddings.isEmpty()) {
            Object embeddingRaw = data.get("embedding");
            if (embeddingRaw instanceof List<?> list && !list.isEmpty()) {
                List<Double> vec = new ArrayList<>();
                for (Object num : list) {
                    vec.add(((Number) num).doubleValue());
                }
                embeddings.add(vec);
            }
        }

        Map<String, Object> result = new HashMap<>(data);
        result.put("embeddings", embeddings);
        result.put("embedding", embeddings.isEmpty() ? List.of() : embeddings.get(0));
        return result;
    }
}
