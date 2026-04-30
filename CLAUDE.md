@AGENTS.md

## UI Design System

All frontend work in `ui/` must use the design system at `ui/packages/design-system` before reaching for raw Element Plus primitives or writing custom components. The design system wraps Element Plus with the `kel` namespace and registers all components globally — use `Ks*` prefixed components everywhere.

### Rules

- **Never recreate** a component that already exists in the design system — check the lists below first.
- **Never hardcode** colors, font sizes, spacing, or border radii — use the CSS custom properties (`var(--ks-*)`) or SCSS variables.
- Prefer a thin wrapper or a new prop over forking a design system component.
- Use `var(--ks-*)` CSS custom properties in `<style>` blocks for automatic dark mode support.

### Components

**Basic / Layout**

| Component | Purpose |
|-----------|---------|
| `KsButton` / `KsButtonGroup` | Primary action button and grouped buttons |
| `KsIcon` / `KsIconButton` | Material Design icon display; icon-only button |
| `KsLink` | Styled hyperlink |
| `KsText` | Typography wrapper |
| `KsScrollbar` | Custom-styled scrollbar wrapper |
| `KsContainer` / `KsHeader` / `KsMain` | Page layout shell |
| `KsRow` / `KsCol` | Responsive grid |
| `KsSplitter` / `KsSplitterPanel` | Resizable split-pane layout |

**Feedback**

| Component | Purpose |
|-----------|---------|
| `KsAlert` | Alert banner for messages and status feedback |
| `KsDialog` | Modal dialog |
| `KsDrawer` | Side drawer/panel |
| `KsTooltip` | Hover tooltip |
| `KsPopover` | Popover for contextual content |
| `KsLoading` (`vKsLoading`) | Loading spinner directive |
| `KsMessage` | Toast notification service |
| `KsNotification` | Notification service |
| `KsMessageBox` | Confirmation dialog service |

**Form**

| Component | Purpose |
|-----------|---------|
| `KsInput` / `KsPassword` | Text and password inputs |
| `KsInputNumber` | Numeric input with increment/decrement |
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

**Data Display**

| Component | Purpose |
|-----------|---------|
| `KsCard` | Card container |
| `KsTable` / `KsTableColumn` | Basic table |
| `KsDataTable` / `KsFilter` / `KsBulkSelect` | Advanced data table with filtering, sorting, pagination, bulk actions |
| `KsBadge` | Small indicator badge |
| `KsTag` / `KsCheckTag` | Tag/label; clickable checkbox-style tag |
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
| `KsExecutionStatus` | Execution/task status badge with icon and color |
| `KsMarkdown` | Markdown renderer |

**Charts**

| Component | Purpose |
|-----------|---------|
| `KsEchart` | ECharts base wrapper |
| `KsLine` / `KsBar` / `KsPie` | Line, bar, and pie charts |
| `KsGraph` | Graph/network visualization |

**Navigation**

| Component | Purpose |
|-----------|---------|
| `KsTabs` / `KsTabPane` / `KsRouterTab` | Tabbed interface |
| `KsMenu` / `KsMenuItem` | Hierarchical menu |
| `KsDropdown` / `KsDropdownMenu` / `KsDropdownItem` | Dropdown menu |
| `KsTopNavBar` | Top navigation bar |
| `KsBreadcrumb` / `KsBreadcrumbItem` | Breadcrumb navigation |
| `KsSteps` / `KsStep` | Step/wizard progress indicator |

**Kestra-specific**

| Component | Purpose |
|-----------|---------|
| `KsTaskIcon` | Plugin task icon resolver |

### Utilities (import from the design system)

- `State`, `STATES`, `LOG_LEVELS` — execution state constants, icons, and colors
- `cssVar(name, opacity?)` — read a `--ks-*` CSS custom property at runtime
- `dateUtils` — `dateFilter()`, `DATE_FORMAT_STORAGE_KEY`, `TIMEZONE_STORAGE_KEY`
- `durationUtils` — `duration()`, `humanDuration()` — ISO 8601 ↔ ms and human-readable
- `stringUtils` — `afterLastDot()`
- `flowYamlUtils` — YAML parsing/manipulation for flow definitions
- `Comparators` — enum of filter comparison operators
- Filter helpers — `decodeSearchParams()`, `encodeFiltersToQuery()`, `getUniqueFilters()`, etc.
- `applyDefaultFilters()`, `useRouteFilterPolicy()` — filter composables
- `setMomentInstance()`, `setDateFormatter()` — date library configuration
- `designSystemLocale`, `setDesignSystemLocale`, `registerDesignSystemI18n` — i18n

### Composables

- `useTheme()` — detects and tracks dark/light mode via MutationObserver
- `useFilters`, `useSavedFilters`, `useDefaultFilter`, `usePreAppliedFilters`, `useRouteFilterPolicy`, `useTableColumns`, `useDataOptions`, `useDragAndDrop`, `usePeriodicRefresh` — data table filter composables

### Design tokens

Prefer `var(--ks-*)` CSS custom properties (auto dark/light) over SCSS variables in component `<style>` blocks:

- **Backgrounds:** `--ks-background-*`
- **Borders:** `--ks-border-*`
- **Text:** `--ks-text-*`
- **Status/badge colors:** `--ks-*` variants for each execution state
- **Charts:** `--ks-chart-*`

When SCSS variables are needed (e.g. in mixins):

- **Brand:** `$base-purple-500` (primary, `#8405FF`)
- **Status:** `$base-green-500` (success), `$base-red-500` (danger), `$base-orange-500` (warning), `$base-blue-500` (info)
- **Grays:** `$base-gray-50` … `$base-gray-950`
- **Typography:** `$font-family-sans-serif` (Public Sans), `$font-family-monospace` (Source Code Pro)
- **Font sizes:** `$font-size-xs` / `sm` / `md` / `lg` / `xl` / `2xl` / `3xl` / `4xl`
- **Radii:** `$border-radius` (0.25rem), `$border-radius-sm` (0.15rem), `$border-radius-lg` (0.5rem)
