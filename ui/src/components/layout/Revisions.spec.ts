import {describe, it, expect, vi} from "vitest"
import {nextTick} from "vue"
import en from "../../translations/en.json"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}}),
    useRouter: () => ({replace: vi.fn(), push: vi.fn()}),
}))

vi.mock("../../stores/flow", () => ({
    useFlowStore: () => ({
        flow: {id: "my-flow", namespace: "my-namespace"},
        deleteRevision: vi.fn(),
    }),
}))

vi.mock("../../composables/useEditorBindings", () => ({
    useEditorBindings: () => ({}),
}))

vi.mock("../../utils/toast", () => ({
    useToast: () => ({
        confirm: vi.fn(),
        success: vi.fn(),
        error: vi.fn(),
    }),
}))

import Revisions from "./Revisions.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"
import {flushPromises} from "@vue/test-utils"

const stubs = {
    KsSelect: {name: "KsSelect", props: ["modelValue"], template: "<div class=\"ks-select\"><slot /></div>"},
    KsOption: {name: "KsOption", props: ["value", "label"], template: "<div class=\"ks-option\" />"},
    KsButtonGroup: {name: "KsButtonGroup", template: "<div class=\"ks-button-group\"><slot /></div>"},
    KsButton: {name: "KsButton", template: "<button><slot /></button>"},
    KsEditor: {name: "KsEditor", template: "<div class=\"ks-editor-mock\" />"},
    KsNoData: {name: "KsNoData", props: ["title", "description"], template: "<div class=\"ks-no-data-mock\" />"},
}

const mockRevisions = [
    {revision: 2, updated: "2026-10-18T10:00:00Z", source: "id: flow-v2\n"},
    {revision: 1, updated: "2026-10-17T10:00:00Z", source: "id: flow-v1\n"},
]

const mountRevisions = (props: Record<string, unknown> = {}) =>
    i18nMount(Revisions, {
        locales: en,
        props: {
            lang: "yaml",
            revisions: mockRevisions,
            revisionSource: vi.fn().mockImplementation((rev: number) => Promise.resolve(`content of rev ${rev}`)),
            ...props,
        },
        global: {stubs},
    })

describe("Revisions.vue", () => {
    it("renders design system classes and no Bootstrap classes with revisions", async () => {
        const w = mountRevisions()
        await flushPromises()
        await nextTick()

        const header = w.find(".revision-header")
        expect(header.exists()).toBe(true)
        expect(header.classes()).not.toContain("d-flex")
        expect(header.classes()).not.toContain("justify-content-end")

        const displaySelect = w.find(".display-select")
        expect(displaySelect.exists()).toBe(true)
        expect(displaySelect.classes()).not.toContain("mb-3")

        const grid = w.find(".revision-grid")
        expect(grid.exists()).toBe(true)
        expect(grid.classes()).not.toContain("mb-2")

        const restoreTexts = w.findAll(".restore-text")
        expect(restoreTexts.length).toBeGreaterThan(0)
        for (const rt of restoreTexts) {
            expect(rt.classes()).not.toContain("d-none")
            expect(rt.classes()).not.toContain("d-lg-inline-block")
        }

        const editor = w.find(".revision-diff")
        expect(editor.exists()).toBe(true)
        expect(editor.classes()).not.toContain("mt-1")
    })

    it("renders no-data fallback when revisions list has <= 1 item", () => {
        const w = mountRevisions({revisions: [{revision: 1}]})
        expect(w.find(".ks-no-data-mock").exists()).toBe(true)
        expect(w.find(".revision").exists()).toBe(false)
    })
})
