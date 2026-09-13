import {describe, expect, it} from "vitest"
import {defineComponent} from "vue"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"

import NavBarActions from "../../../../src/components/layout/NavBarActions.vue"

const i18n = createI18n({
    legacy: false,
    locale: "en",
    missingWarn: false,
    fallbackWarn: false,
    messages: {en: {actions: "Actions"}},
})

function mountActions(slots: Record<string, string>) {
    return mount(NavBarActions, {
        global: {plugins: [i18n, KestraDesignSystem]},
        slots,
    })
}

function overflowTrigger(wrapper: ReturnType<typeof mountActions>) {
    return wrapper.findAll("button").find(button => button.attributes("aria-label") === "Actions")
}

function visibleButton(wrapper: ReturnType<typeof mountActions>, text: string) {
    return wrapper.findAll("button").find(button => button.text().trim() === text)
}

describe("NavBarActions — legacy callers without a secondary slot", () => {
    it("renders a lone action inline", () => {
        const wrapper = mountActions({default: "<button>Import</button>"})

        expect(visibleButton(wrapper, "Import")).toBeDefined()
        expect(overflowTrigger(wrapper)).toBeUndefined()
    })

    it("collapses two or more actions into the overflow", () => {
        const wrapper = mountActions({default: "<button>Import</button><button>Export</button>"})

        expect(overflowTrigger(wrapper)).toBeDefined()
        expect(visibleButton(wrapper, "Import")).toBeUndefined()
    })
})

describe("NavBarActions — callers that opt into the secondary slot", () => {
    it("keeps the secondary visible and moves a lone action into the overflow", () => {
        const wrapper = mountActions({
            default: "<button>Delete logs</button>",
            secondary: "<button>Edit Flow</button>",
            primary: "<button>Execute</button>",
        })

        expect(visibleButton(wrapper, "Edit Flow")).toBeDefined()
        expect(visibleButton(wrapper, "Execute")).toBeDefined()
        expect(visibleButton(wrapper, "Delete logs")).toBeUndefined()
        expect(overflowTrigger(wrapper)).toBeDefined()
    })

    it("omits the overflow when there is nothing left to hold", () => {
        const wrapper = mountActions({
            secondary: "<button>Edit Flow</button>",
            primary: "<button>Execute</button>",
        })

        expect(visibleButton(wrapper, "Edit Flow")).toBeDefined()
        expect(visibleButton(wrapper, "Execute")).toBeDefined()
        expect(overflowTrigger(wrapper)).toBeUndefined()
    })

    it("does not fall back to the legacy layout when the secondary renders nothing", () => {
        const wrapper = mountActions({
            default: "<button>Delete logs</button>",
            secondary: "<template v-if=\"false\"><button>Edit Flow</button></template>",
            primary: "<button>Execute</button>",
        })

        expect(visibleButton(wrapper, "Delete logs")).toBeUndefined()
        expect(overflowTrigger(wrapper)).toBeDefined()
    })

    it("switches layout when a caller gates the secondary declaration after mount", async () => {
        const parent = defineComponent({
            components: {NavBarActions},
            props: {showSecondary: {type: Boolean, default: false}},
            template: `
                <NavBarActions>
                    <button>Delete logs</button>
                    <template v-if="showSecondary" #secondary><button>Edit Flow</button></template>
                    <template #primary><button>Execute</button></template>
                </NavBarActions>
            `,
        })

        const wrapper = mount(parent, {
            props: {showSecondary: false},
            global: {plugins: [i18n, KestraDesignSystem]},
        })

        // Legacy layout: a lone action renders inline, no overflow.
        expect(visibleButton(wrapper, "Delete logs")).toBeDefined()
        expect(overflowTrigger(wrapper)).toBeUndefined()

        await wrapper.setProps({showSecondary: true})

        // Two-slot layout: the action moves into the overflow, the secondary is visible.
        expect(visibleButton(wrapper, "Edit Flow")).toBeDefined()
        expect(visibleButton(wrapper, "Delete logs")).toBeUndefined()
        expect(overflowTrigger(wrapper)).toBeDefined()
    })
})
