import type {Component} from "vue"

export interface ExecutionExtraColumn {
    prop: string;
    /** i18n key, resolved by the consumer via `t(col.label)`. */
    label: string;
    /** Whether this column is visible by default, before any user column-picker choice - see useTableColumns' ColumnConfig. */
    default: boolean;
    condition?: () => boolean;
}

// No-op in OSS. The EE build overrides this file (via the `override/` Vite alias)
// to contribute a Cases column and bulk actions to the Executions table. A function
// (not a static array) so EE can gate the result on its feature flag/RBAC state via
// each column's `condition`, and on the current page via `routeName` — `condition` runs
// inside a computed, where composables like `useRoute()` are unavailable.
export function getExtraColumns(_routeName?: string): ExecutionExtraColumn[] {
    return []
}
export const cellComponents: Record<string, Component> = {}
export const bulkActionComponents: Component[] = []

// No-op in OSS. EE contributes extra items to the execution overflow ("...") menu, which is
// reachable from every execution tab. Each component receives the `execution` prop and gates
// itself on its own feature flag / permissions. Rendered just before Delete, so the
// destructive action stays last.
export const overflowActionComponents: Component[] = []
