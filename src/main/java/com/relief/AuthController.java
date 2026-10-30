package com.relief;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager am;
    private final Db db;

    public AuthController(AuthenticationManager am, Db db) { this.am = am; this.db = db; }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body, HttpServletRequest req) {
        Authentication auth = am.authenticate(
            new UsernamePasswordAuthenticationToken(body.get("username"), body.get("password")));
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        req.getSession(true).setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx);
        return profile(auth.getName());
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication auth) { return profile(auth.getName()); }

    private Map<String, Object> profile(String username) {
        return db.one("SELECT u.username, u.role, CONCAT(p.first_name, ' ', p.last_name) AS name " +
            "FROM User_Account u LEFT JOIN Person p ON p.person_id = u.person_id WHERE u.username = ?", username);
    }
}
