# Git & Migration Policy

## Commits

- Do NOT add a `Co-Authored-By: Claude ...` trailer or any AI-attribution trailer.
  Commits attribute only the human author.
- Use Conventional Commits: `feat(scope):`, `fix(scope):`, `refactor(scope):`, etc.

## Flyway Migrations

- Do NOT create a new versioned migration file for every schema change.
- Update the existing migration file until it has been applied to a shared or
  production environment, then create the next versioned file.
