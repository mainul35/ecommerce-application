Fix the GitHub issue passed as the argument (e.g. `/project:fix-issue 42`).

Steps:
1. Read the issue body from GitHub: `gh issue view $ARGUMENTS`
2. Locate the relevant code — search by symbol, endpoint, or error message from the issue.
3. Reproduce the problem mentally; identify the root cause.
4. Implement the minimal fix that resolves the issue without introducing new abstractions.
5. Verify the fix does not break adjacent features.
6. Summarise what changed and why (one short paragraph).
