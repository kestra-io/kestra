import {afterAll, afterEach, describe, expect, it, vi} from "vitest"
import {nextTick} from "vue"
import {mount} from "@vue/test-utils"

import TaskIcon from "./TaskIcon.vue"

type ObserverCallback = (entries: {target: Element; isIntersecting: boolean}[]) => void

const observers: {callback: ObserverCallback}[] = []

class FakeIntersectionObserver {
    constructor(callback: ObserverCallback) {
        observers.push({callback})
    }

    observe() {}
    unobserve() {}
    disconnect() {}
}

const MONOCHROME_CLS = "io.kestra.plugin.core.log.Log"

function mountMonochromeIcon() {
    return mount(TaskIcon, {
        props: {
            cls: MONOCHROME_CLS,
            onlyIcon: true,
            icons: {[MONOCHROME_CLS]: {flowable: false, monochrome: true, hasIcon: true, hash: "abcdef"}},
        },
    })
}

afterEach(() => {
    observers.length = 0
})

afterAll(() => {
    vi.unstubAllGlobals()
})

describe("TaskIcon", () => {
    it("should request the icon of a monochrome task only once it is on screen", async () => {
        vi.stubGlobal("IntersectionObserver", FakeIntersectionObserver)

        const wrapper = mountMonochromeIcon()
        await nextTick()

        const mask = wrapper.get(".task-icon__icon--mask")
        expect(mask.attributes("style")).toBeUndefined()

        observers.at(-1)!.callback([{target: wrapper.element, isIntersecting: true}])
        await nextTick()

        expect(mask.attributes("style")).toContain(`/plugins/icons/${encodeURIComponent(MONOCHROME_CLS)}/icon.svg`)
    })

    it("should render a monochrome icon eagerly when IntersectionObserver is unavailable", async () => {
        vi.stubGlobal("IntersectionObserver", undefined)

        const wrapper = mountMonochromeIcon()
        await nextTick()

        expect(wrapper.get(".task-icon__icon--mask").attributes("style")).toContain("icon.svg")
    })
})
