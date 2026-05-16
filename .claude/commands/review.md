Review all changes on the current branch compared to main. For each changed file:

1. Check adherence to `.claude/rules/` — code style, API conventions, testing, and git policy.
2. Flag any blocking calls (`.block()`, JDBC/JPA), inline styles, raw SQL, or hardcoded values.
3. Verify reactive chains return `Mono`/`Flux` and controllers delegate to services.
4. Note missing Bean Validation, DTO mapping gaps, or unguarded endpoints.
5. Report: a concise list of issues (file + line) grouped by severity (blocker / warning / suggestion).
