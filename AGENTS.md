# AGENTS.md

Rules and principles for agents working on **this** project.

---

## 1. Core Rules

### 1.0 Document Conventions

When updating this document, preserve its existing purpose and append new information instead of rewriting unrelated sections.

### 1.1 Forbidden Patterns

The following are **strictly prohibited**:

- Hardcoded secrets, API keys, or credentials.
- Debug output in production code; use Android logging or an existing reporting mechanism only when appropriate.
- Wildcard imports.
- Committing local keystores, `signing.properties`, `build/` artifacts or generated APK/AAB files.
- Calling alarm, notification, permission or database APIs from UI composables directly; use the existing ViewModel, repository or service layers.

### 1.2 Security Rules

- Keep the app offline unless a requirement explicitly introduces networking.
- Never store plaintext passwords or signing credentials in source code or Gradle files committed to Git.
- Keep signing configuration in local `signing.properties` and the keystore outside version control unless the repository already tracks a dedicated development copy.
- Validate reminder and notification parameters before scheduling alarms or persisting them.
- Use Room parameterized DAO queries and validate data at repository/domain boundaries.
- Request Android permissions only when needed and handle denied, revoked or manufacturer-restricted states.
- Do not expose internal components or add exported activities, services or receivers without a clear requirement.
- Strip PII and unnecessary device details from logs.
- Preserve `allowBackup=false` and local-only data behavior unless explicitly changed.

### 1.3 Git Operations

- **Never rebase under any circumstance without explicit agreement from the user.** Never assume your decision is correct.
- **Never amend a commit.** Use additional commits instead.
- Never force push.
- **Skill execution is implied authorization.** When a skill is executing — standalone or as part of a chained workflow — the skill's own directives constitute the user's authorization for the git operations it performs (staging, committing, pushing, and opening a PR on the branch it created). Do not halt to request explicit agreement for these. The prohibitions above (rebase, amend, force-push) still apply and are never implied by skill execution.

### 1.4 Core Principles

- **DRY**: Extract repeated logic into functions or classes while preserving the existing separation between UI, domain, data and alarm components.
- **KISS**: Prefer simple, readable Kotlin and Compose code.
- **YAGNI**: Do not build features, abstractions or dependencies not required by the current specification.
- **No Unneeded Refactoring**: Do not refactor working code unless it directly fixes a bug or is required by the current change.
- **Single Responsibility**: Each module, class, and function must have one reason to change.
- **Open/Closed**: Extend behavior through composition and existing abstractions instead of modifying unrelated stable logic.
- **Dependency Inversion**: Inject Android and external dependencies through Hilt and repository/service interfaces where the existing architecture uses abstractions.
- **Immutability First**: Prefer using immutable collections and objects when possible.

## 2. Android Project Conventions

### 2.1 Architecture

- Keep source code in `app/src/main/java/com/raulh/randomreminders/`.
- Preserve package boundaries: `ui` for Compose and ViewModels, `domain` for models and scheduling rules, `data` and `data/db` for repositories and Room, `alarms` for Android alarm and notification integration, and `di` for Hilt modules.
- Keep platform and persistence work out of Composables. Composables should render state and send user actions to ViewModels.
- Expose UI state with immutable state models or `Flow`; avoid mutable state in shared ViewModels.
- Use Kotlin Coroutines for asynchronous work and ensure Room and alarm work does not block the main thread.

### 2.2 UI and Resources

- Use Jetpack Compose and Material 3.
- Use the existing theme and string resources; do not introduce hardcoded user-facing text or color values.
- Define visible text in `app/src/main/res/values/strings.xml` and drawable resources in `app/src/main/res/drawable/`.
- Account for minimum SDK 26 and Android API differences by checking version-dependent behavior at runtime.

### 2.3 Alarm and Notification Behavior

- Preserve local-only scheduling and Room persistence for pending reminder shots.
- Schedule at most the required next alarm and keep future shots persisted, as defined by the existing architecture.
- Re-register exact-alarm fallbacks and boot/time-change behavior through the existing scheduler and receivers.
- Handle exact-alarm denial and notification denial as expected by the app's permission fallback flows.
- Do not perform reminder side effects inside an idle or stale receiver without validating the persisted rule and pending shot.

### 2.4 Build and Dependencies

- Use Kotlin DSL and the Gradle Version Catalog in `gradle/libs.versions.toml`.
- Use JDK 17 and target Java/Kotlin 17 compatibility.
- Preserve `compileSdk 35`, `targetSdk 35` and `minSdk 26` unless a build change explicitly requires otherwise.
- Add dependencies only when needed and keep KSP/Hilt/Room plugin versions consistent through the catalog.
- Never edit generated files under `build/`.

### 2.5 Testing and Validation

- Add focused JUnit unit tests under `app/src/test/java/` for domain logic and ViewModel/form behavior.
- Use `kotlinx-coroutines-test` for coroutine tests.
- Run the smallest relevant test task first, then the standard build when the change affects compilation or resources:
  - `.\gradlew.bat :app:testDebugUnitTest`
  - `.\gradlew.bat :app:assembleDebug`
