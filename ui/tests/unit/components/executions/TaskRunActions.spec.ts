import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {shallowMount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {useAuthStore} from "override/stores/auth"
import resource from "../../../../src/models/resource"
import action from "../../../../src/models/action"

vi.mock("vue-router", () => ({
    useRoute: () => ({params: {namespace: "ns-1"}}),
    useRouter: () => ({}),
}))

import TaskRunActions from "../../../../src/components/executions/TaskRunActions.vue"

const i18n = createI18n({legacy: false, locale: "en", missingWarn: false, fallbackWarn: false, messages: {en: {}}})

function hasDeleteLogs() {
    const wrapper = shallowMount(TaskRunActions, {
        props: {
            taskRun: {id: "tr-1", taskId: "task-1", state: {current: "SUCCESS", histories: []}},
            execution: {id: "ex-1", namespace: "ns-1", flowId: "flow-1", state: {current: "SUCCESS", histories: []}},
        },
        global: {
            plugins: [i18n],
            stubs: {
                KsDropdown: {template: "<div><slot name=\"dropdown\" /></div>"},
                KsDropdownMenu: {template: "<div><slot /></div>"},
                KsDropdownItem: {template: "<div><slot /></div>"},
            },
        },
    })
    return wrapper.find("[data-test='task-run-delete-logs']").exists()
}

describe("TaskRunActions", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    it("offers Delete logs only to a user who can delete the execution", () => {
        expect(hasDeleteLogs()).toBe(true)

        vi.spyOn(useAuthStore().user!, "isAllowed").mockImplementation((resourceName, actionName) => !(resourceName === resource.EXECUTION && actionName === action.DELETE))

        expect(hasDeleteLogs()).toBe(false)
    })
})
