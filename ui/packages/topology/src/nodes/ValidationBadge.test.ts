import {describe, expect, it} from "vitest"
import ValidationBadge from "./ValidationBadge.vue"
import {i18nMount} from "../../../../tests/unit/i18nMount"

const BADGE = "[data-test='topology-task-validation-badge']"

const MESSAGES = {flow_editor_stats: {errors: {label: "{count} Error(s)"}}}

function mountBadge(issues: string[]) {
    return i18nMount(ValidationBadge, {
        props: {issues},
        messages: MESSAGES,
        global: {
            stubs: {
                KsTooltip: {
                    template: "<span><slot /><div class='tooltip-content'><slot name='content' /></div></span>",
                },
            },
        },
    })
}

describe("ValidationBadge", () => {
    it("should render nothing when there are no issues", () => {
        const wrapper = mountBadge([])

        expect(wrapper.find(BADGE).exists()).toBe(false)
        expect(wrapper.find(".tooltip-content").exists()).toBe(false)
        expect(wrapper.text()).toBe("")
    })

    it("should render the badge when there is at least one issue", () => {
        const wrapper = mountBadge(["Missing required property"])

        expect(wrapper.find(BADGE).exists()).toBe(true)
    })

    it("should not show the count for a single issue", () => {
        const wrapper = mountBadge(["Missing required property"])

        expect(wrapper.find(".task-validation-badge-count").exists()).toBe(false)
    })

    it.each([2, 3, 5])("should show the count when there are %i issues", (total) => {
        const issues = Array.from({length: total}, (_, i) => `Issue number ${i + 1}`)

        const wrapper = mountBadge(issues)

        const count = wrapper.find(".task-validation-badge-count")
        expect(count.exists()).toBe(true)
        expect(count.text()).toBe(String(total))
    })

    it("should list every issue as its own entry in the tooltip", () => {
        const issues = ["First problem", "Second problem", "Third problem"]

        const wrapper = mountBadge(issues)

        const entries = wrapper.findAll(".task-validation-tooltip-list > li")
        expect(entries).toHaveLength(issues.length)
        expect(entries.map((entry) => entry.text())).toEqual(issues)
    })

    it("should pluralise the aria-label with the issue count", () => {
        const single = mountBadge(["Only problem"]).find(BADGE).attributes("aria-label")
        const several = mountBadge(["a", "b", "c"]).find(BADGE).attributes("aria-label")

        expect(single).toBe("1 Error(s)")
        expect(several).toBe("3 Error(s)")
    })

    it("should carry its data-test hook and be focusable", () => {
        const wrapper = mountBadge(["Missing required property"])

        const badge = wrapper.find(BADGE)
        expect(badge.exists()).toBe(true)
        expect(badge.attributes("tabindex")).toBe("0")
    })
})