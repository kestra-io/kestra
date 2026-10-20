import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import RouterMd from "./RouterMd.vue"

const i18n = createI18n({
    legacy: false,
    globalInjection: true,
    locale: "en",
    messages: {en: {"for flow": "for flow"}},
})

const mountRouterMd = () =>
    mount(RouterMd, {
        props: {execution: "exec-1", flowId: "flow-1", namespace: "team.sub"},
        global: {plugins: [i18n]},
    })

describe("RouterMd", () => {
    it("links the execution to the execution page with every param", () => {
        const links = mountRouterMd().findAllComponents({name: "RouterLink"})

        expect(links).toHaveLength(2)
        expect(links[0].props("to")).toEqual({
            name: "executions/update",
            params: {id: "exec-1", namespace: "team.sub", flowId: "flow-1"},
        })
        expect(links[0].text()).toBe("exec-1")
    })

    it("links the flow to the flow page with every param", () => {
        const links = mountRouterMd().findAllComponents({name: "RouterLink"})

        expect(links[1].props("to")).toEqual({
            name: "flows/update",
            params: {namespace: "team.sub", id: "flow-1"},
        })
        expect(links[1].text()).toBe("team.sub.flow-1")
    })

    it("separates the two links with the translated text", () => {
        const wrapper = mountRouterMd()

        expect(wrapper.text()).toContain("for flow")
    })
})
