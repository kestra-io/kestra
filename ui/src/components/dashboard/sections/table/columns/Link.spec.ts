import {describe, expect, test} from "vitest"
import {KsId} from "@kestra-io/design-system"
import {i18nMount} from "../../../../../../tests/unit/i18nMount"
import LinkColumn from "./Link.vue"

const columns = {
    id: {field: "ID"},
    flow: {field: "FLOW_ID"},
    namespace: {field: "NAMESPACE"},
}

describe("dashboard table Link column", () => {
    test("renders an execution id through KsId so the full id stays reachable", () => {
        const id = "7Hq2LmZx9KcVbN4pRtYs1a"
        const wrapper = i18nMount(LinkColumn, {
            props: {execution: true, field: "id", columns, row: {id, flow: "daily", namespace: "company.team"}},
        })

        expect(wrapper.findComponent(KsId).props("value")).toBe(id)
    })
})
