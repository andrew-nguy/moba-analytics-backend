package com.nguay097.moba_analytics.service;

import com.nguay097.moba_analytics.entity.User;
import com.nguay097.moba_analytics.repository.UserRepository;
import com.nguay097.moba_analytics.util.JwtUtil;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final String missingUserHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.missingUserHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public String register(String email, String password) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            if (userRepository.findByEmail(email).isPresent()) {
                throw new DuplicateEmailException();
            }
            throw exception;
        }
        return "User registered successfully";
    }

    public String login(String email, String password) {
        var user = userRepository.findByEmail(email);
        String hash = user.map(User::getPassword).orElse(missingUserHash);
        if (!passwordEncoder.matches(password, hash) || user.isEmpty()) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return jwtUtil.generateToken(email);
    }

    public static class DuplicateEmailException extends RuntimeException {}
}
