package com.metapic.controller;

import com.metapic.dto.CreateGroupRequest;
import com.metapic.dto.DeletePhotosRequest;
import com.metapic.model.Group;
import com.metapic.model.Photo;
import com.metapic.model.Photographer;
import com.metapic.model.User;
import com.metapic.repository.GroupRepository;
import com.metapic.repository.PhotoRepository;
import com.metapic.repository.UserRepository;
import com.metapic.service.AuthResolverService;
import com.metapic.service.CloudinaryService;
import com.metapic.service.FaceClientService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * Photographer-specific endpoints — all require photographerJwt.
 *
 * <p>Covers:
 * <ul>
 *   <li>GET    /api/photographer/my-groups</li>
 *   <li>POST   /api/photographer/create-group</li>
 *   <li>GET    /api/photographer/group/{code}</li>
 *   <li>POST   /api/photographer/group/{code}/upload</li>
 *   <li>POST   /api/photographer/group/{code}/delete-photos</li>
 *   <li>DELETE /api/photographer/group/{code}</li>
 *   <li>POST   /api/photographer/upload-avatar</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/photographer")
public class PhotographerController {

    private static final Logger log = LoggerFactory.getLogger(PhotographerController.class);

    private final AuthResolverService authResolver;
    private final GroupRepository groupRepository;
    private final PhotoRepository photoRepository;
    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService;
    private final FaceClientService faceClientService;
    private final com.metapic.repository.PhotographerRepository photographerRepository;

    public PhotographerController(AuthResolverService authResolver,
                                  GroupRepository groupRepository,
                                  PhotoRepository photoRepository,
                                  UserRepository userRepository,
                                  CloudinaryService cloudinaryService,
                                  FaceClientService faceClientService,
                                  com.metapic.repository.PhotographerRepository photographerRepository) {
        this.authResolver = authResolver;
        this.groupRepository = groupRepository;
        this.photoRepository = photoRepository;
        this.userRepository = userRepository;
        this.cloudinaryService = cloudinaryService;
        this.faceClientService = faceClientService;
        this.photographerRepository = photographerRepository;
    }

    // ─── 1. GET /my-groups (Dashboard) ──────────────────────────

    @GetMapping("/my-groups")
    public ResponseEntity<?> getMyGroups(HttpServletRequest request) {
        try {
            Photographer ph = authResolver.resolvePhotographer(request);
            List<Group> groups = groupRepository.findByPhotographerOrderByCreatedAtDesc(ph.getId());

            List<Map<String, Object>> formatted = groups.stream().map(g -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("_id", g.getId());
                m.put("name", g.getName());
                m.put("code", g.getCode());
                m.put("photoCount", g.getPhotos() != null ? g.getPhotos().size() : 0);
                m.put("participantCount", g.getParticipants() != null ? g.getParticipants().size() : 0);
                return m;
            }).toList();

            return ResponseEntity.ok(formatted);
        } catch (Exception e) {
            log.error("Fetch Groups Error:", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // ─── 2. POST /create-group ──────────────────────────────────

    @PostMapping("/create-group")
    public ResponseEntity<?> createGroup(@RequestBody CreateGroupRequest req,
                                         HttpServletRequest request) {
        try {
            Photographer ph = authResolver.resolvePhotographer(request);

            if (req.name() == null || req.name().isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Group Name is required"));
            }

            // Generate unique 6-digit code (same logic as Node)
            String code;
            do {
                code = String.valueOf(100000 + new Random().nextInt(900000));
            } while (groupRepository.existsByCode(code));

            Group group = new Group();
            group.setName(req.name());
            group.setCode(code);
            group.setPhotographer(ph.getId());
            group.setPhotos(new ArrayList<>());
            group.setParticipants(new ArrayList<>());
            group.setCreatedAt(new Date());

            groupRepository.save(group);

            return ResponseEntity.status(HttpStatus.CREATED).body(group);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // ─── 3. GET /group/{code} (Group Manager) ──────────────────

    /**
     * Populate replacement: fetches group, then batch-fetches photos
     * and participants separately.
     *
     * Node equivalent:
     *   Group.findOne({code, photographer}).populate('photos').populate('participants', 'name email selfieUrl')
     */
    @GetMapping("/group/{code}")
    public ResponseEntity<?> getGroup(@PathVariable String code,
                                      HttpServletRequest request) {
        try {
            Photographer ph = authResolver.resolvePhotographer(request);

            var optGroup = groupRepository.findByCodeAndPhotographer(code, ph.getId());
            if (optGroup.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Group not found"));
            }
            Group group = optGroup.get();

            // Manual populate: photos
            List<Photo> photos = group.getPhotos() != null && !group.getPhotos().isEmpty()
                    ? photoRepository.findAllById(group.getPhotos())
                    : List.of();

            // Manual populate: participants (projected to name, email, selfieUrl)
            List<Map<String, Object>> participants = new ArrayList<>();
            if (group.getParticipants() != null && !group.getParticipants().isEmpty()) {
                List<User> users = userRepository.findAllById(group.getParticipants());
                for (User u : users) {
                    Map<String, Object> p = new LinkedHashMap<>();
                    p.put("_id", u.getId());
                    p.put("name", u.getName());
                    p.put("email", u.getEmail());
                    p.put("selfieUrl", u.getSelfieUrl());
                    participants.add(p);
                }
            }

            // Build response matching Mongoose populated shape
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("_id", group.getId());
            body.put("name", group.getName());
            body.put("code", group.getCode());
            body.put("photographer", group.getPhotographer());
            body.put("photos", photos);
            body.put("participants", participants);
            body.put("createdAt", group.getCreatedAt());

            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("Get Group Error:", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // ─── 4. POST /group/{code}/upload ───────────────────────────

    @PostMapping("/group/{code}/upload")
    public ResponseEntity<?> uploadPhotos(@PathVariable String code,
                                          @RequestParam("photos") MultipartFile[] files,
                                          HttpServletRequest request) {
        try {
            Photographer ph = authResolver.resolvePhotographer(request);

            var optGroup = groupRepository.findByCodeAndPhotographer(code, ph.getId());
            if (optGroup.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Group not found"));
            }
            Group group = optGroup.get();

            if (files == null || files.length == 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "No photos uploaded"));
            }

            List<Photo> createdPhotos = new ArrayList<>();

            for (MultipartFile file : files) {
                // Image-only validation (replaces Multer's file filter)
                String contentType = file.getContentType();
                if (contentType == null || !contentType.startsWith("image/")) {
                    continue; // skip non-image files silently, matching Multer behaviour
                }

                byte[] fileBytes = file.getBytes();

                // A. Upload to Cloudinary
                Map<String, Object> uploadResult = cloudinaryService.upload(fileBytes, "metapic/" + code);
                String secureUrl = (String) uploadResult.get("secure_url");
                String publicId = (String) uploadResult.get("public_id");

                // B. Generate Face Embeddings (multi-face)
                List<List<Double>> allEmbeddings = new ArrayList<>();
                try {
                    Map<String, Object> embedResp = faceClientService.computeEmbeddingFromBuffer(
                            fileBytes, file.getOriginalFilename());
                    Object embObj = embedResp.get("embeddings");
                    if (embObj instanceof List<?> embList) {
                        for (Object item : embList) {
                            if (item instanceof List<?> vec) {
                                @SuppressWarnings("unchecked")
                                List<Double> castedVec = (List<Double>) vec;
                                allEmbeddings.add(castedVec);
                            }
                        }
                    }
                } catch (Exception embErr) {
                    log.error("Face embed error: {}", embErr.getMessage());
                }

                // C. Create Photo document
                Photo photo = new Photo();
                photo.setUrl(secureUrl);
                photo.setPublicId(publicId);
                photo.setGroup(group.getId());
                photo.setUploader(ph.getId());
                photo.setFilename(file.getOriginalFilename());
                photo.setEmbeddings(allEmbeddings);
                photo.setCreatedAt(new Date());

                photoRepository.save(photo);
                group.getPhotos().add(photo.getId());
                createdPhotos.add(photo);
            }

            groupRepository.save(group);

            return ResponseEntity.ok(Map.of(
                    "message", "Uploaded successfully",
                    "photos", createdPhotos
            ));
        } catch (Exception e) {
            log.error("Upload Error:", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // ─── 5. POST /group/{code}/delete-photos ────────────────────

    @PostMapping("/group/{code}/delete-photos")
    public ResponseEntity<?> deletePhotos(@PathVariable String code,
                                          @RequestBody DeletePhotosRequest req,
                                          HttpServletRequest request) {
        try {
            Photographer ph = authResolver.resolvePhotographer(request);

            var optGroup = groupRepository.findByCodeAndPhotographer(code, ph.getId());
            if (optGroup.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Group not found"));
            }
            Group group = optGroup.get();

            if (Boolean.TRUE.equals(req.deleteAll())) {
                // Delete ALL photos in group
                photoRepository.deleteAllByGroup(group.getId());
                group.setPhotos(new ArrayList<>());
            } else {
                // Delete SELECTED photos
                if (req.photoIds() == null || req.photoIds().isEmpty()) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("message", "No photos selected"));
                }
                photoRepository.deleteAllById(req.photoIds());
                group.getPhotos().removeAll(req.photoIds());
            }

            groupRepository.save(group);
            return ResponseEntity.ok(Map.of("message", "Photos deleted successfully"));
        } catch (Exception e) {
            log.error("Delete Error:", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // ─── 6. DELETE /group/{code} ────────────────────────────────

    @DeleteMapping("/group/{code}")
    public ResponseEntity<?> deleteGroup(@PathVariable String code,
                                         HttpServletRequest request) {
        try {
            Photographer ph = authResolver.resolvePhotographer(request);

            var optGroup = groupRepository.findByCodeAndPhotographer(code, ph.getId());
            if (optGroup.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Group not found"));
            }
            Group group = optGroup.get();

            // Delete all photos, then the group
            photoRepository.deleteAllByGroup(group.getId());
            groupRepository.deleteById(group.getId());

            return ResponseEntity.ok(Map.of("message", "Group and all photos deleted successfully"));
        } catch (Exception e) {
            log.error("Delete Group Error:", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // ─── 7. POST /upload-avatar ─────────────────────────────────

    @PostMapping("/upload-avatar")
    public ResponseEntity<?> uploadAvatar(@RequestParam("avatar") MultipartFile file,
                                          HttpServletRequest request) {
        log.info("--- Starting Photographer Avatar Upload ---");
        try {
            Photographer ph = authResolver.resolvePhotographer(request);

            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "No file uploaded"));
            }

            // Image-only validation
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Only images allowed"));
            }

            Map<String, Object> result = cloudinaryService.upload(file.getBytes(), "metapic/avatars");
            String secureUrl = (String) result.get("secure_url");
            log.info("Upload Success. URL: {}", secureUrl);

            ph.setAvatarUrl(secureUrl);
            photographerRepository.save(ph);

            return ResponseEntity.ok(Map.of(
                    "url", secureUrl,
                    "message", "Avatar updated successfully"
            ));
        } catch (Exception e) {
            log.error("UPLOAD FAILED:", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Upload failed: " + e.getMessage()));
        }
    }
}
