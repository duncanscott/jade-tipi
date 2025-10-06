# Jade-Tipi SPDX Header Kit

This kit helps you add SPDX license headers to all `*.java`, `*.groovy`, and `*.kt` files.

## Quick start

```bash
# Copy the kit into your repo (recommended path)
cp -r jade-tipi-spdx-header-kit/. .spdx-kit/

# Dry-run to see what would change
.spdx-kit/scripts/apply-headers.sh . --dry-run --verbose

# Apply headers
.spdx-kit/scripts/apply-headers.sh . --verbose

# Commit the changes
git add -A
git commit -m "Add SPDX headers to source files"
```

## Pre-commit hook (optional)

```bash
git config core.hooksPath .githooks
cp .spdx-kit/.githooks/pre-commit .githooks/pre-commit
chmod +x .githooks/pre-commit
```

## Gradle enforcement (optional)

Add this to your Groovy `build.gradle` (requires Spotless plugin configured in your build):

```groovy
spotless {
    java {
        licenseHeaderFile '.spdx-kit/config/header-JavaLike.txt'
        target 'src/**/*.java'
    }
    groovy {
        licenseHeaderFile '.spdx-kit/config/header-JavaLike.txt'
        target 'src/**/*.groovy'
    }
    kotlin {
        licenseHeaderFile '.spdx-kit/config/header-JavaLike.txt'
        target 'src/**/*.kt'
    }
}
```

> If you’re not using Spotless yet, you can rely on the provided Python script.

## Notes
- The injector skips files that already contain an `SPDX-License-Identifier:` within the first ~1000 characters.
- Line endings are preserved (LF/CRLF).
- Adjust `--exclude-dirs` and `--exts` as needed.
