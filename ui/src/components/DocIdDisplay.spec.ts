import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import DocIdDisplay from "./DocIdDisplay.vue"
import {useDocStore} from "../stores/doc"
import * as Utils from "../utils/utils"

let queryParams: Record<string, string | undefined> = {}

vi.mock("vue-router", () => ({
    useRoute: () => ({
        query: queryParams,
    }),
}))

describe("DocIdDisplay", () => {
    beforeEach(() => {
        vi.useFakeTimers()
        setActivePinia(createPinia())
        queryParams = {}
    })

    afterEach(() => {
        vi.useRealTimers()
        vi.restoreAllMocks()
    })

    test("nothing renders without the showDocId query parameter", () => {
        queryParams = {}
        const docStore = useDocStore()
        docStore.docId = "core.flow"

        const wrapper = mount(DocIdDisplay)

        expect(wrapper.find("button").exists()).toBe(false)
        expect(wrapper.html()).toBe("<!--v-if-->")
    })

    test("it renders when the parameter is present, even with no value", () => {
        queryParams = {showDocId: ""}
        const docStore = useDocStore()
        docStore.docId = "core.flow"

        const wrapper = mount(DocIdDisplay)

        expect(wrapper.find("button").exists()).toBe(true)
        expect(wrapper.find(".app-id-display-box").exists()).toBe(true)
    })

    test("the text is docId: <id> from the doc store", () => {
        queryParams = {showDocId: "true"}
        const docStore = useDocStore()
        docStore.docId = "io.kestra.plugin.core.log.Log"

        const wrapper = mount(DocIdDisplay)

        expect(wrapper.text()).toContain("docId: io.kestra.plugin.core.log.Log")
    })

    test("clicking copies that exact text", async () => {
        queryParams = {showDocId: "true"}
        const docStore = useDocStore()
        docStore.docId = "io.kestra.plugin.core.flow.If"
        const copySpy = vi.spyOn(Utils, "copy").mockResolvedValue(undefined)

        const wrapper = mount(DocIdDisplay)
        await wrapper.find("button").trigger("click")

        expect(copySpy).toHaveBeenCalledWith("docId: io.kestra.plugin.core.flow.If")
    })

    test("the tick replaces the copy icon after a click and reverts to the copy icon after two seconds", async () => {
        queryParams = {showDocId: "true"}
        const docStore = useDocStore()
        docStore.docId = "core.flow"
        vi.spyOn(Utils, "copy").mockResolvedValue(undefined)

        const wrapper = mount(DocIdDisplay)

        // Initial state: copy icon is rendered, tick is not
        expect(wrapper.find(".copy-button").exists()).toBe(true)

        // After click: tick replaces the copy icon
        await wrapper.find("button").trigger("click")
        expect(wrapper.find(".copy-button").exists()).toBe(false)

        // Before 2 seconds elapse: still shows tick
        vi.advanceTimersByTime(1999)
        await wrapper.vm.$nextTick()
        expect(wrapper.find(".copy-button").exists()).toBe(false)

        // At 2 seconds: reverts to the copy icon
        vi.advanceTimersByTime(1)
        await wrapper.vm.$nextTick()
        expect(wrapper.find(".copy-button").exists()).toBe(true)
    })
})
