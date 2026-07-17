import type {Component} from "vue"

export interface ExecutionExtraColumn {
    prop: string;
    /** i18n key, resolved by the consumer via `$t(col.label)`. */
    label: string;
}

// No-op in OSS. The EE build overrides this file (via the `override/` Vite alias)
// to contribute a Cases column and bulk actions to the Executions table.
export const extraColumns: ExecutionExtraColumn[] = []
export const cellComponents: Record<string, Component> = {}
export const bulkActionComponents: Component[] = []
