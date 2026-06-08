# Project Memory

Durable, human-written context about this project that isn't obvious from the
code or git history alone — feature design rationale, deliberate decisions,
conventions, and local-setup gotchas. Checked into the repo so the whole team
(and future AI sessions) share the same background.

- **[MEMORY.md](MEMORY.md)** — index; one line per note.
- One file per topic, with frontmatter (`name`, `description`, `type`).
  `[[name]]` links cross-reference other notes.

Types: `project` (ongoing work, goals, constraints), `reference` (external
links), `feedback` (how we like to work), `user` (who works here).

These mirror the assistant's working memory; when a note's facts change
(a file is renamed, a flag removed, a feature ships), update the note. Verify
anything a note claims against the current code before relying on it — notes
reflect what was true when written.
