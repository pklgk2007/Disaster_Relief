package com.relief;

import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
public class AdminController {
    private final Db db;
    private final PasswordEncoder enc;

    public AdminController(Db db, PasswordEncoder enc) { this.db = db; this.enc = enc; }

    @GetMapping
    public List<Map<String, Object>> list() {
        return db.list("SELECT u.user_id, u.username, u.role, u.created_at, " +
            "CONCAT(p.first_name, ' ', p.last_name) AS name " +
            "FROM User_Account u LEFT JOIN Person p ON p.person_id = u.person_id ORDER BY u.user_id");
    }

    @PostMapping
    @Transactional
    public Map<String, Object> create(@RequestBody Map<String, Object> b) {
        String username = U.str(b, "username");
        String password = U.str(b, "password");
        String role = U.str(b, "role");
        if (username == null || username.length() < 3) throw new IllegalArgumentException("Username needs at least 3 characters");
        if (password == null || password.length() < 6) throw new IllegalArgumentException("Password needs at least 6 characters");
        if (role == null || !List.of("ADMIN", "COORDINATOR", "VOLUNTEER").contains(role))
            throw new IllegalArgumentException("Invalid role");

        Integer personId = null;
        if (!role.equals("ADMIN")) {
            String first = U.str(b, "firstName"), last = U.str(b, "lastName");
            if (first == null || last == null) throw new IllegalArgumentException("First and last name are required");
            personId = db.insert("INSERT INTO Person(first_name, last_name, email) VALUES (?, ?, ?)",
                first, last, U.str(b, "email"));
            String phone = U.str(b, "phone");
            if (phone != null) db.update("INSERT INTO Person_Phone(person_id, phone) VALUES (?, ?)", personId, phone);
            if (role.equals("VOLUNTEER")) {
                db.insert("INSERT INTO Volunteer(person_id, skill, availability, shelter_id) VALUES (?, ?, ?, ?)",
                    personId, U.str(b, "skill"), U.str(b, "availability"), U.numOrNull(b.get("shelterId")));
            } else {
                String r = U.str(b, "coordRole");
                db.insert("INSERT INTO Coordinator(person_id, role) VALUES (?, ?)", personId, r == null ? "Coordinator" : r);
            }
        }
        int id = db.insert("INSERT INTO User_Account(username, password_hash, role, person_id) VALUES (?, ?, ?, ?)",
            username, enc.encode(password), role, personId);
        return Map.of("userId", id);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@PathVariable int id, Authentication auth) {
        Map<String, Object> u = db.one("SELECT username, person_id FROM User_Account WHERE user_id = ?", id);
        if (u.get("username").equals(auth.getName())) throw new IllegalArgumentException("You cannot delete your own account");
        if (u.get("person_id") != null) {
            // Person delete cascades to User_Account, Volunteer/Coordinator, phones, contacts and the volunteer's tasks
            db.update("DELETE FROM Person WHERE person_id = ?", u.get("person_id"));
        } else {
            db.update("DELETE FROM User_Account WHERE user_id = ?", id);
        }
    }
}
