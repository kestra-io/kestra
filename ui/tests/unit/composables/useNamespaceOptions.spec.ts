import {beforeEach, describe, expect, it, vi} from "vitest"
import {defineComponent} from "vue"
import {flushPromises, mount} from "@vue/test-utils"

const all = vi.hoisted(() => vi.fn())
const useNamespaces = vi.hoisted(() => vi.fn(() => ({all})))

vi.mock("../../../src/composables/useNamespaces", () => ({
    default: useNamespaces,
}))

import {useNamespaceOptions} from "../../../src/composables/useNamespaceOptions"

function mountComposable() {
    let result: ReturnType<typeof useNamespaceOptions>
    let initialNamespaces: string[] | undefined
    let initialLoading: boolean | undefined

    const wrapper = mount(defineComponent({
        setup() {
            result = useNamespaceOptions()
            initialNamespaces = [...result.namespaces.value]
            initialLoading = result.loading.value
            return () => null
        },
    }))

    return {
        result: result!,
        initialNamespaces: initialNamespaces!,
        initialLoading: initialLoading!,
        wrapper,
    }
}

describe("useNamespaceOptions", () => {
    beforeEach(() => {
        all.mockReset()
        useNamespaces.mockClear()
    })

    it("namespaces starts empty and loading starts false", () => {
        all.mockReturnValue(new Promise(() => {}))
        const {initialNamespaces, initialLoading} = mountComposable()

        expect(initialNamespaces).toEqual([])
        expect(initialLoading).toBe(false)
    })

    it("loading is true while the fetch is in flight", () => {
        all.mockReturnValue(new Promise(() => {}))
        const {result} = mountComposable()

        expect(result.loading.value).toBe(true)
    })

    it("fetched namespaces are mapped to their ids", async () => {
        all.mockResolvedValue([{id: "namespace-a"}, {id: "namespace-b"}])
        const {result} = mountComposable()
        await flushPromises()

        expect(result.namespaces.value).toEqual(["namespace-a", "namespace-b"])
    })

    it("error is set when the fetch rejects", async () => {
        all.mockRejectedValue(new Error("Fetch failed"))
        const {result} = mountComposable()
        await flushPromises()

        expect(result.error.value).toBe(true)
        expect(result.namespaces.value).toEqual([])
    })

    it("loading is cleared on both the success and the failure path", async () => {
        all.mockResolvedValueOnce([{id: "namespace-success"}])
        const success = mountComposable()
        expect(success.result.loading.value).toBe(true)
        await flushPromises()
        expect(success.result.loading.value).toBe(false)

        all.mockRejectedValueOnce(new Error("Network error"))
        const failure = mountComposable()
        expect(failure.result.loading.value).toBe(true)
        await flushPromises()
        expect(failure.result.loading.value).toBe(false)
    })

    it("the fetch asks for the documented page size", async () => {
        all.mockResolvedValue([])
        mountComposable()
        await flushPromises()

        expect(useNamespaces).toHaveBeenCalledWith(500)
    })
})
