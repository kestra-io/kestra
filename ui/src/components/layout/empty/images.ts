import {type MaybeRefOrGetter, type Ref, ref, toValue, watchEffect} from "vue"

/**
 * Lazy, one-request-per-type: eagerly importing all illustrations (the previous shape of this module)
 * made every `<Empty>`/`useEmptyImage` caller download the entire ~1.5MB set on module load, regardless
 * of which single `type` it actually rendered.
 */
const modules = import.meta.glob<{default: string}>("../../../assets/empty_visuals/*.png")

/** Filename (without extension) per empty-state `type`; types without an entry fall back to the generic one. */
const FILES: Record<string, string> = {
    announcements: "announcement",
    apiTokens: "apiTokens",
    apps: "apps",
    assets: "assets",
    auditlogs: "auditLogs",
    blueprints: "blueprints",
    cases: "cases",
    concurrency_executions: "concurrencyExecutions",
    concurrency_limit: "concurrencyFlows",
    concurrency_limits: "concurrencyLimits",
    credentials: "credentials",
    dashboards: "dashboards",
    "dependencies.FLOW": "dependencies",
    "dependencies.EXECUTION": "dependencies",
    "dependencies.NAMESPACE": "dependencies",
    "dependencies.ASSET": "dependencies",
    groups: "groups",
    iam: "iam",
    instance: "instance",
    kill_switches: "killSwitch",
    mcpToolFlows: "mcpToolFlows",
    namespace: "namespace",
    namespaceFiles: "namespaceFiles",
    policies: "pluginDefaults",
    promote: "promote",
    quotas: "quotas",
    secrets: "secrets",
    tenants: "tenants",
    tests: "tests",
    testSuites: "testSuite",
    triggers: "triggers",
    variables: "variables",
    versionPlugin: "versionPlugin",
}

/** The resolved image URL for `type`, fetched on demand; `undefined` while loading or when `type` has no entry. */
export function useEmptyImage(type: MaybeRefOrGetter<string>): Ref<string | undefined> {
    const image = ref<string | undefined>()

    watchEffect((onCleanup) => {
        const file = FILES[toValue(type)]
        const loader = file ? modules[`../../../assets/empty_visuals/${file}.png`] : undefined
        if (!loader) {
            image.value = undefined
            return
        }

        // `type` can change before this resolves; a stale resolution must not clobber a newer one.
        let stale = false
        onCleanup(() => {
            stale = true
        })
        loader().then((mod) => {
            if (!stale) image.value = mod.default
        })
    })

    return image
}
