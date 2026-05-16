# Testing Standards

## Commands

```bash
npm test        # frontend (Vitest)
./mvnw test     # backend (JUnit + Spring Test)
```

## Backend

- Integration tests must hit a real database, not mocks.
  Mocked tests have previously passed while prod migrations failed.
- Test reactive chains end-to-end using `StepVerifier`.

## Frontend

- Verify all UI changes across breakpoints (xs, sm, md, lg, xl) before marking done.
- Test the golden path and edge cases; watch for regressions in adjacent features.
