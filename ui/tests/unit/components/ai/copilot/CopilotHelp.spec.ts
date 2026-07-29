import {describe, it, expect} from "vitest"
import {mount} from "@vue/test-utils"
import CopilotHelp from "../../../../../src/components/ai/copilot/CopilotHelp.vue"
import {mountGlobal} from "./_helpers"

// Stub RouterLink so the Blueprints target can be asserted without a real router.
const RouterLinkStub = {
    name: "RouterLink",
    props: ["to"],
    template: "<a class=\"router-link-stub\" :data-to=\"JSON.stringify(to)\"><slot /></a>",
}

const mountHelp = () =>
    mount(CopilotHelp, {
        global: {...mountGlobal, stubs: {...mountGlobal.stubs, RouterLink: RouterLinkStub}},
    })

describe("CopilotHelp", () => {
    it("renders the 'Need Help?' heading and both help cards with resolved copy", () => {
        const w = mountHelp()
        expect(w.find("[data-test=\"copilot-help\"]").exists()).toBe(true)
        expect(w.text()).toContain("Need Help?")
        // Both cards render their i18n-resolved title + description.
        expect(w.text()).toContain("Blueprints")
        expect(w.text()).toContain("Pre-built workflow templates for common use cases")
        expect(w.text()).toContain("Slack Community")
        expect(w.text()).toContain("Connect with other users and get help from the community")
        // Keys actually resolved — no raw i18n path leaked into the DOM.
        expect(w.text()).not.toContain("welcome_copilot")
    })

    it("links the Blueprints card to the community flow blueprints route", () => {
        const w = mountHelp()
        const link = w.find(".router-link-stub")
        expect(link.exists()).toBe(true)
        const to = JSON.parse(link.attributes("data-to") ?? "{}")
        expect(to).toEqual({name: "blueprints", params: {kind: "flow", tab: "community"}})
    })

    it("links the Slack card to the external community URL, opened safely in a new tab", () => {
        const w = mountHelp()
        const slack = w.find("a[href=\"https://kestra.io/slack\"]")
        expect(slack.exists()).toBe(true)
        expect(slack.attributes("target")).toBe("_blank")
        expect(slack.attributes("rel")).toContain("noopener")
    })
})
