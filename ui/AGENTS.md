# UI Design System Guidelines

Scope: this file applies to everything under `ui/`. AI coding agents (Claude Code, Cursor, etc.) load this file automatically when working in this directory; humans should treat it as the source of truth for frontend conventions in Kestra.

The Kestra design system lives at [ui/packages/design-system/](packages/design-system/) and is the **single source of truth** for every visual element of the product — colors, fonts, spacing, buttons, forms, dialogs, tables, charts, and so on. Anything rendered to a user must come from it.

## What this is, in plain terms

Think of the design system as the product's **visual vocabulary**:

- A short list of agreed-upon **colors**, **fonts**, and **spacings** (called *design tokens*).
- A library of pre-built **components** (`KsButton`, `KsTable`, `KsDialog`, …) that already use those tokens.
- A guarantee that anything built from these pieces will look right in **light mode and dark mode**, follow accessibility rules, and stay visually consistent with the rest of Kestra.

If a screen feels "off-brand," looks broken in dark mode, or every page styles the same control differently, it's almost always because someone bypassed the design system. The rules below exist to prevent that.

Under the hood, the design system wraps Element Plus under the `kel` namespace and globally registers every component with a `Ks*` prefix. You should almost never `import` from `element-plus` directly in `ui/src/`.

> **Note on `@kestra-io/ui-libs`:** The codebase may still contain imports from `@kestra-io/ui-libs`, the previous shared component library. That repository is sunsetting — all components have been migrated here into `ui/packages/`. Do not add new imports from `@kestra-io/ui-libs`; use `Ks*` components from the design system instead.

## Golden rules (non-negotiable)

These rules are what keep the UI maintainable as it grows. Treat any deviation as a bug.

1. **Use a `Ks*` component if one exists.** Check the tables below before writing anything custom or importing from `element-plus`. New screens that mix `<el-button>` and `<KsButton>` are a regression.
2. **Colors come from `--ks-*` tokens. Always.** No hex codes, no `rgb(...)`, no Element Plus tokens (`--el-*`), no Bootstrap variables, no SCSS color variables in component code. If the token you need does not exist, talk to design and add it to `ks-theme-light.scss` / `ks-theme-dark.scss` — do not pick a one-off color.
3. **Typography comes from `KsText` or typography tokens.** Use `<KsText>` (with `size`, `type`, `tag`, `truncated`, `lineClamp`) for body copy. For headings or one-off needs, use the `$font-family-*` and `$font-size-*` SCSS variables only inside the design-system package — feature code should not redefine them.
4. **No `:deep()` selectors.** Reaching into a child component's internals breaks encapsulation and silently shatters when the design system is upgraded. If you need to style something inside a `Ks*` component, add a prop, a slot, or a CSS variable to the component upstream.
5. **No SCSS variables (`$...`) in feature components.** Use `var(--ks-*)` CSS custom properties inside `<style>` blocks. SCSS variables don't react to dark mode, can't be overridden at runtime, and bind your component to a specific theme. SCSS variables are only acceptable inside `ui/packages/design-system/` itself, in mixins, or for math at build time.
6. **No magic numbers for theme values.** Spacing, radii, font sizes, and shadows must reference tokens or design-system SCSS variables — never `padding: 13px`, never `border-radius: 6px`. Use `rem` for sizing primitives and tokens for everything theme-aware.
7. **Never override Element Plus classes directly.** Don't write `.el-button { ... }` in feature code. If a `Ks*` component is missing a behavior, extend the component in the design system instead of patching CSS at the call site.
8. **Don't fork — extend.** If a `Ks*` component is *almost* what you need, add a prop or a slot to the component in `ui/packages/design-system/`. Copy-pasting the component into your feature folder is forbidden.
9. **Every new `Ks*` component needs a Storybook story and a unit test.** Stories double as living documentation for design and product reviewers.
10. **i18n keys live with the design system component**, not inside feature code, when they belong to the component (e.g. `KsEmpty`, `KsDurationPicker`). Register them via `registerDesignSystemI18n`.

## Best practices for keeping the design system healthy

A design system rots fast if it's treated as a one-time deliverable. Apply these rules every time you touch UI code or review a UI PR.

### Before you write code

- Search the component tables and Storybook first. The most common waste in this codebase is rebuilding something that already exists.
- If you can't find what you need, ask: is this a *missing component* (fix it in the DS) or a *missing prop on an existing component* (extend the DS)? Almost never the answer "build it locally."
- For anything visible to a user, check both light and dark mode in Storybook before merging.

### While you write code

- Build screens by *composing* `Ks*` components. A new feature should read like a list of design-system blocks plus business logic — not a wall of custom CSS.
- Keep `<style>` blocks small. If a component file has more than ~50 lines of CSS, you probably need a new prop, a new slot, or a new `Ks*` component.
- Prefer `scoped` styles and rely on design tokens for theming. If you find yourself writing `:deep(.el-...)`, stop — it's a signal the design system needs to expose something.
- Use semantic tokens, not raw colors. `var(--ks-content-link)` communicates intent; `var(--ks-content-blue-500)` does not exist for a reason.
- Co-locate component-specific tokens (e.g. `--ks-card-shadow`) in the component's SCSS, but always derive them from semantic tokens.

### When extending the design system

- Only expose props that are actually used somewhere in the codebase. Speculative props rot.
- Mirror Element Plus prop names where possible — predictability is a feature.
- Pass `v-bind="$attrs"` and forward slots so wrappers don't trap consumer extension points.
- Add the new component or prop to the relevant table in this file, plus a Storybook story and a unit test, in the same PR.
- Document tokens in code comments next to where they're declared in `ks-theme-*.scss`. The `scripts/generate-palette.mjs` file is auto-generated — don't hand-edit it.

### When reviewing a UI PR

Reject (or ask to fix) anything that:

- Imports from `element-plus` directly into `ui/src/`.
- Uses a hex code, `rgb(...)`, `--el-*`, or `--bs-*` for color.
- Uses `:deep()` to reach into a `Ks*` or `el-*` component.
- Hardcodes pixel values for padding, margin, radii, font sizes, or shadows.
- Adds a CSS class that overrides `.el-...` selectors.
- Duplicates a component that already exists in the design system.
- Adds a `Ks*` component without a Storybook story or test.

### Accessibility

- Every icon-only `KsIconButton` must have an accessible label (`aria-label` or `title`). Screen readers do not see the icon glyph.
- Never convey state with color alone — pair status colors with an icon (`KsExecutionStatus` already does this) or a text label.
- Use semantic HTML inside slots: real `<button>`, `<a>`, `<label>`, headings in document order. Don't fake interactivity with `<div @click>`.
- `KsDialog`, `KsDrawer`, `KsPopover` already manage focus trap and `Escape`-to-close — don't reimplement these in feature code.
- Keep tab order logical; rely on the DOM order rather than `tabindex` hacks.
- Color contrast comes for free as long as you use `--ks-content-*` against `--ks-background-*` pairings. If you mix-and-match, verify with the browser inspector.

### Internationalization

- No hardcoded user-facing strings. Always go through `t()` from `useI18n()`.
- Use `<i18n-t>` for plurals and interpolation — never string-concatenate.
- Format dates and times via `dateUtils` (which respects `TIMEZONE_STORAGE_KEY` and `DATE_FORMAT_STORAGE_KEY`); format durations via `durationUtils.humanDuration()`. Don't reach for `Intl.DateTimeFormat` directly.
- Strings owned by a `Ks*` component live in the design system's locale files and are registered via `registerDesignSystemI18n`. Strings owned by a feature live in that feature's locale files.

### Loading, empty, and error states

Every async surface must render all four states. "Happy path only" is a bug.

- **Loading:** `KsSkeleton` for content placeholders; `vKsLoading` directive for sections that already have layout; `KsLoading` component for full-page or container-level spinners.
- **Empty:** `KsEmpty` with an action where possible — never a blank screen.
- **Error:** `KsAlert type="error"` with retry affordance, or `KsMessage` for transient errors.
- **Success / data:** the actual content.

### Icons

- All icons come from [`vue-material-design-icons`](https://github.com/robcresswell/vue-material-design-icons) via `<KsIcon>` (or `<KsIconButton>` for clickable icons).
- Never inline raw SVG, font-icon classes, or emoji as UI state. If a needed icon is missing, propose adding it to the DS rather than dropping an SVG into a feature folder.
- Pass `name` (the kebab-case Material name); size and color come from props or the surrounding token context — don't override with inline `style`.

### Performance

- Lazy-load heavy surfaces: `KsEchart`, `KsLine`, `KsBar`, `KsPie`, `KsGraph`, `KsMarkdown`, code-editor surfaces. Use `defineAsyncComponent` or route-level code splitting.
- Prefer `v-show` for frequent toggles (tabs, filters), `v-if` for rare/heavy mounts (modals, big tables).
- Pass stable `key` props in lists. Avoid index-based keys when items have IDs.
- Don't render giant tables without `KsDataTable`'s pagination/virtualization — server-side paging is the default for anything that can grow.
- Watch out for `watch(..., { deep: true })` and `computed` with object identity — they often re-run more than you expect.

### Testing UI

- Unit tests with **Vitest** + `@vue/test-utils`, colocated next to the component.
- Use `data-test="..."` selectors for E2E tests with **Playwright**. Never select on `.el-*` or `.ks-*` class names — those are not stable contracts and will break on Element Plus / DS upgrades.
- Storybook stories cover: each variant prop, dark mode, edge cases (empty content, very long text, error state). A `*.stories.ts` file with one default story is not enough.
- Visual regressions caught in Storybook are cheaper to fix than caught in production.

### Deprecation contract

When retiring a `Ks*` component, prop, or token:

1. Mark with a `@deprecated` JSDoc tag *and* a one-line replacement path: `@deprecated since 0.x — use <KsNewThing> instead`.
2. Keep it working for at least one minor release; add a `console.warn` in dev mode if the cost is reasonable.
3. Migrate all callers in the same release where feasible — don't leave half-migrations.
4. Only delete after the deprecation window. A silent removal breaks downstream EE / plugin code.

## Anti-patterns (do not write these)

```vue
<!-- Wrong: raw element-plus, hex color, :deep, SCSS variable in feature code -->
<template>
    <el-button class="my-btn">Save</el-button>
</template>
<style lang="scss" scoped>
    .my-btn {
        background: #8405ff;
        font-size: $font-size-md;
    }
    :deep(.el-button__text) { color: white; }
</style>
```

```vue
<!-- Right: Ks component, semantic tokens, no deep selector, i18n -->
<template>
    <KsButton type="primary">{{ t("save") }}</KsButton>
</template>
<style lang="scss" scoped>
    /* Almost always: no custom CSS is needed at all. */
</style>
```

If your `<style>` block needs to exist:

```scss
/* Right: --ks-* tokens, no SCSS vars in feature code, no :deep */
.my-feature {
    background: var(--ks-background-card);
    color: var(--ks-content-primary);
    border: 1px solid var(--ks-border-primary);
}
```

## Components

### Basic / Layout

| Component | Purpose |
|-----------|---------|
| `KsButton` / `KsButtonGroup` | Primary action button and grouped buttons |
| `KsIcon` / `KsIconButton` | Material Design icon display; icon-only button (always with `aria-label`) |
| `KsLink` | Styled hyperlink |
| `KsText` | Typography wrapper — preferred over raw `<span>` / `<p>` for theme-aware text |
| `KsScrollbar` | Custom-styled scrollbar wrapper |
| `KsContainer` / `KsHeader` / `KsMain` | Page layout shell |
| `KsRow` / `KsCol` | Responsive grid |
| `KsSplitter` / `KsSplitterPanel` | Resizable split-pane layout |

### Feedback

| Component | Purpose |
|-----------|---------|
| `KsAlert` | Alert banner for messages and status feedback |
| `KsDialog` | Modal dialog (handles focus trap + Escape) |
| `KsDrawer` | Side drawer / panel |
| `KsTooltip` | Hover tooltip |
| `KsPopover` | Popover for contextual content |
| `KsLoading` (`vKsLoading`) | Loading spinner directive |
| `KsMessage` | Toast notification service |
| `KsNotification` | Notification service |
| `KsMessageBox` | Confirmation dialog service |

### Form

| Component | Purpose |
|-----------|---------|
| `KsInput` / `KsPassword` | Text and password inputs |
| `KsInputNumber` | Numeric input with increment / decrement |
| `KsSelect` / `KsOption` / `KsOptionGroup` | Dropdown select |
| `KsAutocomplete` | Autocomplete input with suggestions |
| `KsCheckbox` / `KsCheckboxGroup` / `KsCheckboxButton` | Checkbox variants |
| `KsRadio` / `KsRadioGroup` / `KsRadioButton` | Radio button variants |
| `KsSwitch` | Toggle switch |
| `KsDatePicker` / `KsTimePicker` | Date and time pickers |
| `KsColorPicker` | Color picker |
| `KsDurationPicker` | ISO 8601 duration picker |
| `KsCascaderPanel` | Cascading hierarchical selector |
| `KsUpload` | File upload |
| `KsForm` / `KsFormItem` | Form container with validation |

### Data Display

| Component | Purpose |
|-----------|---------|
| `KsCard` | Card container |
| `KsTable` / `KsTableColumn` | Basic table |
| `KsDataTable` / `KsFilter` / `KsBulkSelect` | Advanced data table with filtering, sorting, pagination, bulk actions |
| `KsBadge` | Small indicator badge |
| `KsTag` / `KsCheckTag` | Tag / label; clickable checkbox-style tag |
| `KsAvatar` | Avatar with fallback |
| `KsProgress` | Progress bar |
| `KsPagination` | Pagination controls |
| `KsEmpty` | Empty state placeholder |
| `KsSkeleton` | Skeleton loader |
| `KsId` | Copyable ID display |
| `KsDateAgo` | Relative time display ("2 hours ago") |
| `KsSegmented` | Segmented control |
| `KsCollapse` / `KsCollapseItem` | Collapsible sections |
| `KsTree` | Hierarchical tree view |
| `KsTimeline` / `KsTimelineItem` | Timeline visualization |
| `KsExecutionStatus` | Execution / task status badge with icon and color |
| `KsMarkdown` | Markdown renderer (lazy-load on heavy surfaces) |

### Charts

| Component | Purpose |
|-----------|---------|
| `KsEchart` | ECharts base wrapper (lazy-load) |
| `KsLine` / `KsBar` / `KsPie` | Line, bar, and pie charts (lazy-load) |
| `KsGraph` | Graph / network visualization (lazy-load) |

### Navigation

| Component | Purpose |
|-----------|---------|
| `KsTabs` / `KsTabPane` / `KsRouterTab` | Tabbed interface |
| `KsMenu` / `KsMenuItem` | Hierarchical menu |
| `KsDropdown` / `KsDropdownMenu` / `KsDropdownItem` | Dropdown menu |
| `KsTopNavBar` | Top navigation bar |
| `KsBreadcrumb` / `KsBreadcrumbItem` | Breadcrumb navigation |
| `KsSteps` / `KsStep` | Step / wizard progress indicator |

### Kestra-specific

| Component | Purpose |
|-----------|---------|
| `KsTaskIcon` | Plugin task icon resolver |

## Utilities (import from the design system)

- `State`, `STATES`, `LOG_LEVELS` — execution state constants, icons, and colors
- `cssVar(name, opacity?)` — read a `--ks-*` CSS custom property at runtime (use this in JS / chart configs instead of hardcoding hex)
- `dateUtils` — `dateFilter()`, `DATE_FORMAT_STORAGE_KEY`, `TIMEZONE_STORAGE_KEY`
- `durationUtils` — `duration()`, `humanDuration()` — ISO 8601 ↔ ms and human-readable
- `stringUtils` — `afterLastDot()`
- `flowYamlUtils` — YAML parsing / manipulation for flow definitions
- `Comparators` — enum of filter comparison operators
- Filter helpers — `decodeSearchParams()`, `encodeFiltersToQuery()`, `getUniqueFilters()`, etc.
- `applyDefaultFilters()`, `useRouteFilterPolicy()` — filter composables
- `setMomentInstance()`, `setDateFormatter()` — date library configuration
- `designSystemLocale`, `setDesignSystemLocale`, `registerDesignSystemI18n` — i18n

## Composables

- `useTheme()` — detects and tracks dark / light mode via MutationObserver. Use this instead of reading `document.documentElement` yourself.
- `useFilters`, `useSavedFilters`, `useDefaultFilter`, `usePreAppliedFilters`, `useRouteFilterPolicy`, `useTableColumns`, `useDataOptions`, `useDragAndDrop`, `usePeriodicRefresh` — data-table filter composables

## Design tokens

Tokens are CSS custom properties declared in [`ks-theme-light.scss`](packages/design-system/src/assets/styles/ks-theme-light.scss) and [`ks-theme-dark.scss`](packages/design-system/src/assets/styles/ks-theme-dark.scss). Each token is **semantic** — it describes *what the value means*, not what color it is. That is what makes dark mode and rebrands trivial.

**Always use `var(--ks-*)` in component `<style>` blocks** — not SCSS variables, not hex codes, not `--el-*`, not `--bs-*`.

Token families currently exposed:

- `--ks-background-*` — page, card, table-row, panel, input backgrounds, plus per-state backgrounds (`--ks-background-success`, `--ks-background-failed`, …)
- `--ks-border-*` — primary / secondary borders, plus per-state borders
- `--ks-content-*` — text colors (primary, inverse, link, link-hover, per-state)
- `--ks-button-*` — button background and content variants (primary / secondary / success, idle / hover / active, …)
- `--ks-badge-*`, `--ks-tag-*`, `--ks-card-*`, `--ks-dialog-*`, `--ks-dropdown-*`, `--ks-tooltip-*`, `--ks-select-*`, `--ks-scrollbar-*` — component-specific tokens
- `--ks-chart-*` — palette for charts; pair with `cssVar("--ks-chart-success")` in JS
- `--ks-editor-*`, `--ks-log-*`, `--ks-dependencies-*`, `--ks-playground-*`, `--ks-dots-*` — domain-specific surfaces

When a needed token is missing, **add it** to both `ks-theme-light.scss` and `ks-theme-dark.scss` (and review with design) rather than picking a raw color.

**SCSS variables — only inside `ui/packages/design-system/`, never in feature code:**

- **Brand:** `$base-purple-500` (primary, `#8405FF`)
- **Status palette:** `$base-green-500` (success), `$base-red-500` (danger), `$base-orange-500` (warning), `$base-blue-500` (info)
- **Grays:** `$base-gray-50` … `$base-gray-950`
- **Typography:** `$font-family-sans-serif` (Public Sans), `$font-family-monospace` (Source Code Pro)
- **Font sizes:** `$font-size-xs` / `sm` / `md` / `lg` / `xl` / `2xl` / `3xl` / `4xl`
- **Radii:** `$border-radius` (0.25rem), `$border-radius-sm` (0.15rem), `$border-radius-lg` (0.5rem)

These exist so the *design system itself* can compose tokens from a single palette. They are not API for feature code — feature code should reach the same values through `--ks-*` tokens.
