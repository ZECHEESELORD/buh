# Repository Guidelines

## Project Structure & Module Organization
- Production code lives in each module’s src/main/java under sh.harold.fulcrum.* with resources in src/main/resources.
- Tests mirror the package tree in src/test/java; keep integration fixtures (Redis, Mongo, configs) versioned beside the suite.
- Architecture notes live in docs/ and root Markdown files-revise them whenever protocols or message channels change.
- Build outputs stay inside module build/ directories; avoid committing .gradle/, run/, or shaded jars.

## Build, Test, and Development Commands
- All gradle commands must be run with a elevated shell.
- Test compilation with ./gradlew compileJava

## When Building Menus:
- Never, ever put in colour codes in the menu title.
- When creating menu items, you must do the following:
  - Items must have a clear and descriptive name using .name()
  - You must include a secondary info section that succinctly describes what that button does (e.g: Game Settings, Compendium, Gameplay Feature, Core Gameplay, etc.) using `.secondary()`
  - You must include a description of what it does, could be a witty sentence of the feature, could be actually descriptive. the system auto wraps long text. Use `.description()`
  - If there are any additional formatting requirements, such as lists or data that doesn't fit in the above three builder methods, you can use `.lore()`. Remember, body text is gray, and you may choose to highlight important arguments with other colors. 

## Coding Style & Naming Conventions
- Gradle toolchains default to Java 21; You must you use all the latest updated java 21 features to their fullest extent, such as record classes.
- Follow four-space indentation, same-line braces, and PascalCase types; packages remain sh.harold.<whatever>.
- Name tests *Test and mirror production packages; favor JUnit 5 annotations and AssertJ or JUnit assertions.
- Serialization and messaging code depend on Jackson and Lettuce-keep channel constants and payload contracts documented in message-bus-api.

## Testing Guidelines
- Run .\gradlew test before submitting; heavier suites may start Testcontainers for MongoDB or PostgreSQL, so keep timeouts reasonable.
- Use Mockito for doubles and constructor injection to keep tests isolated.
- For manual verification, ensure Redis is running locally before exercising cross-service flows with the runtime or registry components.
- Store shared fixtures or YAML templates under src/test/resources.

## Commit & Pull Request Guidelines
- Follow the type(scope): imperative summary format seen in the git log (e.g., feat(rank): add velocity rank utils).
- Keep commits atomic with clear bodies when several scopes change, and cap subject lines at roughly 72 characters.
- Pull requests should outline behavior changes, list validation steps (gradlew test, manual runs), and link issues or architecture notes.
- Attach logs or screenshots when altering console UX and document any new environment variables (REDIS_HOST, etc.).

## Architecture Principles:
> "As simple as possible, but not simpler"

- Be skeptical. Ensure provided prompt information is correct by reviewing the actual implementation and logic, especially for complex systems.
- KISS + Occam's Razor: The creation of any entity must justify it's existence.
- Pragmatism: A working solution is more important than "correct" architecture.
- Minimalism: only what is actually needed, not more.

## Notes:
- When making commands for minecraft servers, you MUST always use the latest paper API builder, never the outdated bukkit onCommand().
- You must strictly follow all the latest paper APIs when possible, never outdated bukkit or spigot solutions.
- any NMS implementation, must be abstracted. This ensures that it is easy to update.
- we are targetting the LATEST minecraft version, if we're working on servers, unless specified.
- Long-running tooling (tests, docker compose, migrations, etc.) must always be invoked with sensible timeouts or in non-interactive batch mode. Never leave a shell command waiting indefinitely-prefer explicit timeouts, scripted runs, or log polling after the command exits
- Prefer async-first implementations. When adding new storage, cache, or network integrations, expose `CompletionStage` APIs and run blocking work on our shared executors rather than the main thread. Assume agents will be asked to keep persistence non-blocking unless the path is explicitly marked "cold."

## When Writing Documentation or using facing messages:
- Use a somewhat playful, but professional tone. Play with clause lengths, use mixtures of parataxis and hypotaxis. make the writing INTERESTING to read.
- Avoid dashes (all kinds) whenever possible. use them only in specific cirumstances. Prefer usage of colons and semicolons.
- No emojis unless requested.
- Listing out headers/steps should be in the format of `Something:`
- - Prefer clear and concise ways of representing information, such as tables, lists, etc.