package com.kwikpic.controller;

import com.kwikpic.model.Group;
import com.kwikpic.model.Photo;
import com.kwikpic.model.User;
import com.kwikpic.repository.GroupRepository;
import com.kwikpic.repository.PhotoRepository;
import com.kwikpic.repository.UserRepository;
import com.kwikpic.service.AuthResolverService;
import com.kwikpic.service.CloudinaryService;
import com.kwikpic.service.FaceClientService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
//import java.util.stream.Collectors;

/**
 * User endpoints — all require userJwt.
 *
 * <p>Covers:
 * <ul>
 *   <li>GET  /api/user/my-groups</li>
 *   <li>GET  /api/user/group/{code}</li>
 *   <li>POST /api/user/group/{code}/search</li>
 *   <li>POST /api/user/upload-avatar</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    @Value("${match.threshold:0.38}")
    private double matchThreshold;

    private final AuthResolverService authResolver;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final PhotoRepository photoRepository;
    private final CloudinaryService cloudinaryService;
    private final FaceClientService faceClientService;

    public UserController(AuthResolverService authResolver,
                          UserRepository userRepository,
                          GroupRepository groupRepository,
                          PhotoRepository photoRepository,
                          CloudinaryService cloudinaryService,
                          FaceClientService faceClientService) {
        this.authResolver = authResolver;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.photoRepository = photoRepository;
        this.cloudinaryService = cloudinaryService;
        this.faceClientService = faceClientService;
    }

    // ─── 1. GET /my-groups ────────────────────────────────────────

    /**
     * Populate replacement: fetches user, then fetches all joined groups,
     * then counts photos per group.
     */
    @GetMapping("/my-groups")
    public ResponseEntity<?> getMyGroups(HttpServletRequest request) {
        try {
            User user = authResolver.resolveUser(request);

            List<String> joinedGroupIds = user.getJoinedGroups();
            if (joinedGroupIds == null || joinedGroupIds.isEmpty()) {
                return ResponseEntity.ok(List.of());
            }

            List<Group> groups = (List<Group>) groupRepository.findAllById(joinedGroupIds);

            List<Map<String, Object>> formattedGroups = new ArrayList<>();
            for (Group group : groups) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", group.getId());
                m.put("name", group.getName());
                m.put("code", group.getCode());

                List<String> photoIds = group.getPhotos();
                int photoCount = photoIds != null ? photoIds.size() : 0;
                m.put("photoCount", photoCount);

                // Fetch first photo as cover if it exists
                String coverPhotoUrl = null;
                if (photoIds != null && !photoIds.isEmpty()) {
                    var firstOpt = photoRepository.findById(photoIds.get(0));
                    if (firstOpt.isPresent()) {
                        coverPhotoUrl = firstOpt.get().getUrl();
                    }
                }
                m.put("coverPhoto", coverPhotoUrl);

                formattedGroups.add(m);
            }

            return ResponseEntity.ok(formattedGroups);
        } catch (Exception e) {
            log.error("Fetch User Groups Error:", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // ─── 2. GET /group/{code} ─────────────────────────────────────

    @GetMapping("/group/{code}")
    public ResponseEntity<?> getGroup(@PathVariable String code, HttpServletRequest request) {
        try {
            User user = authResolver.resolveUser(request);

            var optGroup = groupRepository.findByCode(code);
            if (optGroup.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Group code does not exist"));
            }
            Group group = optGroup.get();

            // Ensure user is a participant
            boolean isMember = group.getParticipants() != null && group.getParticipants().contains(user.getId());
            if (!isMember) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Access Denied: You must join this group first."));
            }

            // Populate photos
            List<Photo> photos = group.getPhotos() != null && !group.getPhotos().isEmpty()
                    ? photoRepository.findAllById(group.getPhotos())
                    : List.of();

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("_id", group.getId());
            body.put("name", group.getName());
            body.put("code", group.getCode());
            body.put("photos", photos);

            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("Group View Error:", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // ─── 3. POST /group/{code}/search ─────────────────────────────

    @PostMapping("/group/{code}/search")
    public ResponseEntity<?> searchPhotos(@PathVariable String code,
                                          @RequestParam("selfie") MultipartFile file,
                                          HttpServletRequest request) {
        try {
            User user = authResolver.resolveUser(request);

            var optGroup = groupRepository.findByCode(code);
            if (optGroup.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Group not found"));
            }
            Group group = optGroup.get();

            // Security check: is user a participant?
            boolean isMember = group.getParticipants() != null && group.getParticipants().contains(user.getId());
            if (!isMember) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "You must join this group to search photos."));
            }

            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Selfie is required"));
            }

            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Only images allowed"));
            }

            // 1. Get selfie embedding
            byte[] fileBytes = file.getBytes();
            Map<String, Object> embedResp = faceClientService.computeEmbeddingFromBuffer(fileBytes, file.getOriginalFilename());
            
            @SuppressWarnings("unchecked")
            List<Double> selfieEmbedding = (List<Double>) embedResp.get("embedding");

            if (selfieEmbedding == null || selfieEmbedding.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "No face detected in selfie."));
            }

            // Populate photos
            List<Photo> photos = group.getPhotos() != null && !group.getPhotos().isEmpty()
                    ? photoRepository.findAllById(group.getPhotos())
                    : new ArrayList<>();

            // 2. Identify missing embeddings and backfill if necessary
            List<Photo> missingEmbeddings = photos.stream()
                    .filter(p -> p.getEmbeddings() == null || p.getEmbeddings().isEmpty())
                    .filter(p -> p.getUrl() != null && !p.getUrl().isBlank())
                    .toList();

            if (!missingEmbeddings.isEmpty()) {
                try {
                    List<String> urls = missingEmbeddings.stream().map(Photo::getUrl).toList();
                    Map<String, Object> bulkResp = faceClientService.computeBulkEmbeddingsFromUrls(urls);
                    
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> items = (List<Map<String, Object>>) bulkResp.get("items");
                    
                    if (items != null) {
                        Map<String, List<List<Double>>> byUrl = new HashMap<>();
                        for (Map<String, Object> item : items) {
                            String url = (String) item.get("url");
                            @SuppressWarnings("unchecked")
                            List<List<Double>> embs = (List<List<Double>>) item.get("embeddings");
                            byUrl.put(url, embs);
                        }

                        for (Photo p : missingEmbeddings) {
                            List<List<Double>> embs = byUrl.get(p.getUrl());
                            if (embs != null && !embs.isEmpty()) {
                                p.setEmbeddings(embs);
                                photoRepository.save(p); // update db
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Embedding backfill skipped: {}", e.getMessage());
                }
            }

            // 3. Match photos
            List<Map<String, Object>> matches = buildMatches(photos, selfieEmbedding);

            // 4. Force refresh if 0 matches (mirroring Node logic)
            if (matches.isEmpty()) {
                List<Photo> photosWithUrls = photos.stream()
                        .filter(p -> p.getUrl() != null && !p.getUrl().isBlank())
                        .toList();
                        
                if (!photosWithUrls.isEmpty()) {
                    try {
                        List<String> urls = photosWithUrls.stream().map(Photo::getUrl).toList();
                        Map<String, Object> bulkResp = faceClientService.computeBulkEmbeddingsFromUrls(urls);
                        
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> items = (List<Map<String, Object>>) bulkResp.get("items");
                        
                        if (items != null) {
                            Map<String, List<List<Double>>> byUrl = new HashMap<>();
                            for (Map<String, Object> item : items) {
                                String url = (String) item.get("url");
                                @SuppressWarnings("unchecked")
                                List<List<Double>> embs = (List<List<Double>>) item.get("embeddings");
                                byUrl.put(url, embs);
                            }
    
                            for (Photo p : photosWithUrls) {
                                List<List<Double>> embs = byUrl.get(p.getUrl());
                                if (embs != null && !embs.isEmpty()) {
                                    p.setEmbeddings(embs);
                                    photoRepository.save(p);
                                }
                            }
                            matches = buildMatches(photos, selfieEmbedding);
                        }
                    } catch (Exception e) {
                        log.warn("Embedding refresh retry skipped: {}", e.getMessage());
                    }
                }
            }

            return ResponseEntity.ok(Map.of("matches", matches));

        } catch (Exception e) {
            log.error("Search Error:", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Face search failed: " + e.getMessage()));
        }
    }

    private List<Map<String, Object>> buildMatches(List<Photo> photos, List<Double> selfieEmbedding) {
        List<Map<String, Object>> found = new ArrayList<>();
        
        for (Photo p : photos) {
            if (p.getEmbeddings() == null || p.getEmbeddings().isEmpty()) continue;
            
            double bestScore = -1.0;
            for (List<Double> candidate : p.getEmbeddings()) {
                double score = faceClientService.cosineSimilarity(selfieEmbedding, candidate);
                if (score > bestScore) {
                    bestScore = score;
                }
            }

            if (bestScore >= matchThreshold) {
                Map<String, Object> match = new LinkedHashMap<>();
                match.put("_id", p.getId());
                match.put("url", p.getUrl());
                match.put("publicId", p.getPublicId());
                match.put("group", p.getGroup());
                match.put("uploader", p.getUploader());
                match.put("filename", p.getFilename());
                match.put("createdAt", p.getCreatedAt());
                match.put("score", bestScore);
                found.add(match);
            }
        }
        
        // Sort descending by score
        found.sort((a, b) -> Double.compare((Double) b.get("score"), (Double) a.get("score")));
        return found;
    }

    // ─── 4. POST /upload-avatar ───────────────────────────────────

    @PostMapping("/upload-avatar")
    public ResponseEntity<?> uploadAvatar(@RequestParam("avatar") MultipartFile file,
                                          HttpServletRequest request) {
        log.info("Hit /api/user/upload-avatar");
        try {
            User user = authResolver.resolveUser(request);

            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "No file uploaded"));
            }

            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Only images allowed"));
            }

            Map<String, Object> result = cloudinaryService.upload(file.getBytes(), "kwikpic/avatars");
            String secureUrl = (String) result.get("secure_url");

            user.setSelfieUrl(secureUrl);
            userRepository.save(user);

            return ResponseEntity.ok(Map.of(
                    "url", secureUrl,
                    "message", "Profile picture updated"
            ));

        } catch (Exception e) {
            log.error("User Avatar Upload Error:", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }
}
