import {describe, expect, it, vi} from "vitest"
import {defineComponent, h, markRaw} from "vue"
import NodeMenu, {type NodeAction} from "./NodeMenu.vue"
import NodeMenuItem from "./NodeMenuItem.vue"
import {i18nMount} from "../../../../tests/unit/i18nMount"

// Icons are components; mark them raw so props hand the same object through instead of a reactive proxy.
const PencilIcon = markRaw(defineComponent({name: "PencilIcon", setup: () => () => h("svg", {class: "pencil-icon"})}))
const TrashIcon = markRaw(defineComponent({name: "TrashIcon", setup: () => () => h("svg", {class: "trash-icon"})}))

// Element Plus's dropdown menu and items need the context of an open dropdown, so render them as plain
// elements that keep the props NodeMenuItem hands them.
const KsDropdownItemStub = defineComponent({
    name: "KsDropdownItem",
    props: {divided: {type: Boolean, default: false}, icon: {type: [Object, Function, String], default: undefined}},
    setup: (props, {slots}) => () => h("li", {class: "dropdown-item"}, [props.icon ? h(props.icon) : null, slots.default?.()]),
})

function action(overrides: Partial<NodeAction> = {}): NodeAction {
    return {key: "edit", label: "Edit", icon: PencilIcon, onClick: vi.fn(), ...overrides}
}

function twoActions() {
    return [
        action(),
        action({key: "delete", label: "Delete", icon: TrashIcon, danger: true, divided: true}),
    ]
}

function mountNodeMenu(actions: NodeAction[]) {
    return i18nMount(NodeMenu, {
        props: {actions},
        global: {
            stubs: {
                KsDropdown: {template: "<div><slot /><slot name='dropdown' /></div>"},
                KsDropdownMenu: {template: "<ul><slot /></ul>"},
                KsDropdownItem: KsDropdownItemStub,
                // KsTooltip wraps the single action button in an element-plus popper; render only its default slot.
                KsTooltip: {template: "<span><slot /></span>"},
            },
        },
    })
}

describe("NodeMenu with several actions", () => {
    it("should render one menu item per action", () => {
        const actions = twoActions()

        const wrapper = mountNodeMenu(actions)

        expect(wrapper.findAllComponents(NodeMenuItem)).toHaveLength(actions.length)
    })

    it("should show each action's label and icon on its own item", () => {
        const wrapper = mountNodeMenu(twoActions())

        const [edit, remove] = wrapper.findAllComponents(KsDropdownItemStub)
        expect(edit.text()).toBe("Edit")
        expect(edit.props("icon")).toBe(PencilIcon)
        expect(edit.find(".pencil-icon").exists()).toBe(true)
        expect(remove.text()).toBe("Delete")
        expect(remove.props("icon")).toBe(TrashIcon)
        expect(remove.find(".trash-icon").exists()).toBe(true)
    })

    it("should call only the clicked item's onClick, exactly once", async () => {
        const [edit, remove] = twoActions()
        const wrapper = mountNodeMenu([edit, remove])

        await wrapper.findAllComponents(NodeMenuItem)[1].trigger("click")

        expect(remove.onClick).toHaveBeenCalledTimes(1)
        expect(edit.onClick).not.toHaveBeenCalled()
    })

    it("should give the node-action--danger class only to a danger action", () => {
        const wrapper = mountNodeMenu(twoActions())

        const [edit, remove] = wrapper.findAllComponents(NodeMenuItem)
        expect(edit.classes()).not.toContain("node-action--danger")
        expect(remove.classes()).toContain("node-action--danger")
    })

    it("should render only a divided action as divided", () => {
        const wrapper = mountNodeMenu(twoActions())

        const [edit, remove] = wrapper.findAllComponents(KsDropdownItemStub)
        expect(edit.props("divided")).toBe(false)
        expect(remove.props("divided")).toBe(true)
    })
})

describe("NodeMenu with a single action", () => {
    it("should render the action as one icon button instead of a menu", () => {
        const wrapper = mountNodeMenu([action()])

        expect(wrapper.findAllComponents(NodeMenuItem)).toHaveLength(0)
        expect(wrapper.find("button.node-action-button").attributes("aria-label")).toBe("Edit")
        expect(wrapper.find("button .pencil-icon").exists()).toBe(true)
    })

    it("should call the action's onClick exactly once when the button is clicked", async () => {
        const edit = action()
        const wrapper = mountNodeMenu([edit])

        await wrapper.find("button.node-action-button").trigger("click")

        expect(edit.onClick).toHaveBeenCalledTimes(1)
    })
})

describe("NodeMenu without actions", () => {
    it("should render an empty menu without throwing", () => {
        const wrapper = mountNodeMenu([])

        expect(wrapper.findAllComponents(NodeMenuItem)).toHaveLength(0)
        expect(wrapper.find("button").exists()).toBe(false)
    })
})
