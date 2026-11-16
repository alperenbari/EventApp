Secure Ticket and Reservation API

This is an event platform backend. Organizers create and publish events, customers reserve seats.
Features: JWT authentication, role-based authorization, oversell protection, and idempotent reservation requests.

1. Setup

The project uses Maven. No external database setup is needed, since H2 runs in-memory.

Build:
./mvnw clean install

Run (http://localhost:8080):
./mvnw spring-boot:run

Run with the dev profile (seeds demo users, enables the H2 console at /h2-console):
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

Run tests:
./mvnw test

2. Seed Users

admin@eventticket.com / Admin123! / ADMIN
organizer@eventticket.com / Organizer123! / ORGANIZER
customer@eventticket.com / Customer123! / CUSTOMER

3. Authorozation Flow

1. POST /api/auth/register creates a new user. 
2. POST /api/auth/login exchanges email and password for an access token
   and a refresh token.
3. Protected endpoints require the header: Authorization: Bearer <accessToken>
4. When the access token expires, POST /api/auth/refresh (with the refresh
   token) issues a new access/refresh token pair, and revokes the old
   refresh token (rotation). Reusing an already-rotated refresh token
   revokes every token issued in that login session.
5. POST /api/auth/logout (with the refresh token) revokes that session's
   tokens immediately.

Sample JWT:

{
  "sub": "customer@eventticket.com",
  "type": "access",
  "uid": 3,
  "iat": 1731600000,
  "exp": 1731600900
}


4. Architectural Decisions

Layered architecture: controller -> service->
repository

Oversell protection: a JPA lock on Event.version.

Idempotency: the IdempotencyKey table catches duplicate requests that share the same Idempotency-Key header. 

Two-layer role authorization: PreAuthorize checks if a role can call an endpoint. 


