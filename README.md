# global-materialization-workflows

> Reusable GitHub Actions workflows and actions for repository builds and software materializers.

### Implementation Status

---

`src/` contains a dummy Java entrypoint. Ignition, dispatch, source selection, and orchestration explicitly fail until materialization is implemented. The generic source-build workflow remains available.

There are no tests yet, so test results and coverage reports are not generated. The template’s quality gates remain configured for the future implementation.

The previous implementation is saved on GitHub at commit `06001dc8af0f98ebeaec4894b50267e62a2d0082` on `centralized-pipeline`.

### 1. Prerequisites

---

- macOS or Linux on ARM64 or x86-64, as configured in `flake.nix`.
- Install [Nix](https://nix.dev/install-nix).
- Windows: run `wsl --install` in administrator PowerShell; after restarting and completing Linux user setup, run `wsl -- bash -lc "<INSTALL_INSTRUCTION_HERE>"` and follow the [Nix Windows (WSL2) installation instructions](https://nix.dev/install-nix) to populate the placeholder.
- Network access for uncached toolchains and dependencies.

Run the commands below from the repository root.

Optional shortcut: configure `ub` as an alias for `./universal-build`:

- Zsh/Bash: add `alias ub='./universal-build'` to `~/.zshrc` or `~/.bashrc`.
- Windows PowerShell: add `function ub { wsl -- bash -lc 'exec ./universal-build "$@"' ub @args }` to `$PROFILE` (uses the default WSL distribution with Nix installed).

Restart your terminal after saving.

### 2. Build and Test

---

#### Universal Build Contract

Universal Build expects:

- `universal-build` at the project root.
- Any version of Nix on `PATH`.
- `flake.nix` at the project root.
- `flake.lock` at the project root, except for `clean` and initial `update-toolchain-lock`.
- A flake input named `nixpkgs`.
- A development shell named `buildtime`.
- `buildtime.BUILD_COMMAND` must contain the shell command to run for `build`.
- `BUILD_COMMAND` must resolve dependencies and enforce dependency locks.
- `BUILD_COMMAND` must compile the application as needed.
- `BUILD_COMMAND` must run tests and checks.
- `BUILD_COMMAND` must stage all deployable files to `build/release/idiomatic-artifacts/`.
- `buildtime.RELEASE_TYPE` set to `container` or `static`.
- `buildtime.UPDATE_IDIOMATIC_LOCK_COMMAND` must contain the shell command to refresh dependency locks for `update-idiomatic-lock`.
- `buildtime.IDE_PATH_VARS` listing space-separated environment variable names for `ide-paths`.
- Each variable listed in `IDE_PATH_VARS` to be defined in `buildtime`.
- A root `Dockerfile` for container releases.
- A flake package named `runtime` for container releases.
- The runtime package to provide executable named `ignite`.
- The runtime package to include all runtime dependencies and environment variables.
- `ignite` to accept the idiomatic artifacts directory as its first argument.
- `ignite` to execute the software's main() entrypoint based on the language being used.

For `build`, Universal Build recreates `build/release/` and archives its contents as `build/release-artifact.tar.gz`:

- `idiomatic-artifacts/`.
- `Dockerfile` for container releases.
- `runtime-artifacts/` for container releases.
- `symlink-to-absolute-ignite-script` pointing to the runtime package for container releases.

#### Everyday Commands

| Goal | Command |
| --- | --- |
| Build, test, check, and package | `./universal-build build` |
| Remove build outputs | `./universal-build clean` |
| Print IDE toolchain paths | `./universal-build ide-paths` |
| Update toolchain lock | `./universal-build update-toolchain-lock` |
| Refresh application dependency locks | `./universal-build update-idiomatic-lock` |

#### Outputs

- Test results: `build/quality-reports/tests/test/index.html`.
- Coverage: `build/quality-reports/jacoco/test/html/index.html`.
- Checkstyle and SpotBugs: `build/quality-reports/checkstyle/` and `build/quality-reports/spotbugs/`.
- Runnable application and dependencies: `build/release/idiomatic-artifacts/`.
- Release archive: `build/release-artifact.tar.gz`.

### 3. Development Workflows

---

#### Dependency Management

Edit `build.gradle.kts`:

- Add: `implementation("group:artifact:version")` (see appendix for all types of qualifiers).
- Remove: delete the declaration and its usages.
- Upgrade/pin: change the version, or the BOM/constraint that supplies it.

Refresh locks after adding, removing, or updating dependencies:

```sh
./universal-build update-idiomatic-lock
```

#### Toolchain Changes

| Change | Files and steps |
| --- | --- |
| Java version | Change `chosenJdk` in `flake.nix` and `JavaLanguageVersion.of(...)` in `build.gradle.kts` together. The runtime package uses the same JDK. |
| Add a CLI tool | Add its Nix package to the development shell's `packages` in `flake.nix`. |
| Nix package set | Change `inputs.nixpkgs.url` in `flake.nix`, then run `./universal-build update-toolchain-lock`. Review and commit `flake.nix` and `flake.lock`. |
| Gradle version | Select the Gradle major version in `chosenGradle` in `flake.nix`; `flake.lock` pins the exact release. |
| Build plugin or analysis tool | Change its plugin version or `toolVersion` in `build.gradle.kts`; regenerate idiomatic dependency locks if its resolved dependencies change. |

After toolchain changes, run `./universal-build build`, then run `./universal-build ide-paths` and update the IDE's JDK and Gradle paths.

### 4. IDE Setup

---

Print the pinned JDK and Gradle locations:

```sh
./universal-build ide-paths
```

Configure your IDE using these paths:

- [IntelliJ IDEA Ultimate setup](https://app.notion.com/p/f4c0a2b7875583b79fa5015d58412fea)
- [Visual Studio Code setup](https://app.notion.com/p/4fd0a2b787558204a6c001d0b034f698)

Reload Gradle after dependency changes. Reprint both paths after Nix/toolchain changes.

### 5. Concepts

---

- [Product Release Pipelines Across Repositories](https://app.notion.com/p/3f90a2b7875581ef953bca201941e0dd)
- [Deployment Testing and Promotion](https://app.notion.com/p/3f90a2b7875581de812ffaddc1e44b83)
- [Shutdown Concepts](https://app.notion.com/p/4080a2b78755818a93b6c7ffa13a809d)

### 6. Appendix

---

#### Gradle Dependency Reference

Use these inside `dependencies { ... }` in `build.gradle.kts`. Replace `...` with `"group:artifact:version"`; omit the version when a BOM supplies it.

| Declaration | Available when |
| --- | --- |
| `implementation(...)` | Compiling and running production code |
| `compileOnly(...)` | Compiling production code only |
| `runtimeOnly(...)` | Running production code only |
| `annotationProcessor(...)` | Processing annotations during production compilation |
| `testImplementation(...)` | Compiling and running tests |
| `testCompileOnly(...)` | Compiling tests only |
| `testRuntimeOnly(...)` | Running tests only |
| `testAnnotationProcessor(...)` | Processing annotations during test compilation |

These additionally require the `java-library` plugin, which this project does not apply:

| Declaration | Use |
| --- | --- |
| `api(...)` | A library dependency also exposed to consumers at compile time |
| `compileOnlyApi(...)` | Compile-only dependency also needed by consumers to compile |

Other dependency forms:

| Example | Use |
| --- | --- |
| `implementation(platform("group:bom:version"))` | Import a BOM's version constraints |
| `implementation(enforcedPlatform("group:bom:version"))` | Force the BOM's versions, including for consumers; use cautiously |
| `constraints { implementation("group:artifact:version") }` | Constrain a dependency's version without adding it |
| `implementation(project(":module"))` | Depend on another project in the same Gradle build |
| `implementation(files("libs/library.jar"))` | Depend on a local JAR; transitive dependencies are not declared for you |

Reference: [Java configurations](https://docs.gradle.org/current/userguide/java_plugin.html#sec:java_plugin_and_dependency_management), [library API configurations](https://docs.gradle.org/current/userguide/java_library_plugin.html#sec:java_library_configurations_graph), [dependency types](https://docs.gradle.org/current/userguide/declaring_dependencies_basics.html).

### License

---

All rights reserved. See [LICENSE](LICENSE).
