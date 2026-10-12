import {describe, expect, it} from "vitest"
import ValidationBadge from "./ValidationBadge.vue"
import {i18nMount} from "../../../../tests/unit/i18nMount"

function mountValidationBadge(issues: string[]) {
    return i18nMount(ValidationBadge, {
        props: {issues},
        messages: {
            "error detected": "Error(s) detected.",
            flow_editor_stats: {
                errors: {
                    label: "{count} error | {count} errors",
                },
            },
        },
        global: {
            stubs: {
                KsTooltip: {template: "<span class='tooltip'><slot name='content' /><slot /></span>"},
            },
        },
    })
}

describe("ValidationBadge", () => {
    it("should render nothing when there are no validation issues", () => {
        const wrapper = mountValidationBadge([])

        expect(wrapper.find("[data-test='topology-task-validation-badge']").exists()).toBe(false)
    })

    it("should render one focusable badge without a count for a single issue", () => {
        const wrapper = mountValidationBadge(["Task ID is required"])
        const badge = wrapper.find("[data-test='topology-task-validation-badge']")

        expect(badge.exists()).toBe(true)
        expect(badge.attributes("role")).toBe("img")
        expect(badge.attributes("tabindex")).toBe("0")
        expect(badge.attributes("aria-label")).toBe("1 error")
        expect(wrapper.find(".task-validation-badge-count").exists()).toBe(false)
    })

    it("should show the number of issues and a plural accessible label", () => {
        const wrapper = mountValidationBadge(["Task ID is required", "Invalid task type"])
        const badge = wrapper.find("[data-test='topology-task-validation-badge']")

        expect(badge.attributes("aria-label")).toBe("2 errors")
        expect(wrapper.find(".task-validation-badge-count").text()).toBe("2")
    })

    it("should list every validation issue in its tooltip", () => {
        const issues = ["Task ID is required", "Invalid task type"]
        const wrapper = mountValidationBadge(issues)

        expect(wrapper.find(".task-validation-tooltip-head").text()).toContain("Error(s) detected.")
        expect(wrapper.findAll(".task-validation-tooltip-list li").map((issue) => issue.text())).toEqual(issues)
    })
})
