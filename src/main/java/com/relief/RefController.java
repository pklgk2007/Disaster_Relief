package com.relief;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Dropdown data for the forms. */
@RestController
@RequestMapping("/api/ref")
public class RefController {
    private final Db db;

    public RefController(Db db) { this.db = db; }

    @GetMapping
    public Map<String, Object> all() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("shelters", db.list("SELECT s.shelter_id, s.name, s.status, l.area FROM Shelter s " +
            "JOIN Location l ON l.location_id = s.location_id ORDER BY s.name"));
        m.put("events", db.list("SELECT e.event_id, e.type, e.severity, l.area FROM Disaster_Event e " +
            "JOIN Location l ON l.location_id = e.location_id ORDER BY e.start_date DESC"));
        m.put("items", db.list("SELECT i.item_id, i.item_name, i.unit, c.category_name FROM Supply_Item i " +
            "JOIN Item_Category c ON c.category_id = i.category_id ORDER BY c.category_name, i.item_name"));
        m.put("volunteers", db.list("SELECT v.volunteer_id, CONCAT(p.first_name, ' ', p.last_name) AS name, " +
            "v.skill, s.name AS shelter FROM Volunteer v JOIN Person p ON p.person_id = v.person_id " +
            "LEFT JOIN Shelter s ON s.shelter_id = v.shelter_id ORDER BY name"));
        return m;
    }
}
