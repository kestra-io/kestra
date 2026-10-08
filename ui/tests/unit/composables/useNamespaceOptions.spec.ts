import {describe, expect, it, vi} from "vitest"
import {defineComponent} from "vue"
import {flushPromises, mount} from "@vue/test-utils"

const all = vi.hoisted(() => vi.fn())

vi.mock("../../../src/composables/useNamespaces", () => ({
    default: () => ({all}),
}))

import {useNamespaceOptions} from "../../../src/composables/useNamespaceOptions"

function mountComposable() {
    let result!: ReturnType<typeof useNamespaceOptions>
    mount(defineComponent({
        setup() {
            result = useNamespaceOptions()
            return () => null
        },
    }))
    return result
}

describe("useNamespaceOptions", () => {
    it("maps the fetched namespaces to their ids and clears loading", async () => {
        all.mockResolvedValue([{id: "namespace-a"}, {id: "namespace-b"}])
        const result = mountComposable()
        expect(result.loading.value).toBe(true)

        await flushPromises()

        expect(result.namespaces.value).toEqual(["namespace-a", "namespace-b"])
        expect(result.loading.value).toBe(false)
    })

    it("sets error and clears loading when the fetch rejects", async () => {
        all.mockRejectedValue(new Error("unavailable"))
        const result = mountComposable()

        await flushPromises()

        expect(result.error.value).toBe(true)
        expect(result.loading.value).toBe(false)
    })
})
