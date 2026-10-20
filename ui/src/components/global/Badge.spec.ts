import {describe, expect, test} from "vitest"
import {KsTag, KsTooltip} from "@kestra-io/design-system"
import {i18nMount} from "../../../tests/unit/i18nMount"
import Badge from "./Badge.vue"

const mountBadge = (props: {label: string; tooltip?: string}) =>
    i18nMount(Badge, {props})

describe("Badge", () => {
    test("renders the label on the tag", () => {
        const wrapper = mountBadge({label: "Production"})

        expect(wrapper.text()).toContain("Production")
    })

    test("uses the tooltip as the tooltip content when it is given", () => {
        const wrapper = mountBadge({label: "Production", tooltip: "Live environment"})

        expect(wrapper.findComponent(KsTooltip).props("content")).toBe("Live environment")
    })

    test("falls back to the label as the tooltip content when no tooltip is given", () => {
        const wrapper = mountBadge({label: "Production"})

        expect(wrapper.findComponent(KsTooltip).props("content")).toBe("Production")
    })

    test("renders the tag as a small round primary tag", () => {
        const wrapper = mountBadge({label: "Production"})
        const tag = wrapper.findComponent(KsTag)

        expect(tag.props("type")).toBe("primary")
        expect(tag.props("size")).toBe("small")
        expect(tag.props("round")).toBe(true)
    })
})
