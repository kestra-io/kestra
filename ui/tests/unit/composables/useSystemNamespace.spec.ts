import {describe, test, expect, vi} from "vitest"

const store = vi.hoisted(() => ({setConfigs: (_: Record<string, unknown> | undefined) => {}}))

vi.mock("override/stores/misc", async () => {
    const {ref} = await import("vue")
    const configs = ref<Record<string, unknown> | undefined>(undefined)
    store.setConfigs = (value) => {
        configs.value = value
    }
    return {useMiscStore: () => ({get configs() {
        return configs.value
    }})}
})

import {useSystemNamespace, DEFAULT_SYSTEM_NAMESPACE} from "../../../src/composables/useSystemNamespace"

describe("useSystemNamespace", () => {
    test("returns the namespace configured by the backend", () => {
        // Given
        store.setConfigs({systemNamespace: "kestra.system"})

        // When / Then
        expect(useSystemNamespace().value).toBe("kestra.system")
    })

    test("falls back to system while the config has not loaded", () => {
        // Given — the router guard loads configs late, so components read it as undefined first
        store.setConfigs(undefined)

        // When / Then
        expect(useSystemNamespace().value).toBe(DEFAULT_SYSTEM_NAMESPACE)
    })

    test.each(["", "   "])("treats a blank configured value (%j) as absent", (blank) => {
        // Given — an empty namespace would build routes with an empty id
        store.setConfigs({systemNamespace: blank})

        // When / Then
        expect(useSystemNamespace().value).toBe(DEFAULT_SYSTEM_NAMESPACE)
    })

    test("tracks a config that arrives after the first read", () => {
        // Given
        store.setConfigs(undefined)
        const systemNamespace = useSystemNamespace()
        expect(systemNamespace.value).toBe(DEFAULT_SYSTEM_NAMESPACE)

        // When
        store.setConfigs({systemNamespace: "platform"})

        // Then
        expect(systemNamespace.value).toBe("platform")
    })
})
