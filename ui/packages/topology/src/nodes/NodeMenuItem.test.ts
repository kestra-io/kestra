import {describe, expect, it, vi} from "vitest"
import {defineComponent, h, markRaw} from "vue"
import NodeMenuItem from "./NodeMenuItem.vue"
import type {NodeAction} from "./NodeMenu.vue"
import {i18nMount} from "../../../../tests/unit/i18nMount"

const PencilIcon = markRaw(defineComponent({name: "PencilIcon", setup: () => () => h("svg", {class: "pencil-icon"})}))

const KsDropdownItemStub = defineComponent({
    name: "KsDropdownItem",
    setup: (_, {slots}) => () => h("li", {class: "dropdown-item"}, slots.default?.()),
})

function action(overrides: Partial<NodeAction> = {}): NodeAction {
    return {key: "edit", label: "Edit", icon: PencilIcon, onClick: vi.fn(), ...overrides}
}

function mountItem(item: NodeAction) {
    return i18nMount(NodeMenuItem, {
        props: {action: item},
        global: {stubs: {KsDropdownItem: KsDropdownItemStub}},
    })
}

describe("NodeMenuItem", () => {
    it("should render the action's label", () => {
        const wrapper = mountItem(action({label: "Rename"}))

        expect(wrapper.text()).toBe("Rename")
    })

    it("should add the node-action--danger class to a danger action", () => {
        const wrapper = mountItem(action({danger: true}))

        expect(wrapper.classes()).toContain("node-action--danger")
    })

    it("should not add the node-action--danger class to a non-danger action", () => {
        const notDanger = mountItem(action({danger: false}))
        const noDanger = mountItem(action())

        expect(notDanger.classes()).not.toContain("node-action--danger")
        expect(noDanger.classes()).not.toContain("node-action--danger")
    })

    it("should call the action's own onClick when clicked", async () => {
        const item = action()
        const wrapper = mountItem(item)

        await wrapper.trigger("click")

        expect(item.onClick).toHaveBeenCalled()
    })

    it("should not call onClick on mount", () => {
        const item = action()

        mountItem(item)

        expect(item.onClick).not.toHaveBeenCalled()
    })

    it("should call onClick once per click", async () => {
        const item = action()
        const wrapper = mountItem(item)

        await wrapper.trigger("click")
        expect(item.onClick).toHaveBeenCalledTimes(1)

        await wrapper.trigger("click")
        expect(item.onClick).toHaveBeenCalledTimes(2)
    })
})
