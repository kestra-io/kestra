import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import ContentCopy from "vue-material-design-icons/ContentCopy.vue"
import CheckCircle from "vue-material-design-icons/CheckCircle.vue"
import {i18nMount} from "../../tests/unit/i18nMount"
import {memoryRouter} from "../../tests/unit/memoryRouter"
import DocIdDisplay from "./DocIdDisplay.vue"

const {docStore} = vi.hoisted(() => ({docStore: {docId: "task-runner"}}))

vi.mock("../stores/doc", () => ({useDocStore: () => docStore}))

async function mountWithQuery(query: Record<string, string | null>) {
    const router = memoryRouter()
    await router.push({path: "/", query})
    return i18nMount(DocIdDisplay, {global: {plugins: [router]}})
}

describe("DocIdDisplay", () => {
    let writeText: ReturnType<typeof vi.spyOn>

    beforeEach(() => {
        writeText = vi.spyOn(navigator.clipboard, "writeText").mockResolvedValue(undefined)
    })

    afterEach(() => {
        writeText.mockRestore()
        vi.useRealTimers()
    })

    it("renders nothing without the showDocId query parameter", async () => {
        const wrapper = await mountWithQuery({})

        expect(wrapper.find("button").exists()).toBe(false)
    })

    it("renders when showDocId is present with no value", async () => {
        const wrapper = await mountWithQuery({showDocId: null})

        expect(wrapper.find("button").exists()).toBe(true)
    })

    it("shows the doc id from the doc store", async () => {
        const wrapper = await mountWithQuery({showDocId: ""})

        expect(wrapper.find("button").text()).toBe("docId: task-runner")
    })

    it("copies exactly the displayed text on click", async () => {
        const wrapper = await mountWithQuery({showDocId: ""})

        await wrapper.find("button").trigger("click")

        expect(writeText).toHaveBeenCalledExactlyOnceWith("docId: task-runner")
    })

    it("swaps the copy icon for a tick after a click", async () => {
        const wrapper = await mountWithQuery({showDocId: ""})
        expect(wrapper.findComponent(ContentCopy).exists()).toBe(true)

        await wrapper.find("button").trigger("click")

        expect(wrapper.findComponent(CheckCircle).exists()).toBe(true)
        expect(wrapper.findComponent(ContentCopy).exists()).toBe(false)
    })

    it("reverts to the copy icon two seconds after a click", async () => {
        vi.useFakeTimers()
        const wrapper = await mountWithQuery({showDocId: ""})

        await wrapper.find("button").trigger("click")

        await vi.advanceTimersByTimeAsync(1999)
        expect(wrapper.findComponent(CheckCircle).exists()).toBe(true)

        await vi.advanceTimersByTimeAsync(1)
        expect(wrapper.findComponent(CheckCircle).exists()).toBe(false)
        expect(wrapper.findComponent(ContentCopy).exists()).toBe(true)
    })
})
