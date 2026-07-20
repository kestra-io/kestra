import {describe, it, expect, vi} from "vitest"
import {defineComponent, h} from "vue"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"

vi.mock("vue-router", () => ({
    useRoute: () => ({params: {id: "company.team"}}),
}))

import {useHelpers} from "../../../../src/components/namespaces/utils/useHelpers"

const i18n = createI18n({legacy: false, locale: "en", messages: {en: {}}, missingWarn: false, fallbackWarn: false})

function mountHelpers() {
    let captured: ReturnType<typeof useHelpers>
    const wrapper = mount(defineComponent({
        setup() {
            captured = useHelpers()
            return () => h("div")
        },
    }), {global: {plugins: [i18n]}})
    wrapper.unmount()
    return captured!
}

describe("namespaces useHelpers — embedded tab title suppression", () => {
    it("flows tab forwards embed:true so Flows.vue doesn't clobber the namespace title", () => {
        const {tabs} = mountHelpers()
        const flowsTab = tabs.find((tab) => tab.name === "flows")
        expect(flowsTab?.props?.embed).toBe(true)
    })

    it("executions tab forwards embed:true so Executions.vue doesn't clobber the namespace title", () => {
        const {tabs} = mountHelpers()
        const executionsTab = tabs.find((tab) => tab.name === "executions")
        expect(executionsTab?.props?.embed).toBe(true)
    })
})
