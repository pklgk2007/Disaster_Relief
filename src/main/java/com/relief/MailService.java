package com.relief;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Sends the "Want to join" notification through the Resend API (key stays on the server). */
@Service
public class MailService {
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final String apiKey, to, from;

    public MailService(@Value("${resend.api-key:}") String apiKey,
                       @Value("${resend.to:}") String to,
                       @Value("${resend.from:Disaster Relief <onboarding@resend.dev>}") String from) {
        this.apiKey = apiKey; this.to = to; this.from = from;
        System.out.println("Resend mail: " + (apiKey.isBlank() || to.isBlank()
            ? "NOT configured (resend.api-key or resend.to is empty)" : "configured, sending to " + to));
    }

    public void notifyJoin(String name, String phone, String email, String skills, String message) {
        if (apiKey.isBlank() || to.isBlank()) {   // not configured: submissions are still saved in the database
            System.out.println("Resend: skipped, resend.api-key or resend.to is empty");
            return;
        }
        String text = "New volunteer application\n\nName: " + name + "\nPhone: " + phone + "\nEmail: " + email +
            "\nSkills: " + skills + "\nMessage: " + (message == null ? "-" : message);
        // async so the visitor never waits on (or sees failures from) the mail provider
        CompletableFuture.runAsync(() -> {
            try {
                String body = json.writeValueAsString(Map.of(
                    "from", from, "to", List.of(to), "reply_to", email,
                    "subject", "New volunteer application: " + name, "text", text));
                HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create("https://api.resend.com/emails"))
                    .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() >= 300) System.err.println("Resend failed " + res.statusCode() + ": " + res.body());
                else System.out.println("Resend: accepted (" + res.statusCode() + ")");
            } catch (Exception e) {
                System.err.println("Resend error: " + e.getMessage());
            }
        });
    }
}
