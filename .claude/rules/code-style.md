# Frontend & Code Style Rules

## TypeScript

- All frontend code must use strict TypeScript.
- Define interfaces for every API response and component prop — no `any`.

## React

- Write standard React; do NOT manually use `useMemo`, `useCallback`, or `React.memo`.
  The React Compiler handles optimizations automatically.
- Reusable UI logic lives in `src/components/`.
- Page-level composition lives in `src/pages/`.
- Use Redux Toolkit for complex global state (Cart, Auth, User Profile).

## Styling

- Mobile-first using Bootstrap's grid: `col-12 col-md-6`, etc.
- All admin/management pages must be fully usable on tablets and phones.
- Never use inline `style={{...}}` — use Bootstrap utility classes or SCSS.
- Override Bootstrap defaults via SCSS variables in `src/assets/scss/`.
- Test all components across breakpoints: xs, sm, md, lg, xl.
