package com.relief;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInit implements CommandLineRunner {
    private final Db db;
    private final PasswordEncoder enc;

    private final String adminPassword;

    public DataInit(Db db, PasswordEncoder enc, @Value("${app.admin-password:admin123}") String adminPassword) {
        this.db = db; this.enc = enc; this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        Integer admins = db.jdbc.queryForObject("SELECT COUNT(*) FROM User_Account WHERE role = 'ADMIN'", Integer.class);
        if (admins == null || admins == 0) {
            db.update("INSERT INTO User_Account(username, password_hash, role) VALUES (?, ?, 'ADMIN')",
                "admin", enc.encode(adminPassword));
            System.out.println("Created admin account 'admin'" + (adminPassword.equals("admin123") ? " with the default password admin123, set ADMIN_PASSWORD before going public" : ""));
        }
    }
}
