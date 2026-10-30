package com.relief;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DbUserDetailsService implements UserDetailsService {
    private final Db db;

    public DbUserDetailsService(Db db) { this.db = db; }

    @Override
    public UserDetails loadUserByUsername(String username) {
        List<Map<String, Object>> rows =
            db.list("SELECT username, password_hash, role FROM User_Account WHERE username = ?", username);
        if (rows.isEmpty()) throw new UsernameNotFoundException(username);
        Map<String, Object> r = rows.get(0);
        return User.withUsername(username)
            .password((String) r.get("password_hash"))
            .roles((String) r.get("role"))
            .build();
    }
}
