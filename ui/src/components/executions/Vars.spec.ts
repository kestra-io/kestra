import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {defineComponent} from "vue"
import Vars from "./Vars.vue"

vi.mock("../../stores/executions", () => ({
    useExecutionsStore: () => ({execution: {id: "exec-1"}}),
}))
vi.mock("./VarValue.vue", () => ({
    default: defineComponent({
        name: "VarValue",
        props: {value: {type: [String, Object, Number, Boolean], default: ""}},
        template: "<span data-test=\"var-value\">{{ value }}</span>",
    }),
}))
vi.mock("../flows/SubFlowLink.vue", () => ({
    default: defineComponent({name: "SubFlowLink", template: "<span />"}),
}))
vi.mock("vue-virtual-scroller/dist/vue-virtual-scroller.css", () => ({}))
vi.mock("vue-virtual-scroller", () => ({
    // Renders only what it is told to prerender, the way the real scroller windows its rows.
    DynamicScroller: defineComponent({
        name: "DynamicScroller",
        props: {
            items: {type: Array, default: () => []},
            prerender: {type: Number, default: 0},
            keyField: {type: String, default: "id"},
        },
        template: "<div data-test=\"scroller\" :data-item-count=\"items.length\" :data-key-field=\"keyField\">"
            + "<template v-for=\"(item, index) in items.slice(0, prerender)\" :key=\"item[keyField]\">"
            + "<slot :item=\"item\" :index=\"index\" :active=\"true\" /></template></div>",
    }),
    DynamicScrollerItem: defineComponent({
        name: "DynamicScrollerItem",
        template: "<div data-test=\"row\"><slot /></div>",
    }),
}))

const i18n = createI18n({
    legacy: false,
    globalInjection: true,
    locale: "en",
    messages: {en: {name: "Name", value: "Value"}},
})

function mountVars(count: number) {
    const data = Object.fromEntries(
        Array.from({length: count}, (_, index) => [`item_${index}`, `value ${index}`]),
    )

    return mount(Vars, {
        props: {data},
        global: {
            plugins: [i18n],
            stubs: {KsNoData: true, KsText: true, KsDateAgo: true},
        },
    })
}

describe("Vars", () => {
    it("should give every output value to the scroller while building only the visible rows", () => {
        const wrapper = mountVars(15000)
        const scroller = wrapper.find("[data-test=scroller]")

        expect(scroller.attributes("data-item-count")).toBe("15000")
        expect(scroller.attributes("data-key-field")).toBe("key")
        expect(wrapper.findAll("[data-test=row]")).toHaveLength(20)
    })
})
