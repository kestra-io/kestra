import {describe, expect, test} from "vitest"
import {KsAlert} from "@kestra-io/design-system"
import type {Check} from "../../stores/executions"
import {i18nMount} from "../../../tests/unit/i18nMount"
import FailingChecksAlert from "./FailingChecksAlert.vue"

const check = (message: string, style: Check["style"], behavior: Check["behavior"] = "BLOCK_EXECUTION"): Check =>
    ({when: "{{ true }}", message, style, behavior})

const mountChecks = (checks: Check[]) => i18nMount(FailingChecksAlert, {
    props: {checks},
    messages: {checks_failing: "{count} check failing | {count} checks failing", expand: "Expand", collapse: "Collapse"},
})

describe("FailingChecksAlert", () => {
    test("renders one alert per failing check, coloured by its style", () => {
        const wrapper = mountChecks([check("No vCPU on a load balancer", "ERROR"), check("Heads up", "INFO", "CREATE_EXECUTION")])

        const alerts = wrapper.findAllComponents(KsAlert)
        expect(alerts.map(alert => alert.text())).toEqual(["No vCPU on a load balancer", "Heads up"])
        expect(alerts.map(alert => alert.props("type"))).toEqual(["error", "info"])
    })

    test("collapses many checks into a count coloured by the most severe one, and expands to list them", async () => {
        const messages = ["one", "two", "three", "four"]
        const wrapper = mountChecks([
            check(messages[0], "INFO"),
            check(messages[1], "WARNING"),
            check(messages[2], "ERROR"),
            check(messages[3], "SUCCESS"),
        ])

        const summary = wrapper.findAllComponents(KsAlert)
        expect(summary).toHaveLength(1)
        expect(summary[0].props("type")).toBe("error")
        expect(summary[0].text()).toContain("4 checks failing")

        const toggle = wrapper.find("[data-test='failing-checks-toggle']")
        expect(toggle.attributes("aria-expanded")).toBe("false")
        await toggle.trigger("click")

        const listed = wrapper.findAllComponents(KsAlert).slice(1)
        expect(listed.map(alert => alert.text())).toEqual(messages)
        expect(toggle.text()).toBe("Collapse")
        expect(toggle.attributes("aria-expanded")).toBe("true")
    })
})
