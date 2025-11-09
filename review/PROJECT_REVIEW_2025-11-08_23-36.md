# Jade-Tipi Backend Project Review

**Review Date:** 2025-11-08
**Project Version:** 0.0.2
**Reviewer:** Claude (AI Code Assistant)

---

## Executive Summary

Jade-Tipi is a well-structured, early-stage reactive Spring Boot application implementing a scientific metadata framework. The codebase demonstrates solid architectural choices, modern reactive patterns, and good separation of concerns. However, being in proof-of-concept stage, there are several areas for improvement before production readiness.

**Overall Assessment:** ⭐⭐⭐⭐ (4/5)

**Strengths:**
- Clean reactive architecture with proper use of Spring WebFlux
- Good module separation (libraries, main app)
- Comprehensive integration test coverage
- Proper JWT/OAuth2 security implementation
- Well-documented API with Swagger/OpenAPI

**Key Areas for Improvement:**
- Inconsistent error handling across controllers
- Missing validation annotations on controller methods
- Duplicate validation logic between controllers and services
- Limited unit test coverage
- No centralized exception handling
- Missing request/response logging consistency

---

## 1. Critical Issues (High Priority)

### 1.1 Inconsistent Validation Approach

**Issue:** Validation is implemented differently across controllers.

**Evidence:**
- `TransactionController`: Manual validation with `ResponseStatusException`
- DTOs have `@Valid`, `@NotBlank` annotations but controller methods don't use `@Valid`
- Duplicate validation in controllers and services

```groovy
// TransactionController.groovy:45-50
if (!group?.organization()?.trim()) {
    return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, 'organization is required'))
}
// This duplicates DTO @NotBlank validation
```

**Recommendation:**
1. Add `@Valid` annotation to all controller `@RequestBody` parameters
2. Remove manual validation from controllers
3. Implement global exception handler for `MethodArgumentNotValidException`
4. Let Spring's validation framework handle all DTO validation

**Example Fix:**
```groovy
@PostMapping(path = '/open', consumes = MediaType.APPLICATION_JSON_VALUE)
Mono<ResponseEntity<TransactionToken>> openTransaction(
        @Valid @RequestBody Group group,  // Add @Valid here
        @AuthenticationPrincipal Jwt jwt) {

    // Remove manual validation - Spring handles it
    return transactionService.openTransaction(group)
            .map { token -> ResponseEntity.status(HttpStatus.CREATED).body(token) }
}
```

### 1.2 Missing Global Exception Handler

**Issue:** No centralized exception handling leads to inconsistent error responses.

**Evidence:**
- `DocumentController`: Uses `.onErrorResume()` for some errors, not others
- `TransactionController`: Uses `.onErrorMap()`
- Different HTTP status codes for similar errors
- No structured error response format

**Recommendation:**
Create a `@RestControllerAdvice` class for global exception handling:

```groovy
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException)
    Mono<ResponseEntity<ErrorResponse>> handleResponseStatus(ResponseStatusException ex) {
        return Mono.just(ResponseEntity
            .status(ex.statusCode)
            .body(new ErrorResponse(ex.reason, ex.statusCode.value())))
    }

    @ExceptionHandler(MethodArgumentNotValidException)
    Mono<ResponseEntity<ErrorResponse>> handleValidation(MethodArgumentNotValidException ex) {
        def errors = ex.bindingResult.fieldErrors.collectEntries {
            [(it.field): it.defaultMessage]
        }
        return Mono.just(ResponseEntity
            .badRequest()
            .body(new ErrorResponse("Validation failed", 400, errors)))
    }

    @ExceptionHandler(IllegalArgumentException)
    Mono<ResponseEntity<ErrorResponse>> handleIllegalArgument(IllegalArgumentException ex) {
        return Mono.just(ResponseEntity
            .badRequest()
            .body(new ErrorResponse(ex.message, 400)))
    }

    @ExceptionHandler(IllegalStateException)
    Mono<ResponseEntity<ErrorResponse>> handleIllegalState(IllegalStateException ex) {
        return Mono.just(ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(new ErrorResponse(ex.message, 409)))
    }
}
```

### 1.3 No Request Validation on Document Operations

**Issue:** `DocumentController` lacks input validation for document content.

**Evidence:**
- No validation on `ObjectNode` content in POST/PUT operations
- No size limits on documents
- No schema validation

**Recommendation:**
1. Add content size limits (e.g., max 1MB per document)
2. Validate required fields in documents (if any)
3. Consider JSON Schema validation for document structure
4. Add validation in controller or create a custom validator

```groovy
@PostMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
Mono<ResponseEntity<ObjectNode>> createDocument(
        @PathVariable("id") String id,
        @RequestBody ObjectNode document) {

    // Add validation
    if (document == null || document.isEmpty()) {
        return Mono.error(new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Document cannot be empty"))
    }

    // Check size
    String json = document.toString()
    if (json.length() > 1_000_000) {  // 1MB limit
        return Mono.error(new ResponseStatusException(
            HttpStatus.PAYLOAD_TOO_LARGE, "Document exceeds 1MB limit"))
    }

    return documentService.create(id, document)
            .map(created -> ResponseEntity.status(HttpStatus.CREATED).body(created))
}
```

---

## 2. Security Concerns (Medium Priority)

### 2.1 JWT Claims Not Used for Authorization

**Issue:** JWT is extracted in controllers but never used for authorization.

**Evidence:**
```groovy
// TransactionController.groovy:43
@AuthenticationPrincipal Jwt jwt  // Extracted but not used
```

**Recommendation:**
1. Implement authorization checks based on JWT claims
2. Verify user has permission for requested organization/group
3. Add role-based access control (RBAC)

**Example:**
```groovy
@PostMapping(path = '/open')
Mono<ResponseEntity<TransactionToken>> openTransaction(
        @RequestBody Group group,
        @AuthenticationPrincipal Jwt jwt) {

    // Verify user has access to this organization
    if (!hasAccess(jwt, group.organization())) {
        return Mono.error(new ResponseStatusException(
            HttpStatus.FORBIDDEN, "Access denied to organization"))
    }

    return transactionService.openTransaction(group)
            .map { token -> ResponseEntity.status(HttpStatus.CREATED).body(token) }
}

private boolean hasAccess(Jwt jwt, String organization) {
    def allowedOrgs = jwt.getClaim("organizations") as List<String>
    return allowedOrgs?.contains(organization) ?: false
}
```

### 2.2 Secrets Stored in Plain Text

**Issue:** Transaction secrets stored without encryption in MongoDB.

**Evidence:**
```groovy
// TransactionService.groovy:56
secret: secret,  // Plain text storage
```

**Recommendation:**
1. Hash secrets before storage using bcrypt or PBKDF2
2. Compare hashed values on commit verification
3. Never log or return secrets in responses (except on initial creation)

**Note:** This requires careful consideration as it impacts the transaction token pattern. Consider whether secrets need to be retrievable or if one-way hashing is sufficient.

### 2.3 CORS Configuration Too Permissive in Test

**Issue:** Test environment allows all actuator endpoints without authentication.

**Evidence:**
```yaml
# application.yml:27
exposure.include: '*'  # Exposes all actuator endpoints
```

**Recommendation:**
1. Limit exposed actuator endpoints to essential ones: `health`, `info`
2. Require authentication for sensitive endpoints: `mappings`, `env`, `beans`
3. Use separate configuration for production vs. development

---

## 3. Code Quality Issues (Medium Priority)

### 3.1 Inconsistent Error Handling Patterns

**Issue:** Different error handling approaches across similar operations.

**Evidence:**
```groovy
// DocumentController.groovy:68
.onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).build()))

// TransactionController.groovy:75
.onErrorMap(IllegalStateException) { ex ->
    new ResponseStatusException(HttpStatus.CONFLICT, ex.message, ex)
}
```

**Recommendation:**
Standardize on one approach (preferably global exception handler) and use it consistently.

### 3.2 Magic Strings and Numbers

**Issue:** Hard-coded values scattered throughout code.

**Evidence:**
```groovy
// DocumentController.groovy:99
[deletedCount: count] as Map<String, Long>

// DocumentServiceMongoDbImpl.groovy:55
if (id.matches('^[0-9a-fA-F]{24}$'))  // Magic regex

// No size limits defined
```

**Recommendation:**
1. Extract constants for all magic values
2. Define configuration properties for limits
3. Use enums for field names

```groovy
class DocumentConstants {
    static final String FIELD_DELETED_COUNT = "deletedCount"
    static final String OBJECTID_PATTERN = '^[0-9a-fA-F]{24}$'
    static final int MAX_DOCUMENT_SIZE_BYTES = 1_000_000
    static final String COLLECTION_NAME = "objectNodes"
}
```

### 3.3 Mixed Use of String Quotes

**Issue:** Inconsistent quote style (single vs double quotes).

**Evidence:**
```groovy
// Some files use single quotes
'organization is required'

// Others use double quotes
"objectNodes"
```

**Recommendation:**
Standardize on single quotes for Groovy (idiomatic) or double quotes (Java-style). Apply consistently across codebase.

### 3.4 No Logging Strategy

**Issue:** Minimal logging; hard to debug issues in production.

**Evidence:**
- Only one log statement in `DocumentServiceMongoDbImpl.groovy:43`
- No logging of errors or important business operations
- No correlation IDs for request tracking

**Recommendation:**
1. Add structured logging for all important operations
2. Log errors with context
3. Use MDC (Mapped Diagnostic Context) for correlation IDs
4. Add request/response logging filter (already exists for bodies, extend it)

```groovy
@Override
Mono<TransactionToken> openTransaction(Group group) {
    log.info("Opening transaction for org={}, group={}",
             group.organization(), group.group())

    return mongoTemplate.save(doc, COLLECTION_NAME)
        .doOnSuccess { log.info("Transaction created: id={}", transactionId) }
        .doOnError { log.error("Failed to create transaction", it) }
        .thenReturn(new TransactionToken(transactionId, secret, group))
}
```

---

## 4. Testing Gaps (Medium Priority)

### 4.1 Limited Unit Test Coverage

**Issue:** Only 2 unit test files; most testing is integration-level.

**Evidence:**
- `src/test/groovy/`: 2 files
- `src/integrationTest/groovy/`: 6 files
- No unit tests for services, controllers, filters

**Recommendation:**
1. Add unit tests for `TransactionService` logic (ID generation, validation)
2. Add unit tests for `DocumentServiceMongoDbImpl` (map conversions)
3. Mock dependencies to test business logic in isolation
4. Aim for 70%+ line coverage for business logic

**Example:**
```groovy
class TransactionServiceSpec extends Specification {

    def mongoTemplate = Mock(ReactiveMongoTemplate)
    def idGenerator = Mock(IdGenerator)
    def service = new TransactionService(mongoTemplate, idGenerator)

    def "should generate transaction ID with correct format"() {
        given:
        idGenerator.nextId() >> "abc123xyz"
        def group = new Group("org1", "group1")

        when:
        def result = service.nextId(group)

        then:
        result == "abc123xyz~org1~group1"
    }

    def "should reject commit of already committed transaction"() {
        given:
        def token = new TransactionToken("tx1", "secret1", group)
        mongoTemplate.findById(*_) >> Mono.just([
            _id: "tx1",
            secret: "secret1",
            commit: "existing-commit"  // Already committed
        ])

        when:
        def result = service.commitTransaction(token).block()

        then:
        thrown(IllegalStateException)
    }
}
```

### 4.2 No Performance/Load Testing

**Issue:** No tests for concurrent access, throughput, or scalability.

**Recommendation:**
1. Add JMeter or Gatling tests for load scenarios
2. Test concurrent transaction creation
3. Test MongoDB connection pool behavior under load
4. Establish baseline performance metrics

### 4.3 Missing Negative Test Cases

**Issue:** Tests mostly cover happy paths.

**Recommendation:**
Add tests for:
- Invalid JWT tokens
- Malformed JSON documents
- Concurrent modification scenarios
- MongoDB connection failures
- Large document handling
- Boundary conditions (empty strings, nulls, max sizes)

---

## 5. API Design Issues (Low Priority)

### 5.1 Inconsistent REST Patterns

**Issue:** Document API uses ID in path for POST, which is non-standard.

**Evidence:**
```groovy
// DocumentController.groovy:62
@PostMapping(value = "/{id}")  // POST should auto-generate ID
```

**Standard REST Pattern:**
- `POST /api/documents` - Create (server generates ID)
- `PUT /api/documents/{id}` - Update or Create (client provides ID)

**Recommendation:**
Consider two options:

**Option 1: Standard REST (recommended)**
```groovy
@PostMapping
Mono<ResponseEntity<ObjectNode>> createDocument(@RequestBody ObjectNode document) {
    String id = idGenerator.nextId()
    return documentService.create(id, document)
            .map(created -> ResponseEntity
                .created(URI.create("/api/documents/" + id))
                .body(created))
}

@PutMapping("/{id}")
Mono<ResponseEntity<ObjectNode>> upsertDocument(
        @PathVariable String id,
        @RequestBody ObjectNode document) {
    return documentService.upsert(id, document)
            .map(saved -> ResponseEntity.ok(saved))
}
```

**Option 2: Keep Current (if client-provided IDs are required)**
- Document this clearly in API documentation
- Explain why this pattern is used
- Ensure it's consistently applied

### 5.2 Missing HATEOAS Links

**Issue:** API responses don't include hypermedia links for resource navigation.

**Recommendation:**
Consider adding HATEOAS support for better API discoverability:

```groovy
@GetMapping("/{id}")
Mono<ResponseEntity<ObjectNode>> getDocument(@PathVariable String id) {
    return documentService.findById(id)
            .map(document -> {
                // Add hypermedia links
                document.put("_links", objectMapper.createObjectNode()
                    .put("self", "/api/documents/" + id)
                    .put("update", "/api/documents/" + id)
                    .put("delete", "/api/documents/" + id)
                    .put("collection", "/api/documents"))
                return ResponseEntity.ok(document)
            })
            .defaultIfEmpty(ResponseEntity.notFound().build())
}
```

### 5.3 No Pagination for List Operations

**Issue:** `GET /api/documents` returns all documents without pagination.

**Evidence:**
```groovy
// DocumentController.groovy:45
Flux<ObjectNode> listDocuments()  // No pagination parameters
```

**Recommendation:**
Add pagination support before dataset grows:

```groovy
@GetMapping
Flux<ObjectNode> listDocuments(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size,
        @RequestParam(required = false) String sortBy) {

    PageRequest pageRequest = PageRequest.of(page, size,
        sortBy != null ? Sort.by(sortBy) : Sort.unsorted())

    return documentService.findAll(pageRequest)
}
```

### 5.4 No API Versioning Strategy

**Issue:** No version prefix in API paths.

**Current:** `/api/documents`
**Recommended:** `/api/v1/documents`

**Recommendation:**
1. Add version prefix to all API endpoints
2. Document versioning strategy
3. Plan for future API changes

---

## 6. Architecture Improvements (Low Priority)

### 6.1 Missing Domain Model Layer

**Issue:** Controllers work directly with `ObjectNode` and `Map`.

**Observation:**
While flexibility is a design goal, consider adding a thin domain model for core entities:

```groovy
class Document {
    String id
    String name
    Map<String, Object> metadata
    Instant created
    Instant modified

    ObjectNode toObjectNode() { ... }
    static Document fromObjectNode(ObjectNode node) { ... }
}
```

**Benefits:**
- Type safety for common fields
- Business logic encapsulation
- Easier testing
- Better IDE support

**Trade-off:** Loses some flexibility. Consider if this aligns with your "flexible JSON" vision.

### 6.2 Service Interface Abstraction Unused

**Issue:** `DocumentService` interface exists but only one implementation.

**Evidence:**
```groovy
// DocumentService.groovy - Interface with single implementation
interface DocumentService { ... }

// DocumentServiceMongoDbImpl.groovy - Only implementation
```

**Recommendation:**
Either:
1. Remove the interface (YAGNI - You Aren't Gonna Need It) until multiple implementations exist
2. Keep it if FoundationDB implementation is planned soon
3. Document the intended abstraction strategy

### 6.3 Transaction Pattern Not Enforced

**Issue:** Transactions can be created but aren't required for document operations.

**Evidence:**
- Transaction tokens generated but never validated
- Documents can be created without transaction context
- No audit trail linking documents to transactions

**Recommendation:**
1. Decide if transactions are optional or mandatory
2. If mandatory, add transaction validation to document operations
3. If optional, document when they should be used
4. Consider adding audit log with transaction references

---

## 7. Configuration & DevOps

### 7.1 Missing Environment-Specific Profiles

**Issue:** Only one MongoDB profile; no dev/staging/prod separation.

**Recommendation:**
Create profiles for different environments:

```yaml
# application-dev.yml
spring:
  data.mongodb:
    host: localhost
    port: 27017
logging.level.org.jadetipi: DEBUG

# application-staging.yml
spring:
  data.mongodb:
    host: staging-mongo.example.com
    port: 27017
logging.level.org.jadetipi: INFO

# application-prod.yml
spring:
  data.mongodb:
    uri: ${MONGODB_URI}  # From environment
logging.level.org.jadetipi: WARN
```

### 7.2 Secrets in Configuration Files

**Issue:** Keycloak client secret in test code.

**Evidence:**
```groovy
// KeycloakTestHelper.groovy - secret hardcoded
CLIENT_SECRET = '7e8d5df5-5afb-4cc0-8d56-9f3f5c7cc5fd'
```

**Recommendation:**
1. Use environment variables for secrets
2. Use Spring Cloud Config or Vault for production
3. Never commit real secrets to version control
4. Rotate test secrets regularly

### 7.3 No Health Checks Beyond Actuator

**Issue:** No custom health indicators for critical dependencies.

**Recommendation:**
Add custom health checks:

```groovy
@Component
class MongoHealthIndicator implements HealthIndicator {

    private final ReactiveMongoTemplate mongoTemplate

    @Override
    Health health() {
        try {
            mongoTemplate.mongoDatabase.block()
                .runCommand(new Document("ping", 1))
                .block()
            return Health.up().build()
        } catch (Exception e) {
            return Health.down(e).build()
        }
    }
}
```

---

## 8. Documentation

### 8.1 Missing API Documentation Details

**Issue:** Swagger annotations minimal; no request/response examples.

**Recommendation:**
Enhance OpenAPI documentation:

```groovy
@Operation(
    summary = "Create a new document",
    description = "Creates a new JSON document with the specified ID",
    responses = [
        @ApiResponse(responseCode = "201", description = "Document created"),
        @ApiResponse(responseCode = "409", description = "Document already exists"),
        @ApiResponse(responseCode = "400", description = "Invalid document")
    ]
)
@PostMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
Mono<ResponseEntity<ObjectNode>> createDocument(
    @Parameter(description = "Unique document identifier") @PathVariable String id,
    @Parameter(description = "Document content as JSON") @RequestBody ObjectNode document
)
```

### 8.2 No Architecture Decision Records (ADRs)

**Recommendation:**
Document key decisions in `docs/adr/`:
- Why Groovy for backend?
- Why reactive programming?
- Why MongoDB over SQL?
- Why custom ID generation?
- Why transaction token pattern?

### 8.3 Missing Developer Guide

**Recommendation:**
Create `CONTRIBUTING.md` with:
- How to set up development environment
- How to run tests
- Code style guidelines
- Git workflow
- How to add new endpoints
- How to add new database collections

---

## 9. Specific Code Improvements

### 9.1 `DocumentServiceMongoDbImpl` Improvements

```groovy
// Current: Mixing low-level and high-level API
Mono.from(
    mongoTemplate.mongoDatabase
        .map(db -> db.getCollection(COLLECTION_NAME))
        .flatMapMany(collection -> collection.find(new Document("_id", idValue)))
)

// Recommended: Use ReactiveMongoTemplate consistently
mongoTemplate.findById(id, Map.class, COLLECTION_NAME)
    .map(map -> {
        map.put("_id", map.get("_id").toString())
        return objectMapper.convertValue(map, ObjectNode.class)
    })
```

**Why:** Simpler, more maintainable, leverages Spring Data abstractions.

### 9.2 `TransactionController` Validation Cleanup

**Remove:**
```groovy
// Lines 45-50, 60-72 - Manual validation
if (!group?.organization()?.trim()) {
    return Mono.error(new ResponseStatusException(...))
}
```

**Add:**
```groovy
@Valid @RequestBody Group group
```

**Add global handler:**
```groovy
@RestControllerAdvice
class ValidationExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException)
    fun handleValidation(ex: MethodArgumentNotValidException): Mono<ErrorResponse>
}
```

### 9.3 `EndpointsController` Refactoring

**Issue:** `mappings()` method is complex and mixes concerns.

**Recommendation:**
Extract `ApplicationEndpoints` logic into a service:

```groovy
@Service
class EndpointMappingService {
    String getFormattedMappings(String actuatorUrl, List<String> packages)
    List<EndpointInfo> getRawMappings(String actuatorUrl, List<String> packages)
}

@RestController
class EndpointsController {
    private final EndpointMappingService mappingService

    @GetMapping("/")
    String mappings() {
        return mappingService.getFormattedMappings(actuatorUrl, [basePackage])
    }
}
```

---

## 10. Quick Wins (Easy Improvements)

### Priority 1: Add These Today
1. ✅ Add `@Valid` to all `@RequestBody` parameters
2. ✅ Create global `@RestControllerAdvice` exception handler
3. ✅ Add constants for magic strings and numbers
4. ✅ Add logging to critical operations

### Priority 2: This Week
5. ✅ Add pagination to document listing
6. ✅ Add request size limits
7. ✅ Create error response DTO
8. ✅ Add unit tests for services
9. ✅ Document API endpoints with Swagger annotations

### Priority 3: This Sprint
10. ✅ Implement JWT claim-based authorization
11. ✅ Add environment-specific profiles
12. ✅ Create architecture decision records
13. ✅ Add custom health indicators
14. ✅ Write developer guide

---

## 11. Positive Observations

### What's Working Well

1. **Reactive Architecture**: Proper use of Mono/Flux throughout
2. **Security Foundation**: JWT integration is solid
3. **Code Organization**: Clear package structure
4. **Integration Tests**: Good coverage of end-to-end scenarios
5. **Modern Stack**: Up-to-date Spring Boot, Java 21, Groovy 4
6. **Documentation**: Swagger/OpenAPI integration is good
7. **Logging Filters**: Request body logging is helpful for debugging
8. **ID Generation**: Custom IdGenerator is thread-safe and well-designed
9. **Docker Setup**: docker-compose makes local development easy
10. **License Headers**: Consistent copyright headers

---

## 12. Recommended Next Steps

### Immediate (Next Week)
1. Implement global exception handler
2. Add validation annotations to controllers
3. Add request size limits
4. Increase logging coverage

### Short-term (Next Month)
1. Add unit tests for all services
2. Implement JWT-based authorization
3. Add pagination to list endpoints
4. Create environment-specific configurations
5. Document API with detailed Swagger annotations

### Medium-term (Next Quarter)
1. Implement transaction enforcement on documents
2. Add performance/load testing
3. Create developer guide
4. Set up CI/CD pipeline
5. Add monitoring and alerting
6. Consider domain model layer

### Long-term (Future Versions)
1. API versioning strategy
2. HATEOAS support
3. GraphQL consideration
4. Multi-tenancy support
5. Audit logging system
6. Search/query capabilities

---

## 13. Conclusion

Jade-Tipi demonstrates strong architectural foundations and modern development practices. The reactive approach, security implementation, and test coverage show careful planning. With the improvements outlined above—particularly around validation, error handling, and documentation—this will be a robust production-ready system.

The codebase is clean, well-organized, and shows promise for the future. Focus on standardizing patterns (especially error handling and validation) and increasing test coverage in the short term.

**Key Takeaway:** You're on the right track. Address the critical issues (validation, error handling, authorization) first, then enhance testing and documentation. The foundation is solid.

---

## Appendix: Code Examples Repository

For complete code examples of the recommended improvements, see:
- Global Exception Handler example (Section 1.2)
- Validation improvements (Section 1.1)
- Logging strategy (Section 3.4)
- Unit test examples (Section 4.1)
- Health indicator example (Section 7.3)

---

**Review Completed:** 2025-11-08
**Next Review Recommended:** After implementing critical fixes (1-2 weeks)
