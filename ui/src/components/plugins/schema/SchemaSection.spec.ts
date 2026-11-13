import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import SchemaSection from "./SchemaSection.vue"

const mountSection = (props: Record<string, unknown> = {}) =>
    i18nMount(SchemaSection, {
        props: {href: "prop", clickableText: "Properties", ...props},
        slots: {content: "<p>body</p>"},
    })

const isOpen = (wrapper: ReturnType<typeof mountSection>) => wrapper.find("details").attributes("open") !== undefined

describe("SchemaSection", () => {
    beforeEach(() => {
        window.history.replaceState(null, "", "/")
    })

    afterEach(() => {
        vi.restoreAllMocks()
    })

    test("is collapsed by default", () => {
        const wrapper = mountSection()

        expect(isOpen(wrapper)).toBe(false)
    })

    test("is expanded when initiallyExpanded is true", () => {
        const wrapper = mountSection({initiallyExpanded: true})

        expect(isOpen(wrapper)).toBe(true)
    })

    test("opens itself when the URL hash points at its body", () => {
        window.history.replaceState(null, "", "/#prop-body")

        const wrapper = mountSection()

        expect(isOpen(wrapper)).toBe(true)
    })

    test("toggling flips the state and emits expand only when it opens", async () => {
        const wrapper = mountSection()

        await wrapper.find("summary").trigger("click")
        expect(isOpen(wrapper)).toBe(true)
        expect(wrapper.emitted("expand")).toHaveLength(1)

        await wrapper.find("summary").trigger("click")
        expect(isOpen(wrapper)).toBe(false)
        expect(wrapper.emitted("expand")).toHaveLength(1)
    })

    test("opening sets the hash to the body anchor", async () => {
        const wrapper = mountSection()

        await wrapper.find("summary").trigger("click")

        expect(window.location.hash).toBe("#prop-body")
    })

    test("closing clears the hash through history.replaceState", async () => {
        const wrapper = mountSection({initiallyExpanded: true})
        window.history.replaceState(null, "", "/#prop-body")
        const replaceState = vi.spyOn(window.history, "replaceState")

        await wrapper.find("summary").trigger("click")

        expect(replaceState).toHaveBeenCalledWith(null, "", "/")
        expect(window.location.hash).toBe("")
    })

    test("noUrlChange suppresses the hash write and the replaceState call", async () => {
        const wrapper = mountSection({noUrlChange: true})
        const replaceState = vi.spyOn(window.history, "replaceState")

        await wrapper.find("summary").trigger("click")
        expect(isOpen(wrapper)).toBe(true)
        expect(window.location.hash).toBe("")

        await wrapper.find("summary").trigger("click")
        expect(isOpen(wrapper)).toBe(false)
        expect(replaceState).not.toHaveBeenCalled()
    })

    test("renders the chevron by default and omits it when arrow is false", () => {
        expect(mountSection().find(".collapse-button__chevron").exists()).toBe(true)
        expect(mountSection({arrow: false}).find(".collapse-button__chevron").exists()).toBe(false)
    })

    test("renders clickableText in the summary", () => {
        const wrapper = mountSection({clickableText: "Outputs"})

        expect(wrapper.find("summary").text()).toContain("Outputs")
    })
})
