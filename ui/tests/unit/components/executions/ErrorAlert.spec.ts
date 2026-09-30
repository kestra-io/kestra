import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import ErrorAlert from "../../../../src/components/executions/overview/components/main/ErrorAlert.vue"
import {useExecutionsStore, type Execution} from "../../../../src/stores/executions"
import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
import ChevronUp from "vue-material-design-icons/ChevronUp.vue"
import {i18nMount} from "../../i18nMount"

const mockExecution = {
    id: "exec-123",
    namespace: "company.team",
    flowId: "my-flow",
    tenantId: "default",
    kind: "FLOW",
} as unknown as Execution

describe("ErrorAlert", () => {
    beforeEach(() => {
        localStorage.clear()
        setActivePinia(createPinia())
    })

    afterEach(() => {
        localStorage.clear()
    })

    function mountErrorAlert() {
        return i18nMount(ErrorAlert, {
            props: {execution: mockExecution},
            global: {
                stubs: {
                    RouterLink: {props: ["to"], template: "<a><slot /></a>"},
                    "router-link": {props: ["to"], template: "<a><slot /></a>"},
                    LogLine: {
                        props: ["log", "level", "excludeMetas"],
                        template: "<div class=\"log-line\">{{ log.message }}</div>",
                    },
                },
            },
        })
    }

    it("renders the error message in the preview and strips backticks", async () => {
        const store = useExecutionsStore()
        vi.spyOn(store, "loadLogs").mockResolvedValue([
            {level: "ERROR", message: "Error with `backticks` in message"},
        ] as any)

        const wrapper = mountErrorAlert()
        await flushPromises()

        const preview = wrapper.find(".error-preview")
        expect(preview.exists()).toBe(true)
        expect(preview.text()).toContain("Error with backticks in message")
        expect(wrapper.findComponent(ChevronDown).exists()).toBe(true)
        expect(wrapper.find(".logs").exists()).toBe(false)
    })

    it("toggles expanded view when expand button is clicked", async () => {
        const store = useExecutionsStore()
        vi.spyOn(store, "loadLogs").mockResolvedValue([
            {level: "ERROR", message: "First error"},
            {level: "ERROR", message: "Second error"},
        ] as any)

        const wrapper = mountErrorAlert()
        await flushPromises()

        const expandBtn = wrapper.get(".expand-btn")
        await expandBtn.trigger("click")

        expect(wrapper.find(".error-preview").exists()).toBe(false)
        expect(wrapper.find(".logs").exists()).toBe(true)
        expect(wrapper.findComponent(ChevronUp).exists()).toBe(true)

        await expandBtn.trigger("click")
        expect(wrapper.find(".error-preview").exists()).toBe(true)
        expect(wrapper.find(".logs").exists()).toBe(false)
        expect(wrapper.findComponent(ChevronDown).exists()).toBe(true)
    })
})
