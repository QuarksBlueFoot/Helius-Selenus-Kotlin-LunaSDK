# LunaSDK 2026 Modern Android Architecture Dependencies

**Last Updated**: January 2026  
**Target**: Android API 21+ / Java 17 / Kotlin 2.3.0

---

## Overview

LunaSDK uses the **latest 2026 stable versions** of all dependencies to ensure:
- Maximum performance and security
- Modern Kotlin language features
- Full Android 15+ compatibility
- Production-ready stability

---

## Core Dependencies

### Kotlin Ecosystem

| Package | Version | Release Date | Notes |
|---------|---------|--------------|-------|
| **Kotlin** | 2.3.0 | Dec 16, 2025 | Latest stable - K2 compiler, improved performance |
| **kotlinx-serialization-json** | 1.10.0 | Jan 2026 | Compatible with Kotlin 2.3.0 |
| **kotlinx-coroutines-core** | 1.10.2 | Apr 2025 | Stable coroutines, virtual threads support |

### HTTP Client

| Package | Version | Release Date | Notes |
|---------|---------|--------------|-------|
| **OkHttp** | 5.3.2 | Nov 18, 2025 | Major upgrade from 4.x series |

#### OkHttp 5.x Highlights:
- ✅ Separate JVM and Android artifacts (better optimization)
- ✅ Happy Eyeballs RFC 8305 (faster IPv4/IPv6 connections)
- ✅ GraalVM native image support
- ✅ Improved Kotlin APIs (skip-the-builder pattern)
- ✅ Non-null `Response.body`
- ✅ Zstandard compression support

### Android Gradle Plugin

| Package | Version | Notes |
|---------|---------|-------|
| **AGP** | 8.7.3 | Latest stable Android Gradle Plugin |

---

## Version History

### v5.2.0 (Current Release)

Updated all dependencies to 2026 modern versions:

| Dependency | Previous | Updated | Change |
|------------|----------|---------|--------|
| Kotlin | 2.1.0 | **2.3.0** | +2 minor versions |
| kotlinx-serialization | 1.7.3 | **1.10.0** | +3 minor versions |
| kotlinx-coroutines | 1.10.2 | 1.10.2 | ✓ Already current |
| OkHttp | 4.12.0 | **5.3.2** | +1 major version |
| AGP | 8.7.3 | 8.7.3 | ✓ Already current |

---

## Compatibility Matrix

### Android SDK
- **Minimum SDK**: 21 (Android 5.0 Lollipop)
- **Target SDK**: 35 (Android 15)
- **Compile SDK**: 35

### Java
- **JVM Toolchain**: 17 (LTS)
- **Source Compatibility**: Java 17
- **Target Compatibility**: Java 17

### Kotlin
- **Language Version**: 2.3
- **API Version**: 2.3
- **K2 Compiler**: Enabled

---

## Dependency Sources

All dependencies are sourced from official repositories:

```kotlin
repositories {
    mavenCentral()      // Primary - all Kotlin and OkHttp artifacts
    google()            // Android SDK and AGP
}
```

---

## Security & Vulnerability Tracking

Dependencies are verified against:
- [NVD (National Vulnerability Database)](https://nvd.nist.gov/)
- [GitHub Security Advisories](https://github.com/advisories)
- [Snyk Vulnerability Database](https://security.snyk.io/)

### Known Vulnerabilities: **None** ✅

All current dependencies have no known CVEs as of January 2026.

---

## Upgrade Policy

LunaSDK follows a **continuous update policy**:

1. **Major versions**: Evaluated within 2 weeks of release
2. **Minor versions**: Adopted within 1 week of release
3. **Patch versions**: Adopted immediately for security fixes
4. **Security fixes**: Immediate adoption with hotfix release

---

## Build Verification

To verify your build uses correct dependencies:

```bash
# Check dependency tree
./gradlew :luna-sdk:dependencies

# Verify no outdated dependencies
./gradlew :luna-sdk:dependencyUpdates
```

---

## Contributing

When updating dependencies:

1. Check compatibility with Kotlin version
2. Run full test suite: `./gradlew test`
3. Update this document with new versions
4. Update version history table
5. Test on real Android devices (API 21-35)

---

## References

- [Kotlin Releases](https://github.com/JetBrains/kotlin/releases)
- [kotlinx.serialization Releases](https://github.com/Kotlin/kotlinx.serialization/releases)
- [kotlinx.coroutines Releases](https://github.com/Kotlin/kotlinx.coroutines/releases)
- [OkHttp Changelog](https://square.github.io/okhttp/changelogs/changelog/)
- [Android Gradle Plugin Releases](https://developer.android.com/build/releases/gradle-plugin)

---

*This document is auto-maintained as part of the LunaSDK release process.*
