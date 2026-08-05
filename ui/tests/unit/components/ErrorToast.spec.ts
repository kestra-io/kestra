import {describe, it, expect, vi, beforeEach} from "vitest"
import {mount} from "@vue/test-utils"
import {reactive} from "vue"
import {createI18n} from "vue-i18n"
import en from "../../../src/translations/en.json"

const notificationMock = vi.fn()
const closeMock = vi.fn()
const eventsMock = vi.fn()
const route = reactive({
    name: "flows/update",
    path: "/ui/flows/update",
    fullPath: "/ui/flows/update",
    params: {},
    query: {},
})

vi.mock("vue-router", () => ({useRoute: () => route}))

vi.mock("@kestra-io/design-system", () => ({
    KsNotification: (...args: unknown[]) => {
        notificationMock(...args)
        return {close: closeMock}
    },
}))

vi.mock("../../../src/stores/api", () => ({
    useApiStore: () => ({events: eventsMock}),
}))

vi.mock("override/stores/misc", () => ({useMiscStore: () => ({promptCopilot: vi.fn()})}))

import ErrorToast from "../../../src/components/ErrorToast.vue"

const i18n = createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false, messages: en})
const stubs = {
    KsButton: {name: "KsButton", template: "<button><slot /></button>"},
    KsMarkdown: {name: "KsMarkdown", template: "<div />"},
}

const errorMessage = () => ({
    message: "A flow cannot have no tasks",
    content: {
        message: "A flow cannot have no tasks",
    },
})

describe("ErrorToast", () => {
    beforeEach(() => {
        notificationMock.mockReset()
        closeMock.mockReset()
        eventsMock.mockReset()
        route.name = "flows/update"
    })

    it("shows a notification for each new error message while mounted", async () => {
        const w = mount(ErrorToast, {
            props: {message: errorMessage()},
            global: {plugins: [i18n], stubs},
        })

        await w.vm.$nextTick()

        expect(notificationMock).toHaveBeenCalledTimes(1)

        // A second error while the component is still mounted must open a fresh notification.
        await w.setProps({message: errorMessage()})

        expect(notificationMock).toHaveBeenCalledTimes(2)

        // The previously open notification is closed before the new one opens.
        expect(closeMock).toHaveBeenCalled()
    })

    it("closes the notification on route change without opening a new one", async () => {
        const w = mount(ErrorToast, {
            props: {message: errorMessage()},
            global: {plugins: [i18n], stubs},
        })

        await w.vm.$nextTick()
        expect(notificationMock).toHaveBeenCalledTimes(1)

        route.name = "flows/index"

        await w.vm.$nextTick()

        expect(closeMock).toHaveBeenCalled()
        // Route change only closes; it must not open a fresh notification.
        expect(notificationMock).toHaveBeenCalledTimes(1)
    })
})
