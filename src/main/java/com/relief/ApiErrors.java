package com.relief;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiErrors {
    private ResponseEntity<Map<String, String>> err(int code, String msg) {
        return ResponseEntity.status(code).body(Map.of("error", msg));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Map<String, String>> auth(AuthenticationException e) {
        return err(401, "Invalid username or password");
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<Map<String, String>> dup(DuplicateKeyException e) {
        return err(409, "That value already exists (usernames, emails and item rows must be unique)");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> integrity(DataIntegrityViolationException e) {
        return err(400, "Invalid data, or a referenced record does not exist");
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<Map<String, String>> missing(NoSuchElementException e) {
        return err(404, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> bad(IllegalArgumentException e) {
        return err(400, e.getMessage());
    }
}
