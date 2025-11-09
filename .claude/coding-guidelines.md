# Jade-Tipi Coding Guidelines

## MongoDB Field Naming Conventions

**IMPORTANT:** MongoDB documents must use simple lowercase field names, NOT camelCase.

### Examples:

✅ **Correct:**
```json
{
  "created": "2025-11-08T...",
  "committed": "2025-11-08T...",
  "commit": "abc123",
  "organization": "acme",
  "group": "research"
}
```

❌ **Incorrect:**
```json
{
  "createdAt": "2025-11-08T...",
  "committedAt": "2025-11-08T...",
  "commitId": "abc123"
}
```

### Rationale:
- Maintains consistency across the database layer
- Separates database conventions from application code conventions
- Improves readability in MongoDB queries and documents

## Background Process Management

**IMPORTANT:** When starting background processes for development/testing, always stop them when done.

### Guidelines:

- If you start the backend server with `./gradlew bootRun` in the background, stop it when the task is complete
- Use `KillShell` to terminate background shell processes
- Clean up any processes running on application ports (e.g., port 8765)
- Don't leave processes running between tasks unless explicitly requested by the user

### Example workflow:
1. Start process in background for testing
2. Complete the task
3. Stop the background process before finishing
4. Verify the port is free

## General Guidelines

- Use descriptive, simple field names
- Prefer shorter names when the context is clear (e.g., `created` over `created_at`)
- Be consistent within the same collection/document structure