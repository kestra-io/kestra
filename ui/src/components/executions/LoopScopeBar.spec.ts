import {describe, expect, it} from "vitest"
import LoopScopeBar from "./LoopScopeBar.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"
import en from "../../translations/en.json"

const mountBar = (props: Partial<InstanceType<typeof LoopScopeBar>["$props"]> = {}) =>
    i18nMount(LoopScopeBar, {locales: en, props: {entries: [], canJumpToFailure: false, failuresOnly: false, ...props}})

describe("LoopScopeBar", () => {
    it("shouldShowTheWholeExecutionWhenNothingIsScoped", () => {
        const wrapper = mountBar()

        expect(wrapper.find("[data-test='loop-scope-trail']").text()).toBe("Whole execution")
        expect(wrapper.find("[data-test='loop-scope-clear']").exists()).toBe(false)
    })

    it("shouldShowEachScopeWithItsNumberAndValue", () => {
        const wrapper = mountBar({entries: [{taskId: "per_region", number: 2, value: "AMER"}, {taskId: "per_quarter", number: 1}]})

        expect(wrapper.find("[data-test='loop-scope-trail']").text()).toBe("per_region: #2 AMERper_quarter: Iteration 1")
    })

    it("shouldEmitClearJumpAndToggleFromTheirButtons", async () => {
        const wrapper = mountBar({entries: [{taskId: "per_region", number: 2}], canJumpToFailure: true})

        await wrapper.find("[data-test='loop-scope-clear']").trigger("click")
        await wrapper.find("[data-test='loop-scope-jump']").trigger("click")
        await wrapper.find("[data-test='loop-scope-failures-only']").trigger("click")

        expect(wrapper.emitted("clear")).toHaveLength(1)
        expect(wrapper.emitted("jumpToFailure")).toHaveLength(1)
        expect(wrapper.emitted("toggleFailuresOnly")).toHaveLength(1)
    })

    it("shouldHideJumpWhenNoFailureIsKnownAndOfferRetryOnFailure", async () => {
        const wrapper = mountBar({failure: "unknown"})

        expect(wrapper.find("[data-test='loop-scope-jump']").exists()).toBe(false)
        await wrapper.find("[data-test='loop-scope-retry']").trigger("click")
        expect(wrapper.emitted("retry")).toHaveLength(1)
    })

    it("shouldExplainAForbiddenScopeWithoutOfferingARetry", () => {
        const wrapper = mountBar({failure: "forbidden"})

        expect(wrapper.find("[data-test='loop-scope-forbidden']").text()).toContain("do not have access")
        expect(wrapper.find("[data-test='loop-scope-retry']").exists()).toBe(false)
    })

    it("shouldReflectTheFailuresOnlyToggleInAriaPressed", () => {
        expect(mountBar({failuresOnly: true}).find("[data-test='loop-scope-failures-only']").attributes("aria-pressed")).toBe("true")
    })
})
