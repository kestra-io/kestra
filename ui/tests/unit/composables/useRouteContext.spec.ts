import {beforeEach, afterEach, describe, expect, it} from "vitest"
import {defineComponent, h, nextTick, ref, type Ref} from "vue"
import {mount, VueWrapper} from "@vue/test-utils"
import useRouteContext from "../../../src/composables/useRouteContext"

function mountRouteContext(routeInfo: Ref<{title: string}>, embed = false) {
    return mount(defineComponent({
        setup() {
            useRouteContext(routeInfo, embed)
        },
        render: () => h("div"),
    }))
}

describe("useRouteContext", () => {
    let wrapper: VueWrapper

    beforeEach(() => {
        document.title = "Kestra EE"
    })

    afterEach(() => {
        wrapper?.unmount()
    })

    it("sets the title on mount", () => {
        wrapper = mountRouteContext(ref({title: "Initial"}))
        expect(document.title).toBe("Initial | Kestra EE")
    })

    it("updates the title when routeInfo.title changes after mount (async-loaded entity name)", async () => {
        const routeInfo = ref({title: "Loading"})
        wrapper = mountRouteContext(routeInfo)

        routeInfo.value = {title: "Loaded Entity"}
        await nextTick()

        expect(document.title).toBe("Loaded Entity | Kestra EE")
    })

    it("does not accumulate whitespace across repeated title changes", async () => {
        const routeInfo = ref({title: "First"})
        wrapper = mountRouteContext(routeInfo)

        routeInfo.value = {title: "Second"}
        await nextTick()
        routeInfo.value = {title: "Third"}
        await nextTick()

        expect(document.title).toBe("Third | Kestra EE")
    })

    it("does not touch document.title when embed is true", () => {
        wrapper = mountRouteContext(ref({title: "Ignored"}), true)
        expect(document.title).toBe("Kestra EE")
    })

    it("does not double the pipe when the base title starts with '|' (browser-trimmed leading space)", async () => {
        document.title = "| Kestra EE"
        const routeInfo = ref({title: "Default Dashboard"})
        wrapper = mountRouteContext(routeInfo)

        expect(document.title).toBe("Default Dashboard | Kestra EE")
    })
})
