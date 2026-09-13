import {describe, it, expect} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import Add from "../../../src/components/no-code/components/Add.vue"

const i18n = createI18n({
    legacy: false,
    locale: "en",
    messages: {en: {no_code: {
        adding: "+ Add a {what}",
        adding_default: "+ Add a new value",
        adding_to: "+ Add to {what}",
    }}},
})

function render(props: {what?: string, to?: string} = {}) {
    return mount(Add, {props, global: {plugins: [i18n]}})
}

describe("Add", () => {
    it("names its target field when one is known", () => {
        expect(render({to: "sla"}).text()).toBe("+ Add to sla")
    })

    it("keeps the noun-based label when a what is given", () => {
        expect(render({what: "label"}).text()).toBe("+ Add a label")
    })

    it("falls back to the generic label without context", () => {
        expect(render().text()).toBe("+ Add a new value")
    })

    it("what wins over to when both are given", () => {
        expect(render({what: "label", to: "sla"}).text()).toBe("+ Add a label")
    })

    it("emits add with its what on click", async () => {
        const wrapper = render({what: "label"})
        await wrapper.find("button").trigger("click")
        expect(wrapper.emitted("add")).toEqual([["label"]])
    })
})
