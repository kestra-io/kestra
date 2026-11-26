import {afterEach, describe, expect, it, vi} from "vitest"
import {reactive} from "vue"
import {type VueWrapper} from "@vue/test-utils"

const flowStore = reactive({isDraftOnly: false})

vi.mock("../../stores/flow", () => ({
    useFlowStore: () => flowStore,
}))

import NoExecutions from "./NoExecutions.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"

let wrapper: VueWrapper | undefined

afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
})

describe("NoExecutions", () => {
    it.each([
        {flow: "a flow with a published revision", isDraftOnly: false, key: "no-executions-view.sub_title"},
        {flow: "a flow with only drafts", isDraftOnly: true, key: "no-executions-view.draft_sub_title"},
    ])("tells how to run $flow", ({isDraftOnly, key}) => {
        flowStore.isDraftOnly = isDraftOnly

        wrapper = i18nMount(NoExecutions, {global: {stubs: {OverviewBottom: true}}})

        expect(wrapper.text()).toContain(key)
    })
})
