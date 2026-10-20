import {describe, expect, test, vi} from "vitest"
import {defineComponent, h, inject} from "vue"
import {i18nMount} from "../../../packages/design-system/tests/units/i18nMount"
import NavBarActionsDropdown from "./NavBarActionsDropdown.vue"
import {asItemKey, closeDropdownKey} from "./navBarActionsContext"

const handleClose = vi.fn()

const KsDropdownStub = defineComponent({
    name: "KsDropdown",
    props: {
        trigger: {type: String, required: true},
        persistent: Boolean,
    },
    setup(_, {expose, slots}) {
        expose({handleClose})

        return () => h("div", [...(slots.default?.() ?? []), ...(slots.dropdown?.() ?? [])])
    },
})

const KsDropdownMenuStub = defineComponent({
    name: "KsDropdownMenu",
    setup(_, {slots}) {
        return () => h("div", {"data-testid": "dropdown-menu"}, slots.default?.())
    },
})

let injectedAsItem: boolean | undefined
const missingCloseDropdown = (): void => {
    throw new Error("closeDropdownKey was not provided")
}
let injectedCloseDropdown = missingCloseDropdown

const InjectionProbe = defineComponent({
    setup() {
        injectedAsItem = inject(asItemKey)
        injectedCloseDropdown = inject(closeDropdownKey) ?? missingCloseDropdown

        return () => h("span")
    },
})

const CloseBeforeTemplateRefProbe = defineComponent({
    setup() {
        const closeDropdown = inject(closeDropdownKey)
        expect(closeDropdown).toBeTypeOf("function")
        expect(closeDropdown).not.toThrow()

        return () => h("span")
    },
})

const mountDropdown = (slot: () => ReturnType<typeof h> = () => h("span")) =>
    i18nMount(NavBarActionsDropdown, {
        global: {
            stubs: {
                KsDropdown: KsDropdownStub,
                KsDropdownMenu: KsDropdownMenuStub,
            },
        },
        slots: {default: slot},
    })

describe("NavBarActionsDropdown", () => {
    test("renders default slot content inside the dropdown menu", () => {
        const wrapper = mountDropdown(() => h("span", {"data-testid": "menu-item"}, "Menu item"))

        expect(wrapper.get("[data-testid='dropdown-menu'] [data-testid='menu-item']").text()).toBe("Menu item")
    })

    test("uses the translated actions label for its trigger", () => {
        const wrapper = i18nMount(NavBarActionsDropdown, {
            global: {
                stubs: {
                    KsDropdown: KsDropdownStub,
                    KsDropdownMenu: KsDropdownMenuStub,
                },
            },
            messages: {actions: "Localized actions"},
        })

        expect(wrapper.get("button").attributes("aria-label")).toBe("Localized actions")
    })

    test("configures the dropdown to open on click", () => {
        const wrapper = mountDropdown()

        expect(wrapper.getComponent(KsDropdownStub).props("trigger")).toBe("click")
    })

    test("configures the dropdown as persistent", () => {
        const wrapper = mountDropdown()

        expect(wrapper.getComponent(KsDropdownStub).props("persistent")).toBe(true)
    })

    test("provides asItemKey with a true value", () => {
        injectedAsItem = undefined
        mountDropdown(() => h(InjectionProbe))

        expect(injectedAsItem).toBe(true)
    })

    test("provides a close callback that closes a resolved dropdown", () => {
        handleClose.mockReset()
        injectedCloseDropdown = missingCloseDropdown
        mountDropdown(() => h(InjectionProbe))

        expect(injectedCloseDropdown).not.toBe(missingCloseDropdown)
        injectedCloseDropdown()

        expect(handleClose).toHaveBeenCalledOnce()
    })

    test("does not throw when the close callback runs before the dropdown ref resolves", () => {
        expect(() => mountDropdown(() => h(CloseBeforeTemplateRefProbe))).not.toThrow()
    })
})
