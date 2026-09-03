# Upgrade Plan: study-agent (20260903054507)

- **Generated**: 2026-09-03
- **HEAD Branch**: N/A
- **HEAD Commit ID**: N/A

## Available Tools

**JDKs**
- JDK 17: not available (baseline will be skipped)
- JDK 21.0.12.1: `C:\Program Files\Java\jdk-21.0.12.1\bin` (available environment JDK)
- JDK 25.0.2: `C:\Users\Dell\.jdk\jdk-25\jdk-25.0.2\bin` (used by upgrade and final validation)

**Build Tools**
- Maven 3.9.16: `C:\Users\Dell\.maven\maven-3.9.16\bin` (used for compilation and tests)

## Guidelines

> Note: You can add any specific guidelines or constraints for the upgrade process here if needed, bullet points are preferred.

## Options

- Working branch: appmod/java-upgrade-20260903054507
- Run tests before and after the upgrade: true

## Upgrade Goals

- Java runtime and compiler target: 25 LTS

## Technology Stack

| Technology/Dependency | Current | Min Compatible Version | Why Incompatible |
| --------------------- | ------- | ---------------------- | ---------------- |
| Java | 17 | 25 | User requested Java 25 LTS |
| Maven | Not installed | 3.9+ | Required to compile and test the Maven project |
| maven-compiler-plugin | 3.11.0 | 3.11.0 | Compatible with Java 25 by delegating to the selected JDK |
| SQLite JDBC | 3.45.2.0 | 3.45.2.0 | No Java 25 incompatibility identified |
| Gson | 2.10.1 | 2.10.1 | No Java 25 incompatibility identified |
| JUnit Jupiter | 5.10.2 | 5.10.2 | No Java 25 incompatibility identified |

## Derived Upgrades

- Install JDK 25 because it is the requested target runtime and is not currently available.
- Install Maven 3.9+ because no Maven executable or wrapper is present; Maven is needed for required compile and test gates.
- Update all explicit Java compiler source and target settings from 17 to 25 so main and test code compile for the target runtime.
- No Kotlin, Spring, Jakarta EE, or framework migration is present, so no related derived upgrade is required.
- Version control is unavailable because the workspace is not a Git repository; changes will remain uncommitted.

## Impact Analysis

### Dependency Changes

| File | Dependency | Current | Action | Target | Reason |
|------|------------|---------|--------|--------|--------|
| pom.xml | maven.compiler.source | 17 | upgrade | 25 | Set Maven compiler source to the requested Java runtime |
| pom.xml | maven.compiler.target | 17 | upgrade | 25 | Set Maven compiler target to the requested Java runtime |
| pom.xml | maven-compiler-plugin `<source>` | 17 | upgrade | 25 | Align explicit plugin source configuration |
| pom.xml | maven-compiler-plugin `<target>` | 17 | upgrade | 25 | Align explicit plugin target configuration |

### Source Code Changes

| File | Location | Current | Required Change | Reason |
|------|----------|---------|----------------|--------|
| None | N/A | No Java 25-incompatible APIs or internal JDK imports identified in the targeted scan | No source changes | Existing code uses standard Java APIs and does not require a runtime migration rewrite |

### Configuration Changes

| File | Property/Setting | Current | Required Change | Reason |
|------|------------------|---------|----------------|--------|
| pom.xml | Maven compiler properties and plugin configuration | Java 17 | Change all four explicit compiler settings to 25 | Ensure consistent main and test compilation target |

### CI/CD Changes

| File | Location | Current | Required Change |
|------|----------|---------|----------------|
| None detected | N/A | No CI/CD configuration with a hardcoded Java version found | No change |

### Risks & Warnings

- **JDK 25 availability**: The target JDK is not installed. **Mitigation**: Install JDK 25 before modifying the project and use it for final compilation and tests.
- **No Maven wrapper**: Build reproducibility depends on the installed Maven 3.9+ tool. **Mitigation**: Install Maven and record its path in progress tracking.
- **Baseline unavailable**: JDK 17 is not installed, so the prescribed pre-upgrade baseline cannot run. **Mitigation**: Skip baseline per workflow rules and use the post-upgrade full compile/test gates.
- **Version control unavailable**: This directory is not a Git repository. **Mitigation**: Keep changes in the working directory and document the limitation; do not initialize Git.

## Upgrade Steps

- Step 1: Setup Environment
  - **Rationale**: Install the target JDK and Maven required for all executable verification.
  - **Changes to Make**: Install JDK 25 and Maven 3.9+.
  - **Verification**: List installed JDKs and Maven; expected JDK 25 and Maven 3.9+ are available.

- Step 2: Setup Baseline
  - **Rationale**: Establish pre-upgrade compilation and test status when the base JDK is available.
  - **Changes to Make**: None; JDK 17 is unavailable, so this step is skipped.
  - **Verification**: Record baseline as skipped because JDK 17 is unavailable.

- Step 3: Upgrade Java Compiler Target
  - **Rationale**: Apply the requested Java 25 runtime/compiler target while preserving dependencies and application behavior.
  - **Changes to Make**: Apply all Dependency Changes and Configuration Changes listed above in `pom.xml`.
  - **Verification**: Run `mvn clean test-compile -q` with JDK 25; main and test compilation must succeed.

- Step 4: Final Validation
  - **Rationale**: Confirm the target is met and the complete test suite passes.
  - **Changes to Make**: Resolve any Java 25 compilation or test failures found during validation; verify no temporary workarounds remain.
  - **Verification**: Run `mvn clean test-compile -q` and `mvn clean test -q` with JDK 25; expected 100% test pass rate.

- Step 5: CVE Validation & Fix
  - **Rationale**: Check direct dependencies for known vulnerabilities after the runtime upgrade.
  - **Changes to Make**: Scan `org.xerial:sqlite-jdbc:3.45.2.0`, `com.google.code.gson:gson:2.10.1`, and `org.junit.jupiter:junit-jupiter:5.10.2`; upgrade only vulnerable dependencies if reported.
  - **Verification**: Rebuild and rescan after any fixes; expected no unresolved fixable CVEs.

- Step 6: Final Reporting
  - **Rationale**: Record verification, coverage, risks, and the unversioned workspace state.
  - **Changes to Make**: Generate progress and summary artifacts; clean temporary files.
  - **Verification**: Confirm all applicable steps are complete and summary contains no unresolved placeholders.
