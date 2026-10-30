package com.relief;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/** Admin view of submissions from the public "Want to join" form. */
@RestController
@RequestMapping("/api/admin/applications")
public class ApplicationController {
    private final Db db;

    public ApplicationController(Db db) { this.db = db; }

    @GetMapping
    public List<Map<String, Object>> list() {
        return db.list("SELECT app_id, name, phone, email, skills, message, status, created_at " +
            "FROM Volunteer_Application ORDER BY FIELD(status, 'NEW', 'CONTACTED'), created_at DESC");
    }

    @PutMapping("/{id}/status")
    public void setStatus(@PathVariable int id, @RequestBody Map<String, Object> b) {
        String s = U.str(b, "status");
        if (s == null || !List.of("NEW", "CONTACTED").contains(s)) throw new IllegalArgumentException("Invalid status");
        if (db.update("UPDATE Volunteer_Application SET status = ? WHERE app_id = ?", s, id) == 0)
            throw new NoSuchElementException("Application not found");
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable int id) {
        if (db.update("DELETE FROM Volunteer_Application WHERE app_id = ?", id) == 0)
            throw new NoSuchElementException("Application not found");
    }
}
