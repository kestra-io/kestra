import {describe, expect, test, vi} from "vitest"
import {defineComponent, h, inject} from "vue"
import {i18nMount} from "../../../tests/unit/i18nMount"
import NavBarActionsDropdown from "./NavBarActionsDropdown.vue"
import {asItemKey, closeDropdownKey} from "./navBarActionsContext"

const handleClose = vi.fn()

const KsDropdownStub = defineComponent({
    name: "KsDropdown",
    setup(_, {expose, slots}) {
        expose({handleClose})

        return () => h("div", [...(slots.default?.() ?? []), ...(slots.dropdown?.() ?? [])])
    },
})

const KsDropdownMenuStub = defineComponent({
    name: "KsDropdownMenu",
    setup(_, {slots}) {
        return () => slots.default?.()
    },
})

let injectedAsItem: boolean | undefined
let closeDropdown: (() => void) | undefined

const AsItemProbe = defineComponent({
    setup() {
        injectedAsItem = inject(asItemKey)

        return () => h("span")
    },
})

const CloseBeforeTemplateRefProbe = defineComponent({
    setup() {
        closeDropdown = inject(closeDropdownKey)
        closeDropdown?.()

        return () => h("span")
    },
})

const mountDropdown = (options: Parameters<typeof i18nMount<typeof NavBarActionsDropdown>>[1] = {}) =>
    i18nMount(NavBarActionsDropdown, {
        global: {stubs: {KsDropdown: KsDropdownStub, KsDropdownMenu: KsDropdownMenuStub}},
        ...options,
    })

describe("NavBarActionsDropdown", () => {
    test("uses the translated actions label for its trigger", () => {
        const wrapper = mountDropdown({messages: {actions: "Localized actions"}})

        expect(wrapper.get("button").attributes("aria-label")).toBe("Localized actions")
    })

    test("provides asItemKey with a true value", () => {
        injectedAsItem = undefined
        mountDropdown({slots: {default: () => h(AsItemProbe)}})

        expect(injectedAsItem).toBe(true)
    })

    test("does not call handleClose when the close callback runs before the dropdown ref resolves", () => {
        handleClose.mockReset()
        closeDropdown = undefined

        expect(() => mountDropdown({slots: {default: () => h(CloseBeforeTemplateRefProbe)}})).not.toThrow()

        expect(closeDropdown).toBeTypeOf("function")
        expect(handleClose).not.toHaveBeenCalled()
    })
})
