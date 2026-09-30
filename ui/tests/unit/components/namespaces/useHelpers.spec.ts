import {describe, it, expect, vi} from "vitest"
import {defineComponent, h} from "vue"
import {createPinia} from "pinia"

vi.mock("vue-router", () => ({
    useRoute: () => ({params: {id: "company.team"}}),
}))

import {useHelpers} from "../../../../src/components/namespaces/utils/useHelpers"
import {i18nMount} from "../../i18nMount"

function mountHelpers() {
    let captured: ReturnType<typeof useHelpers>
    const wrapper = i18nMount(defineComponent({
        setup() {
            captured = useHelpers()
            return () => h("div")
        },
    }), {global: {plugins: [createPinia()]}})
    wrapper.unmount()
    return captured!
}

describe("namespaces useHelpers — embedded tab title suppression", () => {
    it("flows tab forwards embed:true so Flows.vue doesn't clobber the namespace title", () => {
        const {tabs} = mountHelpers()
        const flowsTab = tabs.value.find((tab) => tab.name === "flows")
        expect(flowsTab?.props?.embed).toBe(true)
    })

    it("executions tab forwards embed:true so Executions.vue doesn't clobber the namespace title", () => {
        const {tabs} = mountHelpers()
        const executionsTab = tabs.value.find((tab) => tab.name === "executions")
        expect(executionsTab?.props?.embed).toBe(true)
    })
})
