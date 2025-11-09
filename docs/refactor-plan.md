# Termux App Refactor Plan

## Overview

This document captures a high-level view of the project structure and identifies suitable leaf nodes for the initial Kotlin migration.

## Module Structure

```mermaid
graph TD
    A[termux-app] --> A1[app]
    A[termux-app] --> B[termux-core]
    A[termux-app] --> C[termux-shared]
    A[termux-app] --> D[terminal-emulator]
    A[termux-app] --> E[terminal-view]

    subgraph App Module
        A1 --> A1a[src/main/java/com/termux/app]
        A1a --> A1a1[DriverActivity.java]
        A1a --> A1a2[BootstrapCommandExecutor.kt]
        A1a --> A1a3[TermuxShellEnvironment.kt]
        A1a --> A1a4[TermuxFileUtils.kt]
        A1a --> A1a5[Logger.kt]
        A1a --> A1a6[Error.kt]
        A1 --> A1b[src/main/res]
    end

    subgraph Termux Core
        B --> B1[com/termux/app]
        B1 --> B1a[TermuxService.java]
        B1 --> B1b[TermuxInstaller.java]
        B --> B2[src/main/cpp]
        B --> B3[src/main/res]
    end

    subgraph Termux Shared
        C --> C1[com/termux/shared/errors]
        C --> C2[com/termux/shared/logger]
        C --> C3[com/termux/shared/termux]
        C --> C4[com/termux/shared/shell]
    end

    subgraph Terminal Emulator
        D --> D1[com/termux/terminal]
    end

    subgraph Terminal View
        E --> E1[com/termux/view]
    end

    A1 -->|depends on| B
    A1 -->|depends on| C
    B -->|uses utils| C
    D -->|used by| B
    E -->|used by| B
```

## Leaf Nodes (Good First Migration Targets)

Start refactoring these files first; they have minimal inbound dependencies:

1. `app/src/main/java/com/termux/app/BootstrapCommandExecutor.kt`
2. `app/src/main/java/com/termux/app/TermuxShellEnvironment.kt`
3. `app/src/main/java/com/termux/app/TermuxFileUtils.kt`
4. `app/src/main/java/com/termux/app/Logger.kt`
5. `app/src/main/java/com/termux/app/Error.kt`
6. Utility classes under `termux-shared` used by a single module
7. Helper classes inside `termux-core` with limited cross-module usage

## Suggested Migration Flow

1. **Stabilize the build** (resolve file locks, update Gradle/Kotlin).
2. **Convert leaf nodes** to Kotlin and clean them up.
3. **Introduce Kotlin-first architecture** (ViewModels, coroutines/Flow).
4. **Gradually replace** `termux-core` dependencies with Kotlin equivalents if desired.
5. **Modernize UI/UX** (ViewBinding or Compose) and streamline async logic.
6. **Add tests & CI** to prevent regressions during the migration.


