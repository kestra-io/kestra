import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import EditorButtons from "../../../src/components/inputs/EditorButtons.vue"
import {saveDefaultActions, storageKeys} from "../../../src/utils/constants"

// Stub the playground store so tests run without a full Pinia/router setup.
vi.mock("../../../src/stores/playground", () => ({
    usePlaygroundStore: () => ({enabled: false}),
}))

// Named stubs so findAllComponents({name}) can distinguish them.
const KsDropdownStub = {
    name: "KsDropdownStub",
    template: "<div v-bind=\"$attrs\"><slot /><slot name=\"dropdown\" /></div>",
}
const KsButtonStub = {
    name: "KsButtonStub",
    template: "<div v-bind=\"$attrs\"><slot /></div>",
}

const BASE_PROPS = {
    isCreating: false,
    isReadOnly: false,
    canDelete: true,
    isAllowedEdit: true,
    haveChange: true,
    flowHaveTasks: true,
    errors: undefined as string[] | undefined,
    warnings: undefined as string[] | undefined,
    isNamespace: false,
    isDraft: false,
    showSaveAndExecute: false,
}

const GLOBAL = {
    mocks: {$t: (k: string) => k},
    stubs: {
        KsDropdown: KsDropdownStub,
        KsDropdownMenu: {template: "<div v-bind=\"$attrs\"><slot /></div>"},
        KsDropdownItem: {template: "<div v-bind=\"$attrs\"><slot /></div>"},
        KsButton: KsButtonStub,
        ContentSave: {template: "<span />"},
        FileDocumentEditOutline: {template: "<span />"},
        Publish: {template: "<span />"},
        DotsVertical: {template: "<span />"},
        Delete: {template: "<span />"},
        ContentCopy: {template: "<span />"},
        Download: {template: "<span />"},
    },
}

/** Returns the save split-button KsDropdown stub (identified by its `splitbutton` attr). */
function findSaveDropdown(wrapper: ReturnType<typeof mount>) {
    return wrapper
        .findAllComponents({name: "KsDropdownStub"})
        .find(s => Object.prototype.hasOwnProperty.call(s.attributes(), "splitbutton"))
}

/** Calls the `onCommand` listener that KsDropdown forwards via $attrs when an item is selected. */
async function invokeCommand(wrapper: ReturnType<typeof mount>, command: string) {
    const drop = findSaveDropdown(wrapper)
    expect(drop, "save split-button should be present").toBeDefined()
    const onCommand = (drop!.vm as any).$attrs?.onCommand
    expect(typeof onCommand, "KsDropdown onCommand listener should be a function").toBe("function")
    onCommand(command)
    await wrapper.vm.$nextTick()
}

describe("EditorButtons", () => {
    beforeEach(() => {
        localStorage.clear()
    })

    // ─── dropdown command executes the action immediately ──────────────────────

    it("emits 'save' when the SAVE command is selected from the dropdown", async () => {
        const wrapper = mount(EditorButtons, {props: BASE_PROPS, global: GLOBAL})
        await invokeCommand(wrapper, saveDefaultActions.SAVE)
        expect(wrapper.emitted("save")).toBeTruthy()
    })

    it("emits 'save-as-draft' when the SAVE_AS_DRAFT command is selected", async () => {
        const wrapper = mount(EditorButtons, {props: BASE_PROPS, global: GLOBAL})
        await invokeCommand(wrapper, saveDefaultActions.SAVE_AS_DRAFT)
        expect(wrapper.emitted("save-as-draft")).toBeTruthy()
    })

    // ─── main-button click respects the localStorage default ──────────────────

    it("emits 'save' on main-button click when SAVE is the stored default", async () => {
        localStorage.setItem(storageKeys.SAVE_DEFAULT_ACTION, saveDefaultActions.SAVE)
        const wrapper = mount(EditorButtons, {props: BASE_PROPS, global: GLOBAL})
        const drop = findSaveDropdown(wrapper)
        const onClick = (drop!.vm as any).$attrs?.onClick
        expect(typeof onClick).toBe("function")
        onClick(new MouseEvent("click"))
        await wrapper.vm.$nextTick()
        expect(wrapper.emitted("save")).toBeTruthy()
    })

    it("emits 'save-as-draft' on main-button click when SAVE_AS_DRAFT is the stored default", async () => {
        localStorage.setItem(storageKeys.SAVE_DEFAULT_ACTION, saveDefaultActions.SAVE_AS_DRAFT)
        const wrapper = mount(EditorButtons, {props: BASE_PROPS, global: GLOBAL})
        const drop = findSaveDropdown(wrapper)
        const onClick = (drop!.vm as any).$attrs?.onClick
        expect(typeof onClick).toBe("function")
        onClick(new MouseEvent("click"))
        await wrapper.vm.$nextTick()
        expect(wrapper.emitted("save-as-draft")).toBeTruthy()
    })

    // ─── saveButtonType: draft steps Save down to default style ───────────────

    it("passes type='primary' to the save split-button when NOT a draft", () => {
        const wrapper = mount(EditorButtons, {
            props: {...BASE_PROPS, isDraft: false},
            global: GLOBAL,
        })
        const drop = findSaveDropdown(wrapper)
        expect(drop!.attributes("type")).toBe("primary")
    })

    it("passes type='default' to the save split-button when flow IS a draft", () => {
        const wrapper = mount(EditorButtons, {
            props: {...BASE_PROPS, isDraft: true},
            global: GLOBAL,
        })
        // When a draft, Publish is primary — Save steps down to default.
        const drop = findSaveDropdown(wrapper)
        expect(drop!.attributes("type")).toBe("default")
    })

    // ─── Publish button visibility and behaviour ───────────────────────────────

    it("does not render the Publish button when isDraft is false", () => {
        const wrapper = mount(EditorButtons, {
            props: {...BASE_PROPS, isDraft: false},
            global: GLOBAL,
        })
        // With our $t mock that returns keys, "publish" text only appears when the Publish
        // KsButton is rendered. Ensure no KsButton contains that text.
        const buttons = wrapper.findAllComponents({name: "KsButtonStub"})
        expect(buttons.some(b => b.text().trim() === "publish")).toBe(false)
    })

    it("renders the Publish button when isDraft is true", () => {
        const wrapper = mount(EditorButtons, {
            props: {...BASE_PROPS, isDraft: true},
            global: GLOBAL,
        })
        const buttons = wrapper.findAllComponents({name: "KsButtonStub"})
        expect(buttons.some(b => b.text().trim() === "publish")).toBe(true)
    })

    it("emits 'publish' when the Publish button is clicked", async () => {
        const wrapper = mount(EditorButtons, {
            props: {...BASE_PROPS, isDraft: true, errors: undefined},
            global: GLOBAL,
        })
        const buttons = wrapper.findAllComponents({name: "KsButtonStub"})
        const publishBtn = buttons.find(b => b.text().trim() === "publish")
        expect(publishBtn, "Publish button stub should be found").toBeDefined()
        await publishBtn!.trigger("click")
        expect(wrapper.emitted("publish")).toBeTruthy()
    })

    it("disables the Publish button when errors are present", () => {
        const wrapper = mount(EditorButtons, {
            props: {...BASE_PROPS, isDraft: true, errors: ["type error"]},
            global: GLOBAL,
        })
        const buttons = wrapper.findAllComponents({name: "KsButtonStub"})
        const publishBtn = buttons.find(b => b.text().trim() === "publish")
        expect(publishBtn, "Publish button stub should be found").toBeDefined()
        // :disabled="hasErrors" propagates as a boolean attribute on the stub's root element.
        expect(publishBtn!.attributes("disabled")).toBeDefined()
    })
})
