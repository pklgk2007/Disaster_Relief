package com.relief;

import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.regex.Pattern;

/** Endpoints open to everyone (no login): home-page stats and the "Want to join" form. */
@RestController
@RequestMapping("/api/public")
public class PublicController {
    private static final Pattern PHONE = Pattern.compile("^[+0-9 ()-]{7,20}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private final Db db;
    private final MailService mail;

    public PublicController(Db db, MailService mail) { this.db = db; this.mail = mail; }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return db.one("SELECT " +
            "(SELECT COUNT(*) FROM Shelter) AS shelters, " +
            "(SELECT COALESCE(SUM(capacity), 0) FROM Shelter) AS capacity, " +
            "(SELECT COUNT(*) FROM Disaster_Event) AS events, " +
            "(SELECT COUNT(*) FROM Volunteer) AS volunteers, " +
            "(SELECT COUNT(*) FROM Relief_Request) AS requests, " +
            "(SELECT COUNT(*) FROM Relief_Request WHERE status IN ('PENDING','APPROVED','DISPATCHED')) AS open_requests, " +
            "(SELECT COUNT(*) FROM Relief_Request WHERE status = 'FULFILLED') AS fulfilled, " +
            "(SELECT COALESCE(SUM(quantity), 0) FROM Shelter_Stock) AS stock_units");
    }

    @PostMapping("/join")
    public Map<String, Object> join(@RequestBody Map<String, Object> b) {
        if (U.str(b, "website") != null) return Map.of("ok", true);   // honeypot field: bots fill it, people don't
        String name = U.str(b, "name"), phone = U.str(b, "phone"), email = U.str(b, "email"), skills = U.str(b, "skills");
        if (name == null || name.length() < 2 || name.length() > 100) throw new IllegalArgumentException("Enter your full name");
        if (phone == null || !PHONE.matcher(phone).matches()) throw new IllegalArgumentException("Enter a valid phone number");
        if (email == null || email.length() > 100 || !EMAIL.matcher(email).matches()) throw new IllegalArgumentException("Enter a valid email address");
        if (skills == null || skills.length() > 200) throw new IllegalArgumentException("Tell us your skills (up to 200 characters)");
        String message = U.str(b, "message");
        if (message != null && message.length() > 500) throw new IllegalArgumentException("Message is too long (500 characters max)");
        db.insert("INSERT INTO Volunteer_Application(name, phone, email, skills, message) VALUES (?, ?, ?, ?, ?)",
            name, phone, email, skills, message);
        mail.notifyJoin(name, phone, email, skills, message);
        return Map.of("ok", true);
    }
}
