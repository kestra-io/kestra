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

const KsDataTableStub = defineComponent({
    name: "KsDataTable",
    props: {
        data: {type: Array, default: () => []},
        total: {type: Number, default: 0},
        pageSize: {type: Number, default: 0},
    },
    template: "<div data-test=\"table\" :data-total=\"total\" :data-page-size=\"pageSize\">"
        + "<span v-for=\"row in data\" :key=\"row.key\" data-test=\"row\">{{ row.key }}</span></div>",
})

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
            stubs: {KsDataTable: KsDataTableStub, KsTableColumn: true, KsDateAgo: true},
        },
    })
}

describe("Vars", () => {
    it("should hand the table one page of rows instead of every output value", () => {
        const wrapper = mountVars(15000)
        const table = wrapper.find("[data-test=table]")

        expect(wrapper.findAll("[data-test=row]")).toHaveLength(100)
        expect(table.attributes("data-total")).toBe("15000")
        expect(table.attributes("data-page-size")).toBe("100")
    })

    it("should hand the table every row when they fit on one page", () => {
        const wrapper = mountVars(12)

        expect(wrapper.findAll("[data-test=row]")).toHaveLength(12)
    })
})
