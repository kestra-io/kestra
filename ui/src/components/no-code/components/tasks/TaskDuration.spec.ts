import {describe, expect, it} from "vitest"
import {i18nMount} from "../../../../../tests/unit/i18nMount"

import TaskDuration from "./TaskDuration.vue"

/** Types a value into the first row's number input, as the user would, and returns what the field wrote. */
async function typeIntoFirstRow(modelValue: string, value: number) {
    const wrapper = i18nMount(TaskDuration, {props: {modelValue}})
    wrapper.findComponent({name: "KsInputNumber"}).vm.$emit("update:model-value", value)
    await wrapper.vm.$nextTick()
    return wrapper.emitted("update:modelValue")?.at(-1)?.[0]
}

describe("TaskDuration", () => {
    // java.time.Duration.parse, which reads every duration in the backend, rejects the W designator.
    it("writes weeks as days", async () => {
        expect(await typeIntoFirstRow("P1W", 2)).toBe("P14D")
    })

    it("adds weeks to the days of the same duration", async () => {
        expect(await typeIntoFirstRow("P2W3DT4H", 1)).toBe("P10DT4H")
    })
})
