import {describe, expect, it} from "vitest"
import TaskRunLogs from "./TaskRunLogs.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"

function mountLogs() {
    return i18nMount(TaskRunLogs, {
        props: {executionId: "exec", taskRunIds: ["run"]},
        global: {
            stubs: {
                Collapse: {template: "<div><slot /></div>"},
                SearchField: {template: "<div></div>"},
                LogLevelSelector: {
                    props: ["value"],
                    emits: ["update:modelValue"],
                    template: "<button data-test='log-level-selector' @click=\"$emit('update:modelValue', 'DEBUG')\">{{ value }}</button>",
                },
                TaskRunDetails: {
                    props: ["level", "levelFilter"],
                    template: "<div data-test='task-run-details' :data-level='level' :data-level-filter='JSON.stringify(levelFilter)' />",
                },
            },
        },
    })
}

describe("TaskRunLogs", () => {
    it("shouldPassTheSelectedLevelAsBothTheClientFilterAndTheRequestFilter", async () => {
        const wrapper = mountLogs()
        const details = wrapper.get("[data-test='task-run-details']")

        expect(details.attributes("data-level")).toEqual("INFO")
        expect(JSON.parse(details.attributes("data-level-filter") as string)).toEqual({value: "INFO", direction: "min"})

        await wrapper.get("[data-test='log-level-selector']").trigger("click")

        expect(details.attributes("data-level")).toEqual("DEBUG")
        expect(JSON.parse(details.attributes("data-level-filter") as string)).toEqual({value: "DEBUG", direction: "min"})
    })
})
