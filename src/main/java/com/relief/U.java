package com.relief;

import java.util.Map;

final class U {
    private U() {}

    /** Trimmed string value, or null if missing/blank. */
    static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    static int num(Object o, String field) {
        try { return Integer.parseInt(String.valueOf(o).trim()); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid " + field); }
    }

    static Integer numOrNull(Object o) {
        if (o == null || o.toString().isBlank()) return null;
        return num(o, "number");
    }
}
