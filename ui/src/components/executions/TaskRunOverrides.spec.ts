import {describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {i18nMount} from "../../../tests/unit/i18nMount"
import en from "../../translations/en.json"
import {useExecutionsStore} from "../../stores/executions"
import TaskRunOverrides from "./TaskRunOverrides.vue"

const PopoverOpenedOnMount = {
    emits: ["show"],
    mounted(this: {$emit: (event: "show") => void}) {
        this.$emit("show")
    },
    template: "<div><slot name='reference' /><slot /></div>",
}

async function mountOverrides(loadTaskRunOverrides: () => Promise<unknown>) {
    setActivePinia(createPinia())
    const executionsStore = useExecutionsStore()
    executionsStore.loadTaskRunOverrides = vi.fn(loadTaskRunOverrides) as typeof executionsStore.loadTaskRunOverrides

    const wrapper = i18nMount(TaskRunOverrides, {
        locales: en,
        props: {
            executionId: "exec1",
            taskRunId: "tr1",
            overrides: {id: "o1", keys: ["outputs.fetch.values.code"], editedAt: "2026-10-27T10:00:00Z"},
        },
        global: {stubs: {KsPopover: PopoverOpenedOnMount}},
    })
    await flushPromises()
    return wrapper
}

describe("TaskRunOverrides", () => {
    it("shows each original value next to the value the replay used", async () => {
        const wrapper = await mountOverrides(() => Promise.resolve([{key: "outputs.fetch.values.code", value: "KR-0412", originalValue: "KR-0412 "}]))

        expect(wrapper.find("[data-test='task-run-overrides']").text()).toBe("1 overridden expression")
        expect(wrapper.find("tbody").text()).toContain("\"KR-0412 \"")
        expect(wrapper.find("tbody").text()).toContain("\"KR-0412\"")
    })

    it("keeps the keys but explains the missing values when outputs cannot be read", async () => {
        const forbidden = Object.assign(new Error("Forbidden"), {problem: {type: "about:blank", status: 403, title: "Forbidden"}})
        const wrapper = await mountOverrides(() => Promise.reject(forbidden))

        expect(wrapper.find("tbody").text()).toBe("{{ outputs.fetch.values.code }}")
        expect(wrapper.find("[data-test='task-run-overrides-hidden']").exists()).toBe(true)
    })
})
