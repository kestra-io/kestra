import {describe, expect, test} from "vitest"
import {mount, RouterLinkStub} from "@vue/test-utils"
import RouterMd from "./RouterMd.vue"

describe("RouterMd", () => {
    const props = {
        execution: "execution-123",
        namespace: "company.team",
        flowId: "my-flow",
    }

    const mountComponent = () => mount(RouterMd, {
        props,
        global: {
            mocks: {
                $t: (key: string) => key,
            },
            stubs: {
                RouterLink: RouterLinkStub,
            },
        },
    })

    test("renders the execution link with the correct route and text", () => {
        const wrapper = mountComponent()
        const links = wrapper.findAllComponents(RouterLinkStub)

        expect(links).toHaveLength(2)
        expect(links[0].text()).toBe("execution-123")
        expect(links[0].props("to")).toEqual({
            name: "executions/update",
            params: {
                id: "execution-123",
                namespace: "company.team",
                flowId: "my-flow",
            },
        })
    })

    test("renders the translated for-flow text between the links", () => {
        const wrapper = mountComponent()
        const links = wrapper.findAllComponents(RouterLinkStub)
        const text = wrapper.text()

        expect(text).toContain("for flow")
        expect(text.indexOf("execution-123")).toBeLessThan(
            text.indexOf("for flow"),
        )
        expect(text.indexOf("for flow")).toBeLessThan(
            text.indexOf("company.team.my-flow"),
        )
        expect(links).toHaveLength(2)
    })

    test("renders the flow link with the correct route and text", () => {
        const wrapper = mountComponent()
        const links = wrapper.findAllComponents(RouterLinkStub)

        expect(links).toHaveLength(2)
        expect(links[1].text()).toBe("company.team.my-flow")
        expect(links[1].props("to")).toEqual({
            name: "flows/update",
            params: {
                namespace: "company.team",
                id: "my-flow",
            },
        })
    })
})