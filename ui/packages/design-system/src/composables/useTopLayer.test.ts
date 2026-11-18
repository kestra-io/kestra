import {describe, test, expect, afterEach} from "vitest"
import {defineComponent, nextTick, type Ref} from "vue"
import {mount, type VueWrapper} from "@vue/test-utils"
import {useZIndex} from "element-plus"
import {useTopLayer} from "./useTopLayer"

const mounted: VueWrapper[] = []

function mountTopLayer() {
    let zIndex!: Ref<number>
    const Comp = defineComponent({
        setup() {
            zIndex = useTopLayer()
            return () => null
        },
    })
    mounted.push(mount(Comp))
    return zIndex
}

function openOverlay(): number {
    let overlayZIndex = 0
    const Overlay = defineComponent({
        setup() {
            overlayZIndex = useZIndex().nextZIndex()
            return () => null
        },
    })
    mounted.push(mount(Overlay))
    return overlayZIndex
}

describe("useTopLayer", () => {
    afterEach(() => {
        mounted.splice(0).forEach((wrapper) => wrapper.unmount())
    })

    test("is 0 before mount and a real z-index after", () => {
        let beforeMount: number | undefined
        const Comp = defineComponent({
            setup() {
                const zIndex = useTopLayer()
                beforeMount = zIndex.value
                return {zIndex}
            },
            render: () => null,
        })

        const wrapper = mount(Comp)
        mounted.push(wrapper)

        expect(beforeMount).toBe(0)
        expect(wrapper.vm.zIndex).toBeGreaterThan(0)
    })

    test("gives a later consumer a higher value than an earlier one", () => {
        const first = mountTopLayer()
        const second = mountTopLayer()

        expect(second.value).toBeGreaterThan(first.value)
    })

    test("claims a value above an Element Plus overlay opened before it", () => {
        const overlayZIndex = openOverlay()

        const zIndex = mountTopLayer()

        expect(zIndex.value).toBeGreaterThan(overlayZIndex)
    })

    test("keeps its value once mounted, even when other overlays open later", async () => {
        const zIndex = mountTopLayer()
        const claimed = zIndex.value

        openOverlay()
        await nextTick()

        expect(zIndex.value).toBe(claimed)
    })

    test("does not throw when mounted outside any overlay context", () => {
        expect(() => mountTopLayer()).not.toThrow()
    })
})
