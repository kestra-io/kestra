import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {computed, reactive} from "vue"
import {type VueWrapper} from "@vue/test-utils"
import KestraDesignSystem, {KsDropdownItem} from "@kestra-io/design-system"

const publish = vi.fn()
const saveAsDraft = vi.fn()
const state = reactive({isDraft: false, haveChange: false, isCreating: false, hasErrors: false})

vi.mock("../../stores/flow", () => ({
    useFlowStore: () => state,
}))

vi.mock("./useFlowEditorActions", () => ({
    useFlowEditorActions: () => ({
        haveChange: computed(() => state.haveChange),
        canSave: computed(() => state.haveChange || state.isCreating),
        hasErrors: computed(() => state.hasErrors),
        isReadOnly: computed(() => false),
        isDraft: computed(() => state.isDraft),
        publish,
        saveAsDraft,
        saveAndExecute: vi.fn(),
    }),
}))

import FlowSaveActions from "./FlowSaveActions.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"

const messages = {publish: "Publish", save_draft: "Save draft", save_and_execute: "Save & Execute"}

let wrapper: VueWrapper | undefined

afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
})

function mainButton() {
    wrapper = i18nMount(FlowSaveActions, {messages, global: {plugins: [KestraDesignSystem]}})
    return wrapper.findAll("button").find(button => ["Publish", "Save draft"].includes(button.text().trim()))!
}

async function publishMenuItem() {
    await wrapper!.findAll("button").at(-1)!.trigger("mouseenter")
    const item = await vi.waitFor(() => {
        const found = wrapper!.findAllComponents(KsDropdownItem).find(candidate => candidate.text().startsWith("Publish"))
        if (!found) throw new Error("The dropdown menu did not open.")
        return found
    })
    return item.get("[role='menuitem']")
}

describe("FlowSaveActions", () => {
    beforeEach(() => {
        vi.clearAllMocks()
        Object.assign(state, {isDraft: false, haveChange: false, isCreating: false, hasErrors: false})
    })

    it.each([
        {flow: "a published flow", isDraft: false},
        {flow: "a draft", isDraft: true},
    ])("puts Save draft first with unsaved changes on $flow, and Publish next to it", async ({isDraft}) => {
        Object.assign(state, {isDraft, haveChange: true})

        const button = mainButton()
        await button.trigger("click")

        expect(button.text()).toBe("Save draft")
        expect(saveAsDraft).toHaveBeenCalledOnce()
        expect(publish).not.toHaveBeenCalled()

        await (await publishMenuItem()).trigger("click")
        expect(publish).toHaveBeenCalledOnce()
        expect(saveAsDraft).toHaveBeenCalledOnce()
    })

    it("puts Save draft first on a flow being created", () => {
        Object.assign(state, {isDraft: true, isCreating: true})

        expect(mainButton().text()).toBe("Save draft")
    })

    it("puts Publish first on a saved draft", async () => {
        state.isDraft = true

        const button = mainButton()
        await button.trigger("click")

        expect(button.text()).toBe("Publish")
        expect(publish).toHaveBeenCalledOnce()
    })

    it("keeps an invalid draft savable, but not publishable", async () => {
        Object.assign(state, {isDraft: true, haveChange: true, hasErrors: true})

        await mainButton().trigger("click")

        expect(saveAsDraft).toHaveBeenCalledOnce()
        expect((await publishMenuItem()).attributes("aria-disabled")).toBe("true")
    })
})
