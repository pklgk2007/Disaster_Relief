package com.relief;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api")
public class TaskController {
    private static final List<String> STATUSES = List.of("ASSIGNED", "IN_PROGRESS", "COMPLETED");
    private final Db db;

    public TaskController(Db db) { this.db = db; }

    // ---------- volunteer ----------
    @GetMapping("/my-tasks")
    public List<Map<String, Object>> myTasks(Authentication auth) {
        return db.list(
            "SELECT t.task_id, t.title, t.description, t.status, t.assigned_at, t.updated_at, t.request_id, " +
            "s.name AS shelter, r.priority " +
            "FROM Volunteer_Task t " +
            "LEFT JOIN Relief_Request r ON r.request_id = t.request_id " +
            "LEFT JOIN Shelter s ON s.shelter_id = r.shelter_id " +
            "WHERE t.volunteer_id = ? " +
            "ORDER BY FIELD(t.status, 'IN_PROGRESS', 'ASSIGNED', 'COMPLETED'), t.assigned_at DESC",
            volunteerId(auth));
    }

    @PutMapping("/my-tasks/{id}/status")
    public void updateMine(@PathVariable int id, @RequestBody Map<String, Object> b, Authentication auth) {
        String s = U.str(b, "status");
        if (s == null || !STATUSES.contains(s)) throw new IllegalArgumentException("Invalid status");
        if (db.update("UPDATE Volunteer_Task SET status = ? WHERE task_id = ? AND volunteer_id = ?",
                s, id, volunteerId(auth)) == 0)
            throw new NoSuchElementException("Task not found");
    }

    private int volunteerId(Authentication auth) {
        return ((Number) db.one("SELECT v.volunteer_id FROM Volunteer v " +
            "JOIN User_Account u ON u.person_id = v.person_id WHERE u.username = ?", auth.getName())
            .get("volunteer_id")).intValue();
    }

    // ---------- coordinator / admin ----------
    @GetMapping("/tasks")
    public List<Map<String, Object>> all() {
        return db.list(
            "SELECT t.task_id, t.title, t.description, t.status, t.assigned_at, t.request_id, " +
            "CONCAT(p.first_name, ' ', p.last_name) AS volunteer " +
            "FROM Volunteer_Task t JOIN Volunteer v ON v.volunteer_id = t.volunteer_id " +
            "JOIN Person p ON p.person_id = v.person_id ORDER BY t.assigned_at DESC");
    }

    @PostMapping("/tasks")
    public Map<String, Object> assign(@RequestBody Map<String, Object> b) {
        String title = U.str(b, "title");
        if (title == null) throw new IllegalArgumentException("Task title is required");
        int id = db.insert("INSERT INTO Volunteer_Task(volunteer_id, request_id, title, description) VALUES (?, ?, ?, ?)",
            U.num(b.get("volunteerId"), "volunteer"), U.numOrNull(b.get("requestId")), title, U.str(b, "description"));
        return Map.of("taskId", id);
    }

    @DeleteMapping("/tasks/{id}")
    public void delete(@PathVariable int id) {
        if (db.update("DELETE FROM Volunteer_Task WHERE task_id = ?", id) == 0)
            throw new NoSuchElementException("Task not found");
    }
}
