# Jade-Tipi Coding Guidelines

**Version:** 0.0.3
**Last Updated:** 2025-11-09

---

## Overview

This document outlines coding standards and practices for the Jade-Tipi demonstration project.

---

## General Principles

1. **Clean Architecture**: Maintain clear separation between Controller → Service → Data layers
2. **Reactive Programming**: Use Mono/Flux throughout, avoid blocking operations in request handling
3. **Type Safety**: Leverage Java/Groovy type system and validation annotations
4. **Error Handling**: Use GlobalExceptionHandler for consistent error responses
5. **Testing**: Maintain comprehensive unit and integration test coverage

---

## Security Considerations

### Secrets Management

**For Demonstration/Development:**
- Hard-coded secrets in test code are **acceptable** for this demonstration project
- This project will **not** be deployed to production with the current configuration
- Test credentials in files like `KeycloakTestHelper.groovy` are for local development only
- The Keycloak component will not be deployed to production with these secrets

**Rationale:**
- This is a demonstration/educational project
- Production deployment will use proper secret management (e.g., HashiCorp Vault, AWS Secrets Manager)
- Test/demo credentials do not protect real sensitive data
- Local development convenience is prioritized over production security practices

**Production Deployment:**
When this project is adapted for production use:
- All secrets MUST be externalized using environment variables or secret management systems
- Test helper classes MUST NOT contain fallback secrets
- Proper secret rotation procedures MUST be documented
- Keycloak realm configuration MUST use production-grade credentials

---

## Code Style

### Backend (Groovy/Java)

**Naming Conventions:**
- Classes: PascalCase (`DocumentService`, `TransactionController`)
- Methods: camelCase (`findById`, `openTransaction`)
- Constants: UPPER_SNAKE_CASE (`MAX_DOCUMENT_SIZE_BYTES`, `COLLECTION_NAME`)
- Variables: camelCase (`documentId`, `transactionToken`)

**Logging:**
- Use `@Slf4j` annotation on all controllers and services
- Log levels:
  - DEBUG: Operation start
  - INFO: Successful operations
  - WARN: Business rule violations
  - ERROR: Unexpected failures
- Include relevant context (IDs, parameters) in log messages

**Error Handling:**
- Throw specific exceptions (`IllegalArgumentException`, `ResponseStatusException`)
- Let GlobalExceptionHandler handle exception-to-response mapping
- Use reactive error operators (`onErrorMap`, `onErrorResume`) when appropriate

**Validation:**
- Use `@Valid` annotations on controller endpoints
- Use Jakarta Bean Validation on DTOs (`@NotBlank`, `@NotNull`, etc.)
- Implement business logic validation in service layer

**Testing:**
- Spock framework for unit tests (`*Spec.groovy`)
- Use `StepVerifier` for reactive assertions
- Mock dependencies with Spock's `Mock()` or `Stub()`
- Integration tests in separate source set

---

## Reactive Programming

### Best Practices

- Return `Mono<T>` for single-value operations
- Return `Flux<T>` for multi-value streams
- Avoid `.block()` in request handling paths
- Chain reactive operators instead of imperative code
- Use `doOnSuccess`, `doOnError` for side effects (logging)

### Blocking Operations During Startup

**IMPORTANT:** Blocking operations are **acceptable** during application initialization and startup.

**Rationale:**
- Performance is not critical during startup
- Simpler, more readable code during initialization is preferred
- Easier debugging and troubleshooting
- Startup only happens once, not on every request

**Acceptable blocking scenarios:**
- Database initialization (`MongoDbInitializer`)
- Index creation during startup
- Configuration loading and validation
- Resource preloading

**Example (Acceptable):**
```groovy
@Component
class MongoDbInitializer {

    @EventListener(ApplicationReadyEvent)
    void initializeMongoDB() {
        // .block() is acceptable here - this is startup code
        mongoTemplate.save(jsonContent, COLLECTION_NAME)
            .doOnSuccess { saved -> log.info('Initialized collection') }
            .doOnError { error -> log.error('Failed to initialize', error) }
            .block()
    }
}
```

**Example (Request Handling - No Blocking):**
```groovy
@PostMapping
Mono<ResponseEntity<Document>> createDocument(@RequestBody Document doc) {
    log.debug('Creating document: id={}', doc.id)
    // Never use .block() here - this handles requests
    return documentService.create(doc)
        .doOnSuccess { log.info('Document created: id={}', doc.id) }
        .doOnError { ex -> log.error('Failed to create document', ex) }
        .map(created -> ResponseEntity.status(HttpStatus.CREATED).body(created))
}
```

---

## MongoDB Conventions

### Field Naming

**IMPORTANT:** MongoDB documents must use simple lowercase field names, NOT camelCase.

**✅ Correct:**
```json
{
  "created": "2025-11-08T...",
  "committed": "2025-11-08T...",
  "commit": "abc123",
  "organization": "acme",
  "group": "research"
}
```

**❌ Incorrect:**
```json
{
  "createdAt": "2025-11-08T...",
  "committedAt": "2025-11-08T...",
  "commitId": "abc123"
}
```

**Rationale:**
- Maintains consistency across the database layer
- Separates database conventions from application code conventions
- Improves readability in MongoDB queries and documents
- Prefer shorter names when the context is clear (e.g., `created` over `created_at`)

### Database Operations

**MongoDB Best Practices:**
- Use `ReactiveMongoTemplate` for all operations
- Define indexes for frequently queried fields
- Use field projection for list/summary queries
- Validate document size before persistence

**Constants:**
- Define collection names in `Constants.groovy`
- Define field names as constants
- Use constants for regex patterns and limits

### Transaction Retention Policy

**IMPORTANT:** Transactions are retained indefinitely by design.

**Policy:**
- All transactions are saved permanently in the database
- No TTL (Time-To-Live) or automatic cleanup is implemented
- Transactions may be long-lived and must remain accessible
- This is an intentional design decision, not a missing feature

**Rationale:**
- Transactions represent important historical records
- Long-lived transactions are a valid use case
- Audit trail and compliance requirements may require indefinite retention
- Manual cleanup processes can be implemented if needed in the future

**Future Considerations:**
- If cleanup becomes necessary, implement manual administrative endpoints
- Consider archival strategies for old transactions rather than deletion
- Document any retention policies in operational procedures

---

## API Design

**REST Endpoints:**
- Use standard HTTP methods (GET, POST, PUT, DELETE)
- Return appropriate status codes (201 Created, 204 No Content, 404 Not Found, etc.)
- Use `ResponseEntity<T>` for explicit status control
- Implement pagination for list endpoints

**Request/Response:**
- Use DTOs (records) for request/response bodies
- Apply validation annotations to DTOs
- Return consistent error response format (via GlobalExceptionHandler)
- Document APIs with OpenAPI annotations

---

## Frontend (Next.js)

**TypeScript:**
- Use strict TypeScript configuration
- Prefer functional components
- Use React hooks for state management

**Navigation:**
- Use Next.js router (`useRouter`) instead of `window.location`
- Implement loading states for async operations
- Add error boundaries for graceful error handling

**API Integration:**
- Handle loading, success, and error states
- Provide user feedback during operations
- Implement proper error messages

---

## Version Control

**Commit Messages:**
- Use clear, concise messages
- Prefix with type: feat, fix, refactor, test, docs, chore
- Examples:
  - `feat: add pagination to document list endpoint`
  - `fix: resolve CORS configuration duplication`
  - `test: add integration tests for transaction timeout`

**Branches:**
- `main`: Production-ready code
- `develop`: Active development
- Feature branches: `feature/description`
- Bug fixes: `fix/description`

---

## Documentation

**Code Documentation:**
- JavaDoc for public APIs and interfaces
- Inline comments for complex logic
- Document assumptions and constraints

**README:**
- Keep setup instructions current
- Document environment variables
- Include Docker Compose usage

**API Documentation:**
- Use OpenAPI/Swagger annotations
- Provide request/response examples
- Document error scenarios

---

## Performance

**Optimization:**
- Add MongoDB indexes for query optimization
- Implement caching where appropriate
- Use pagination for large result sets
- Monitor and log performance metrics

**Scalability:**
- Design stateless services
- Use JWT for authentication (no session state)
- Document horizontal scaling approach

---

## Monitoring & Observability

**Logging:**
- Use structured logging (logstash-logback-encoder)
- Include correlation IDs for request tracing
- Configure appropriate log levels per environment

**Metrics:**
- Use Spring Boot Actuator
- Expose health endpoints
- Track business metrics (transactions, documents)

**Health Checks:**
- Implement custom health indicators for databases
- Include dependency status in health endpoints
- Configure health check timeouts

---

## Dependencies

**Dependency Management:**
- Keep dependencies current
- Review security advisories
- Document dependency choices

**Gradle:**
- Use version catalogs for dependency versions
- Separate implementation from test dependencies
- Configure build optimization

---

## Testing Strategy

**Unit Tests:**
- Test individual components in isolation
- Mock external dependencies
- Achieve high coverage of business logic
- Use descriptive test names

**Integration Tests:**
- Test complete request-response cycles
- Use test containers where appropriate
- Clean up test data between tests
- Test error scenarios

**Test Organization:**
- Unit tests in `src/test`
- Integration tests in `src/integrationTest`
- Test utilities in shared packages

---

## Configuration

**Application Properties:**
- Use YAML format (`application.yml`)
- Profile-specific configuration (`application-{profile}.yml`)
- Externalize environment-specific values
- Document required configuration

**Profiles:**
- `default`: Base configuration
- `mongodb`: MongoDB-specific configuration
- `test`: Test environment overrides

---

## Deployment

**Docker:**
- Create Dockerfile for backend service
- Use multi-stage builds for optimization
- Configure health checks in docker-compose
- Document resource requirements

**Environment Variables:**
- Document all required variables
- Provide sensible defaults where appropriate
- Use `.env` for local development (not committed)

---

## Background Process Management

**IMPORTANT:** When starting background processes for development/testing, always stop them when done.

**Guidelines:**
- If you start the backend server with `./gradlew bootRun` in the background, stop it when the task is complete
- Use `KillShell` to terminate background shell processes
- Clean up any processes running on application ports (e.g., port 8765)
- Don't leave processes running between tasks unless explicitly requested by the user

**Example workflow:**
1. Start process in background for testing
2. Complete the task
3. Stop the background process before finishing
4. Verify the port is free

---

## Review Checklist

Before submitting code:
- [ ] All tests pass
- [ ] Code follows style guidelines
- [ ] New features have tests
- [ ] API changes documented
- [ ] Error handling implemented
- [ ] Logging added where appropriate
- [ ] No hardcoded production secrets (test secrets OK)
- [ ] Performance considered
- [ ] Security implications reviewed
- [ ] Blocking operations only used during startup (if applicable)
- [ ] MongoDB field names use lowercase convention

---

**Prepared by:** Claude Code
**Date:** 2025-11-09
