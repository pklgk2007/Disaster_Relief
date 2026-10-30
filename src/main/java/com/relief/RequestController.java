package com.relief;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/requests")
public class RequestController {
    private static final List<String> PRIORITIES = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final List<String> STATUSES = List.of("PENDING", "APPROVED", "DISPATCHED", "FULFILLED", "CANCELLED");
    private final Db db;

    public RequestController(Db db) { this.db = db; }

    @GetMapping
    public List<Map<String, Object>> list() {
        return db.list(
            "SELECT r.request_id, r.priority, r.status, r.created_at, s.name AS shelter, " +
            "CONCAT(e.type, ' (', l.area, ')') AS event, " +
            "(SELECT COUNT(*) FROM Request_Item ri WHERE ri.request_id = r.request_id) AS item_count " +
            "FROM Relief_Request r " +
            "JOIN Shelter s ON s.shelter_id = r.shelter_id " +
            "JOIN Disaster_Event e ON e.event_id = r.event_id " +
            "JOIN Location l ON l.location_id = e.location_id " +
            "ORDER BY FIELD(r.priority, 'CRITICAL', 'HIGH', 'MEDIUM', 'LOW'), r.created_at DESC");
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable int id) {
        Map<String, Object> r = new LinkedHashMap<>(db.one(
            "SELECT r.request_id, r.priority, r.status, r.created_at, s.name AS shelter FROM Relief_Request r " +
            "JOIN Shelter s ON s.shelter_id = r.shelter_id WHERE r.request_id = ?", id));
        // in_stock = what the requesting shelter currently holds, so a shortage is visible at a glance
        r.put("items", db.list(
            "SELECT si.item_name, si.unit, ri.qty_needed, COALESCE(ss.quantity, 0) AS in_stock " +
            "FROM Request_Item ri " +
            "JOIN Relief_Request rr ON rr.request_id = ri.request_id " +
            "JOIN Supply_Item si ON si.item_id = ri.item_id " +
            "LEFT JOIN Shelter_Stock ss ON ss.shelter_id = rr.shelter_id AND ss.item_id = ri.item_id " +
            "WHERE ri.request_id = ? ORDER BY si.item_name", id));
        return r;
    }

    @SuppressWarnings("unchecked")
    @PostMapping
    @Transactional
    public Map<String, Object> create(@RequestBody Map<String, Object> b) {
        int eventId = U.num(b.get("eventId"), "disaster event");
        int shelterId = U.num(b.get("shelterId"), "shelter");
        String priority = U.str(b, "priority");
        if (priority == null || !PRIORITIES.contains(priority)) throw new IllegalArgumentException("Invalid priority");
        List<Map<String, Object>> items = (List<Map<String, Object>>) b.get("items");
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("Add at least one item");

        // merge duplicate items by summing quantities
        Map<Integer, Integer> merged = new LinkedHashMap<>();
        for (Map<String, Object> it : items) {
            int itemId = U.num(it.get("itemId"), "item");
            int qty = U.num(it.get("qty"), "quantity");
            if (qty <= 0) throw new IllegalArgumentException("Quantity must be greater than zero");
            merged.merge(itemId, qty, Integer::sum);
        }

        int id = db.insert("INSERT INTO Relief_Request(event_id, shelter_id, priority) VALUES (?, ?, ?)",
            eventId, shelterId, priority);
        for (Map.Entry<Integer, Integer> e : merged.entrySet())
            db.update("INSERT INTO Request_Item(request_id, item_id, qty_needed) VALUES (?, ?, ?)", id, e.getKey(), e.getValue());
        return Map.of("requestId", id);
    }

    @PutMapping("/{id}/status")
    public void setStatus(@PathVariable int id, @RequestBody Map<String, Object> b) {
        String s = U.str(b, "status");
        if (s == null || !STATUSES.contains(s)) throw new IllegalArgumentException("Invalid status");
        if (db.update("UPDATE Relief_Request SET status = ? WHERE request_id = ?", s, id) == 0)
            throw new NoSuchElementException("Request not found");
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable int id) {
        // Request_Item, Dispatch and Dispatch_Log cascade; Volunteer_Task.request_id is set to NULL
        if (db.update("DELETE FROM Relief_Request WHERE request_id = ?", id) == 0)
            throw new NoSuchElementException("Request not found");
    }
}
