import type {Component} from "vue"

// No-op in OSS. The EE build overrides this file (via the `override/` Vite alias)
// to render a linked-cases panel on the execution Overview tab.
export const executionOverviewPanel: Component | null = null
