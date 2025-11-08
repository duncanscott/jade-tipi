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

## General Guidelines

- Use descriptive, simple field names
- Prefer shorter names when the context is clear (e.g., `created` over `created_at`)
- Be consistent within the same collection/document structure