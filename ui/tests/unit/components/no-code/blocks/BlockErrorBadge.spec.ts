import {describe, expect, test} from "vitest"
import KestraDesignSystem from "@kestra-io/design-system"
import BlockErrorBadge from "../../../../../src/components/no-code/blocks/BlockErrorBadge.vue"
import {i18nMount} from "../../../i18nMount"

const globalConfig = {
    plugins: [KestraDesignSystem],
}

function mountBadge(issues: string[]) {
    return i18nMount(BlockErrorBadge, {global: globalConfig, props: {issues}})
}

describe("BlockErrorBadge", () => {
    test("renders nothing when there are no issues", () => {
        const wrapper = mountBadge([])
        expect(wrapper.find("[data-test='block-card-warning']").exists()).toBe(false)
    })

    test("renders the badge without a count for a single issue", () => {
        const wrapper = mountBadge(["uri: must not be null"])
        expect(wrapper.find("[data-test='block-card-warning']").exists()).toBe(true)
        expect(wrapper.find(".block-error-badge-count").exists()).toBe(false)
        expect(wrapper.find("[data-test='block-card-warning']").attributes("aria-label")).toBeTruthy()
    })

    test("shows the issue count when there is more than one", () => {
        const wrapper = mountBadge(["uri: must not be null", "message: must not be null"])
        expect(wrapper.find(".block-error-badge-count").text()).toBe("2")
    })
})
