import type {Component} from "vue"

export interface ExecutionExtraColumn {
    prop: string;
    /** i18n key, resolved by the consumer via `t(col.label)`. */
    label: string;
    /** Whether this column is visible by default, before any user column-picker choice - see useTableColumns' ColumnConfig. */
    default: boolean;
}

// No-op in OSS. The EE build overrides this file (via the `override/` Vite alias)
// to contribute a Cases column and bulk actions to the Executions table. A function
// (not a static array) so EE can gate the result on its feature flag/RBAC state -
// an empty result keeps the column out of both the table and the column-picker.
export function getExtraColumns(): ExecutionExtraColumn[] {
    return []
}
export const cellComponents: Record<string, Component> = {}
export const bulkActionComponents: Component[] = []
