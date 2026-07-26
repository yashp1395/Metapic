package com.metapic.controller;

import com.metapic.dto.JoinGroupRequest;
import com.metapic.model.Group;
import com.metapic.model.User;
import com.metapic.repository.GroupRepository;
import com.metapic.repository.UserRepository;
import com.metapic.service.AuthResolverService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client endpoints — all require userJwt.
 *
 * <p>Covers:
 * <ul>
 *   <li>POST /api/client/join-group — canonical join endpoint (frontend uses this)</li>
 * </ul>
 *
 * <p>Note: The duplicate /api/user/join-group in Usersignup.js is NOT ported
 * because the frontend exclusively calls /api/client/join-group.
 */
@RestController
@RequestMapping("/api/client")
public class ClientController {

    private static final Logger log = LoggerFactory.getLogger(ClientController.class);

    private final AuthResolverService authResolver;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;

    public ClientController(AuthResolverService authResolver,
                            GroupRepository groupRepository,
                            UserRepository userRepository) {
        this.authResolver = authResolver;
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
    }

    /**
     * Join a group by 6-digit code.
     *
     * <p>Adds user to group.participants AND group to user.joinedGroups
     * (bidirectional reference, same as Node's client.js).
     */
    @PostMapping("/join-group")
    public ResponseEntity<?> joinGroup(@RequestBody JoinGroupRequest req,
                                       HttpServletRequest request) {
        try {
            User user = authResolver.resolveUser(request);

            if (req.code() == null || req.code().isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Group code is required"));
            }

            var optGroup = groupRepository.findByCode(req.code());
            if (optGroup.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Invalid Group Code"));
            }
            Group group = optGroup.get();

            // Check if already joined
            if (group.getParticipants().contains(user.getId())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "You are already in this group"));
            }

            // Add user to group participants
            group.getParticipants().add(user.getId());
            groupRepository.save(group);

            // Add group to user's joined list
            if (!user.getJoinedGroups().contains(group.getId())) {
                user.getJoinedGroups().add(group.getId());
                userRepository.save(user);
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", "Joined successfully!");
            body.put("groupId", group.getId());
            body.put("name", group.getName());
            return ResponseEntity.ok(body);

        } catch (Exception e) {
            log.error("Join Group Error:", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", e.getMessage()));
        }
    }
}
