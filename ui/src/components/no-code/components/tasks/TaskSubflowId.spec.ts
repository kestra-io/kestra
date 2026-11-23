
import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../../../tests/unit/i18nMount"

const {flowState, flowsByNamespace} = vi.hoisted(() => ({
    flowState: {
        flow: undefined as {id: string; namespace: string} | undefined,
    },
    flowsByNamespace: vi.fn(),
}))

vi.mock("../../../../stores/flow", () => ({
    useFlowStore: () => ({
        get flow() {
            return flowState.flow
        },
        flowsByNamespace,
    }),
}))

import TaskSubflowId from "./TaskSubflowId.vue"

const KsSelectStub = {
    name: "KsSelect",
    props: ["modelValue", "disabled", "placeholder"],
    emits: ["update:modelValue"],
    template: "<div><slot /></div>",
}

const KsOptionStub = {
    name: "KsOption",
    props: ["label", "value"],
    template: "<div>{{ label }}</div>",
}

function mountField(props: {
    modelValue?: string
    schema?: Record<string, unknown>
    task?: Record<string, unknown>
} = {}) {
    return i18nMount(TaskSubflowId, {
        props,
        global: {
            stubs: {
                KsSelect: KsSelectStub,
                KsOption: KsOptionStub,
            },
        },
    })
}

describe("TaskSubflowId", () => {
    beforeEach(() => {
        flowState.flow = {
            id: "current_flow",
            namespace: "team.current",
        }

        flowsByNamespace.mockReset()
        flowsByNamespace.mockResolvedValue([
            {id: "current_flow"},
            {id: "child_flow"},
        ])
    })

    it("uses the namespace from the task when set", async () => {
        mountField({task: {namespace: "team.other"}})

        await flushPromises()

        expect(flowsByNamespace).toHaveBeenCalledWith("team.other")
        expect(flowsByNamespace).toHaveBeenCalledTimes(1)
    })

    it("falls back to the current flow namespace", async () => {
        mountField({task: {}})

        await flushPromises()

        expect(flowsByNamespace).toHaveBeenCalledWith("team.current")
    })

    it("does not fetch flows when no namespace is available", async () => {
        flowState.flow = undefined

        mountField({task: {}})

        await flushPromises()

        expect(flowsByNamespace).not.toHaveBeenCalled()
    })

    it("offers the fetched flow ids as options", async () => {
        const wrapper = mountField({task: {namespace: "team.other"}})

        await flushPromises()

        const options = wrapper.findAllComponents({name: "KsOption"})
        expect(options.map(option => option.props("value"))).toEqual([
            "current_flow",
            "child_flow",
        ])
    })

    it("removes the current flow from options in its own namespace", async () => {
        const wrapper = mountField({task: {namespace: "team.current"}})

        await flushPromises()

        const options = wrapper.findAllComponents({name: "KsOption"})
        expect(options.map(option => option.props("value"))).toEqual([
            "child_flow",
        ])
    })

    it("keeps the current flow in options when browsing another namespace", async () => {
        const wrapper = mountField({task: {namespace: "team.other"}})

        await flushPromises()

        const options = wrapper.findAllComponents({name: "KsOption"})
        expect(options.map(option => option.props("value"))).toEqual([
            "current_flow",
            "child_flow",
        ])
    })

    it("uses the schema default when modelValue is not provided", () => {
        const wrapper = mountField({
            schema: {default: "default_flow"},
            task: {namespace: "team.other"},
        })

        expect(
            wrapper.findComponent({name: "KsSelect"}).props("modelValue"),
        ).toBe("default_flow")
    })

    it("emits the selected flow id", async () => {
        const wrapper = mountField({task: {namespace: "team.other"}})

        await wrapper.findComponent({name: "KsSelect"})
            .vm.$emit("update:modelValue", "child_flow")

        expect(wrapper.emitted("update:modelValue")).toEqual([
            ["child_flow"],
        ])
    })

    it("collapses an empty selection to undefined", async () => {
        const wrapper = mountField({task: {namespace: "team.other"}})

        await wrapper.findComponent({name: "KsSelect"})
            .vm.$emit("update:modelValue", "")

        expect(wrapper.emitted("update:modelValue")).toEqual([
            [undefined],
        ])
    })
})
