import {describe, expect, it} from "vitest"
import {computed} from "vue"
import TriggerNode from "./TriggerNode.vue"
import {VALIDATION_ISSUES_INJECTION_KEY} from "../injectionKeys"
import {i18nMount} from "../../../../tests/unit/i18nMount"

function mountTriggerNode(validationIssues: Map<string, string[]>) {
    return i18nMount(TriggerNode, {
        props: {
            id: "Triggers.daily",
            data: {node: {triggerDeclaration: {id: "daily", type: "io.kestra.plugin.core.trigger.Schedule"}}, color: "default"},
        },
        global: {
            stubs: {
                Handle: true,
                NodeMenu: true,
                KsTooltip: {template: "<span><slot /></span>"},
                BasicNode: {template: "<div><slot name='title-status'/><slot name='title-actions'/></div>"},
            },
            provide: {[VALIDATION_ISSUES_INJECTION_KEY as symbol]: computed(() => validationIssues)},
        },
    })
}

describe("TriggerNode validation", () => {
    it("should flag a trigger that has validation errors with a badge and an outline", () => {
        const wrapper = mountTriggerNode(new Map([["daily", ["cron: must not be null"]]]))

        expect(wrapper.find("[data-test=\"topology-task-validation-badge\"]").exists()).toBe(true)
        expect(wrapper.find(".node-core--error").exists()).toBe(true)
    })

    it("should not flag a trigger whose id has no validation errors", () => {
        const wrapper = mountTriggerNode(new Map([["hourly", ["cron: must not be null"]]]))

        expect(wrapper.find("[data-test=\"topology-task-validation-badge\"]").exists()).toBe(false)
        expect(wrapper.find(".node-core--error").exists()).toBe(false)
    })
})
