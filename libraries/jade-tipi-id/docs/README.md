# 🪶 Jade-Tipi ID Validation Library

Part of **Jade-Tipi**, an open scientific metadata framework for globally unique, structured identifiers across
laboratories, instruments, and research systems.

This module provides a **validation layer** for Jade-Tipi IDs and their components, ensuring globally consistent,
sortable, and semantically clean identifiers.

---

## 📘 Overview

A **Jade-Tipi ID** is composed of eight components separated by `~`:

```
<prefix>~<timestamp>~<sequence>~<organization>~<group>~<uuid>~<type>~<subtype>
```

Example:

```
xsbxieljzowitdtf~1760287077097~aaab~jgi_lbl-gov~ii-pps~123e4567-e89b-12d3-a456-426614174000~ent~plate-384
```

These IDs ensure global uniqueness, lexical order within a millisecond, and consistent cross-system parsing.

| Component        | Description                                         | Allowed         | Max length |
|------------------|-----------------------------------------------------|-----------------|------------|
| **prefix**       | 16 lowercase random letters to prevent clustering   | `[a-z]{16}`     | 16         |
| **timestamp**    | Unix epoch in milliseconds                          | `[0-9]+`        | 14         |
| **sequence**     | Base-36 sequence within the same millisecond        | `[0-9a-z]+`     | 16         |
| **organization** | Lowercase slug from domain (may include `_` or `-`) | `[a-z0-9_-]+`   | 48         |
| **group**        | Lowercase team/group slug                           | `[a-z0-9_-]+`   | 48         |
| **uuid**         | Standard 36-char UUID (RFC 4122)                    | `[0-9a-fA-F-]+` | 36         |
| **type**         | Object type (e.g. `ent`, `ppy`, `lnk`, `grp`)       | `[a-z]+`        | 16         |
| **subtype**      | Sub-classification (slug, `-` only)                 | `[a-z0-9-]+`    | 30         |

The basic set of types are defined by the Jade-Tipi specification. This set may
be extended.

```text
entity       (ent)
property     (ppy)
link         (lnk)
group        (grp)
type         (typ)
validation   (val)
transaction  (txn)
task         (tsk)
procedure    (prc)
```

The subtype component is optional. The set of allowable subtypes is defined for each Jade-Tipi instance.

---

## 🧩 Packages and Classes

### `org.jadetipi.id.api.JadeTipiIdDto`

A record DTO annotated with **Jakarta Bean Validation** annotations for Spring Boot.  
Used to validate each Jade-Tipi ID component in REST endpoints or service APIs.

#### Example

```java
@PostMapping("/validate-id")
public ResponseEntity<String> validate(@Valid @RequestBody JadeTipiIdDto dto) {
    return ResponseEntity.ok("Valid ID: " + dto);
}
```

---

### `org.jadetipi.id.validation.Patterns`

A collection of reusable **regex constants** for core components (`PREFIX`, `TIMESTAMP`, `SEQUENCE`, `UUID36`, `TYPE`).

---

### `org.jadetipi.id.validation.Slug` and `SlugValidator`

A **custom Bean Validation constraint** for slug-like fields (organization, group, subtype).

Features:

- Ensures lowercase ASCII only.
- Must start with a letter.
- May contain digits.
- Allows `-` and `_` (or configurable separators).
- No consecutive separators or leading/trailing dashes.
- Configurable maximum length and separator policy.

**Annotation example:**

```java
@Slug(max = 48, separators = "-_", forbidMixedSeparators = false)
String organization;
```

---

### `org.jadetipi.id.validation.JadeTipiValidators`

A static **utility class** providing standalone validators for prefix, timestamp, sequence, UUID, and type.  
Useful for CLI tools, integration tests, or microservices that need light-weight validation without Spring Boot.

---

### `org.jadetipi.id.api.JadeTipiIdDtoSpec`

A **Spock test suite** validating the library’s rules using Jakarta Bean Validation.  
Covers success cases, field-specific failures, and edge cases (length, illegal chars, separator rules).

Run via Gradle:

```bash
./gradlew test
```

---

## ✅ Validation Example

### Input DTO

```json
{
  "prefix": "xsbxieljzowitdtf",
  "timestamp": "1760287077097",
  "sequence": "aaab",
  "organization": "jgi_lbl-gov",
  "group": "ii-pps",
  "uuid": "123e4567-e89b-12d3-a456-426614174000",
  "type": "ent",
  "subtype": "plate-384"
}
```

### Validated Response

All constraints pass, producing a valid 231-character ID:

```
xsbxieljzowitdtf~1760287077097~aaab~jgi_lbl-gov~ii-pps~123e4567-e89b-12d3-a456-426614174000~ent~plate-384
```

---

## 🧪 Run the tests

```bash
./gradlew clean test
```

Spock specs will report detailed constraint messages for failing DTOs, e.g.:

```
ConstraintViolation:
  propertyPath: organization
  message: "must not contain consecutive separators"
  invalidValue: "foo--bar"
```

---

## 🛠️ Dependencies

- **Java 21**
- **Spring Boot 3.3.x**
- **Jakarta Bean Validation (jakarta.validation)**
- **Spock 2.4 (Groovy 4)**
- **Gradle 8+**

---

## 📜 License

Dual-licensed under:

- **AGPL-3.0-only** for open-source use
- **Commercial License** for proprietary integrations

See `LICENSE` and `DUAL-LICENSE.txt`, or contact  
📧 **licensing@jade-tipi.org**

---

## 🪶 Author & Credits

**Duncan Scott** and **Jade-Tipi contributors**  
© 2025 Jade-Tipi Project — [https://jade-tipi.org](https://jade-tipi.org)
