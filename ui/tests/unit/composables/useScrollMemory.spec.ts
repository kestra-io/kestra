import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {defineComponent, h, nextTick, ref} from "vue"
import {mount, type VueWrapper} from "@vue/test-utils"

type ScrollHandler = () => void

const scrollMock = vi.hoisted(() => ({handler: undefined as ScrollHandler | undefined}))

vi.mock("@vueuse/core", () => ({
    useScroll: (_target: unknown, options: {onScroll: ScrollHandler}) => {
        scrollMock.handler = options.onScroll
    },
    useThrottleFn: <Args extends unknown[], Result>(callback: (...args: Args) => Result) => callback,
    useWindowScroll: () => ({y: {value: 0}}),
}))

import {useScrollMemory} from "../../../src/composables/useScrollMemory"

const wrappers: VueWrapper[] = []

function mountScrollMemory(initialKey = "view") {
    const key = ref(initialKey)
    const element = ref<HTMLElement | null>(document.createElement("div"))
    const scrollTo = vi.fn()
    Object.defineProperty(element.value, "scrollTo", {value: scrollTo})

    let api!: ReturnType<typeof useScrollMemory>
    const Host = defineComponent({
        setup() {
            api = useScrollMemory(key, element)
            return () => h("div")
        },
    })

    wrappers.push(mount(Host))
    return {api, element, key, scrollTo}
}

async function runPendingTimers() {
    await nextTick()
    await nextTick()
    vi.runAllTimers()
}

describe("useScrollMemory", () => {
    beforeEach(() => {
        vi.useFakeTimers()
        sessionStorage.clear()
        scrollMock.handler = undefined
    })

    afterEach(() => {
        wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
        sessionStorage.clear()
        vi.useRealTimers()
    })

    it("writes the scroll position under the prefixed view key", async () => {
        const {element} = mountScrollMemory("flows")
        await runPendingTimers()

        element.value!.scrollTop = 128
        scrollMock.handler?.()

        expect(sessionStorage.getItem("scroll-flows")).toBe("128")
    })

    it("restores the stored position when the key changes", async () => {
        sessionStorage.setItem("scroll-details", "240")
        const {key, scrollTo} = mountScrollMemory("list")
        await runPendingTimers()
        scrollTo.mockClear()

        key.value = "details"
        await runPendingTimers()

        expect(scrollTo).toHaveBeenCalledWith({top: 240, behavior: "smooth"})
    })

    it("restores to zero when no position is stored", async () => {
        const {scrollTo} = mountScrollMemory("empty")

        await runPendingTimers()

        expect(scrollTo).toHaveBeenCalledWith({top: 0, behavior: "smooth"})
    })

    it("round-trips arbitrary data", () => {
        const {api} = mountScrollMemory()
        const value = {filters: ["running", "failed"], page: 3}

        api.saveData(value)

        expect(api.loadData()).toEqual(value)
    })

    it("keeps suffixed values under distinct keys", () => {
        const {api} = mountScrollMemory("executions")

        api.saveData("table", "-table")
        api.saveData("cards", "-cards")

        expect(sessionStorage.getItem("scroll-executions-table")).toBe(JSON.stringify("table"))
        expect(sessionStorage.getItem("scroll-executions-cards")).toBe(JSON.stringify("cards"))
        expect(api.loadData("-table")).toBe("table")
        expect(api.loadData("-cards")).toBe("cards")
    })

    it("returns the supplied default when nothing is stored", () => {
        const {api} = mountScrollMemory()
        const fallback = {page: 1}

        expect(api.loadData("-missing", fallback)).toBe(fallback)
    })
})
