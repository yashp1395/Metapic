package com.metapic.controller;

import com.metapic.dto.LoginRequest;
import com.metapic.dto.PhotographerLoginRequest;
import com.metapic.dto.PhotographerSignupRequest;
import com.metapic.dto.SignupRequest;
import com.metapic.model.Photographer;
import com.metapic.model.User;
import com.metapic.repository.PhotographerRepository;
import com.metapic.repository.UserRepository;
import com.metapic.service.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Authentication endpoints — no JWT required (public).
 *
 * <p>Covers:
 * <ul>
 *   <li>POST /api/signup          — User registration</li>
 *   <li>POST /api/login           — User login</li>
 *   <li>POST /api/photographer/signup — Photographer registration</li>
 *   <li>POST /api/photographer/login  — Photographer login</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    private final UserRepository userRepository;
    private final PhotographerRepository photographerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final com.metapic.service.EmailService emailService;

    public AuthController(UserRepository userRepository,
                          PhotographerRepository photographerRepository,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService,
                          com.metapic.service.EmailService emailService) {
        this.userRepository = userRepository;
        this.photographerRepository = photographerRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailService = emailService;
    }

    // ─── User Signup ────────────────────────────────────────────

    @PostMapping("/signup")
    public ResponseEntity<Map<String, Object>> userSignup(@RequestBody SignupRequest req) {
        try {
            // Check name uniqueness
            if (userRepository.existsByName(req.name())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "User name already exists"));
            }
            // Check email uniqueness
            if (userRepository.existsByEmail(req.email())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Email is already registered"));
            }

            User user = new User();
            user.setName(req.name());
            user.setEmail(req.email());
            user.setPassword(passwordEncoder.encode(req.password()));
            userRepository.save(user);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("message", "User registered successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Server error during signup"));
        }
    }

    // ─── User Login ─────────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> userLogin(@RequestBody LoginRequest req) {
        try {
            // Node: User.findOne({ $or: [{name}, {email}] })
            var optUser = userRepository.findByNameOrEmail(req.name(), req.email());
            if (optUser.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "User not found"));
            }

            User user = optUser.get();
            if (!passwordEncoder.matches(req.password(), user.getPassword())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Invalid credentials"));
            }

            String token = jwtService.generateToken(user.getId());

            Map<String, Object> userInfo = new LinkedHashMap<>();
            userInfo.put("_id", user.getId());
            userInfo.put("name", user.getName());
            userInfo.put("email", user.getEmail());
            userInfo.put("joinedGroups", user.getJoinedGroups());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", "Login successful");
            body.put("token", token);
            body.put("user", userInfo);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Server error during login"));
        }
    }

    // ─── Photographer Signup ────────────────────────────────────

    @PostMapping("/photographer/signup")
    public ResponseEntity<Map<String, Object>> photographerSignup(
            @RequestBody PhotographerSignupRequest req) {
        try {
            if (photographerRepository.existsByEmail(req.email())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Email already in use"));
            }

            Photographer ph = new Photographer();
            ph.setName(req.name());
            ph.setBusinessName(req.businessName());
            ph.setEmail(req.email());
            ph.setPasswordHash(passwordEncoder.encode(req.password()));
            photographerRepository.save(ph);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("message", "Photographer registered successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error registering photographer"));
        }
    }

    // ─── Photographer Login ─────────────────────────────────────

    @PostMapping("/photographer/login")
    public ResponseEntity<Map<String, Object>> photographerLogin(
            @RequestBody PhotographerLoginRequest req) {
        try {
            var optPh = photographerRepository.findByEmail(req.email());
            if (optPh.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "User not found"));
            }

            Photographer ph = optPh.get();
            if (!passwordEncoder.matches(req.password(), ph.getPasswordHash())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Invalid credentials"));
            }

            String token = jwtService.generateToken(ph.getId());

            Map<String, Object> userInfo = new LinkedHashMap<>();
            userInfo.put("name", ph.getName());
            userInfo.put("email", ph.getEmail());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", "Login successful");
            body.put("token", token);
            body.put("user", userInfo);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Server error"));
        }
    }

    // ─── Forgot Password (User) ─────────────────────────────────

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(@RequestBody com.metapic.dto.ForgotPasswordRequest req) {
        try {
            var optUser = userRepository.findByEmail(req.email());
            if (optUser.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "User with this email not found"));
            }

            User user = optUser.get();
            String otp = String.format("%06d", new java.util.Random().nextInt(999999));
            
            // Set expiry to 15 minutes from now
            long expiryTime = System.currentTimeMillis() + (15 * 60 * 1000);
            user.setResetOtp(otp);
            user.setResetOtpExpiry(new java.util.Date(expiryTime));
            userRepository.save(user);

            emailService.sendPasswordResetOtp(user.getEmail(), otp);

            return ResponseEntity.ok(Map.of("message", "Password reset OTP sent to your email"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error sending OTP"));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody com.metapic.dto.ResetPasswordRequest req) {
        try {
            var optUser = userRepository.findByEmail(req.email());
            if (optUser.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "User with this email not found"));
            }

            User user = optUser.get();
            
            if (user.getResetOtp() == null || !user.getResetOtp().equals(req.otp())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Invalid OTP"));
            }
            
            if (user.getResetOtpExpiry() == null || user.getResetOtpExpiry().before(new java.util.Date())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "OTP has expired"));
            }

            // Valid OTP, reset password
            user.setPassword(passwordEncoder.encode(req.newPassword()));
            user.setResetOtp(null);
            user.setResetOtpExpiry(null);
            userRepository.save(user);

            return ResponseEntity.ok(Map.of("message", "Password has been successfully reset"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error resetting password"));
        }
    }

    // ─── Forgot Password (Photographer) ─────────────────────────

    @PostMapping("/photographer/forgot-password")
    public ResponseEntity<Map<String, Object>> photographerForgotPassword(@RequestBody com.metapic.dto.ForgotPasswordRequest req) {
        try {
            var optPh = photographerRepository.findByEmail(req.email());
            if (optPh.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Photographer with this email not found"));
            }

            Photographer ph = optPh.get();
            String otp = String.format("%06d", new java.util.Random().nextInt(999999));
            
            // Set expiry to 15 minutes from now
            long expiryTime = System.currentTimeMillis() + (15 * 60 * 1000);
            ph.setResetOtp(otp);
            ph.setResetOtpExpiry(new java.util.Date(expiryTime));
            photographerRepository.save(ph);

            emailService.sendPasswordResetOtp(ph.getEmail(), otp);

            return ResponseEntity.ok(Map.of("message", "Password reset OTP sent to your email"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error sending OTP"));
        }
    }

    @PostMapping("/photographer/reset-password")
    public ResponseEntity<Map<String, Object>> photographerResetPassword(@RequestBody com.metapic.dto.ResetPasswordRequest req) {
        try {
            var optPh = photographerRepository.findByEmail(req.email());
            if (optPh.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Photographer with this email not found"));
            }

            Photographer ph = optPh.get();
            
            if (ph.getResetOtp() == null || !ph.getResetOtp().equals(req.otp())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Invalid OTP"));
            }
            
            if (ph.getResetOtpExpiry() == null || ph.getResetOtpExpiry().before(new java.util.Date())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "OTP has expired"));
            }

            // Valid OTP, reset password
            ph.setPasswordHash(passwordEncoder.encode(req.newPassword()));
            ph.setResetOtp(null);
            ph.setResetOtpExpiry(null);
            photographerRepository.save(ph);

            return ResponseEntity.ok(Map.of("message", "Password has been successfully reset"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error resetting password"));
        }
    }
}
