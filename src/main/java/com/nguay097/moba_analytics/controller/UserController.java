package com.nguay097.moba_analytics.controller;

import com.nguay097.moba_analytics.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody Credentials credentials) {
        return ResponseEntity.ok(authService.register(credentials.email(), credentials.password()));
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@Valid @RequestBody Credentials credentials) {
        String token = authService.login(credentials.email(), credentials.password());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(token);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> invalidCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Invalid email or password"));
    }

    @ExceptionHandler(AuthService.DuplicateEmailException.class)
    public ResponseEntity<Map<String, String>> duplicateEmail() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "Email address already exists"));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> invalidInput() {
        return ResponseEntity.badRequest().body(Map.of("message",
                "Provide a valid email address and a nonblank password of up to 72 UTF-8 bytes."));
    }

    public record Credentials(@NotBlank @Email @Size(max = 254) String email, @NotBlank String password) {

        public Credentials {
            if (email != null) {
                email = email.strip().toLowerCase(Locale.ROOT);
            }
        }

        @AssertTrue
        public boolean isPasswordWithinBcryptLimit() {
            return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
        }

        @Override
        public String toString() {
            return "Credentials[redacted]";
        }
    }
}
