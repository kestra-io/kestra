import {describe, expect, test, vi} from "vitest"
import {defineComponent, ref} from "vue"
import {mount, flushPromises} from "@vue/test-utils"

import {useEmptyImage} from "../../../../../src/components/layout/empty/images"

function mountWithType(initial: string) {
    const type = ref(initial)
    let image: ReturnType<typeof useEmptyImage>
    const Comp = defineComponent({
        setup() {
            image = useEmptyImage(() => type.value)
            return () => null
        },
    })
    const wrapper = mount(Comp)
    return {wrapper, type, image: () => image}
}

describe("useEmptyImage", () => {
    test("resolves the file mapped to a known type", async () => {
        const {image} = mountWithType("assets")

        await vi.waitFor(() => expect(image().value).toBeDefined())

        expect(image().value).toContain("assets")
    })

    test("stays undefined for a type with no mapped illustration", async () => {
        const {image} = mountWithType("not_a_real_empty_state_type")

        await flushPromises()

        expect(image().value).toBeUndefined()
    })

    test("switches to the new type's image when type changes, without going stale", async () => {
        const {type, image} = mountWithType("assets")
        await vi.waitFor(() => expect(image().value).toBeDefined())
        const assetsImage = image().value

        type.value = "triggers"
        await vi.waitFor(() => expect(image().value).not.toBe(assetsImage))

        expect(image().value).toContain("triggers")
    })
})
