# Disaster Relief & Shelter Management

Stack: MySQL 8, Spring Boot 3 (Java 17), vanilla JS.

## Run
1. `mysql -u root -p < db/schema.sql`
2. Set your MySQL password in `src/main/resources/application.properties`
3. `mvn spring-boot:run`
4. Open http://localhost:8080 and sign in as `admin` / `admin123`

## Roles
- Admin: creates and deletes accounts (volunteer, coordinator, admin), plus everything coordinators can do
- Coordinator: creates, views, changes status of and deletes relief requests; assigns tasks to volunteers
- Volunteer: sees all their tasks and sets each one to Assigned / In progress / Completed

## Public home page
- `/` shows a public home page (what we do, why it matters, live stats, "Want to join" form); staff sign in via "Staff login"
- Join submissions are saved in `Volunteer_Application` and shown to admins under "Join requests"
- Email copy via Resend: set `resend.api-key` and `resend.to` in `application.properties`
- Existing database? Run `db/migration_applications.sql` once

## Notes
- Schema is the 17 tables from the project sheet plus `User_Account` and `Volunteer_Task`
- Seed data: 3 locations, 4 shelters, 3 disaster events, 6 supply items, some stock
- Restyle by editing the CSS variables at the top of `static/style.css`
