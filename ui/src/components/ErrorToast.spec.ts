import {describe, it, expect, vi, beforeEach} from "vitest"
import {reactive} from "vue"
import type {ToastMessage} from "../utils/problem"

vi.mock("vue-router", () => ({useRoute: () => reactive({path: "/flows", fullPath: "/flows", params: {}, query: {}, name: "flows/list", hash: ""})}))

const events = vi.fn()
vi.mock("../stores/api", () => ({useApiStore: () => ({events})}))

vi.mock("@kestra-io/design-system", async (importOriginal) => ({
    ...await importOriginal<typeof import("@kestra-io/design-system")>(),
    KsNotification: () => ({close: vi.fn()}),
}))

import ErrorToast from "./ErrorToast.vue"
import {i18nMount} from "../../tests/unit/i18nMount"

const mountToast = (message: ToastMessage) => i18nMount(ErrorToast, {props: {message}})

describe("ErrorToast", () => {
    beforeEach(() => events.mockReset())

    it("does not report a success toast as an error event", () => {
        mountToast({variant: "success", title: "Saved", content: "Flow saved"})

        expect(events).not.toHaveBeenCalled()
    })

    it("reports an error toast as an error event", () => {
        mountToast({variant: "error", title: "Boom", content: "Something failed"})

        expect(events).toHaveBeenCalledOnce()
        expect(events.mock.calls[0][0]).toMatchObject({type: "ERROR", error: {message: "Boom"}})
    })
})
