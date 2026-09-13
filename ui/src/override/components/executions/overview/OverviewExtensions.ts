import type {Component} from "vue"

// No-op in OSS. The EE build overrides this file (via the `override/` Vite alias) to
// surface execution relations — linked cases today — as items in the Overview banner
// meta row. Each component receives the `execution` prop and gates itself on its own
// feature flag / permissions.
export const executionBannerRelations: Component[] = []
