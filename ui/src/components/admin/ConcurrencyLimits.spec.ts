import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../tests/unit/i18nMount"
import ConcurrencyLimits from "./ConcurrencyLimits.vue"

const {toastError, put, get} = vi.hoisted(() => ({toastError: vi.fn(), put: vi.fn(), get: vi.fn()}))

vi.mock("../../utils/toast", () => ({useToast: () => ({error: toastError})}))
vi.mock("@kestra-io/kestra-sdk", () => ({useClient: () => ({get, put})}))
vi.mock("override/utils/route", () => ({apiUrlWithTenant: () => "/api/v1/main"}))
vi.mock("override/stores/auth", () => ({useAuthStore: () => ({user: {instanceOwner: true}})}))
vi.mock("../../composables/useRouteContext", () => ({default: () => {}}))

const PROBLEM = {type: "https://kestra.io/problems/validation-failed", title: "Validation failed", status: 422, detail: "running: must be greater than or equal to 0"}

function httpError(response: object | undefined) {
    return Object.assign(new Error("Request failed"), response ? {status: 422, problem: PROBLEM, response: {status: 422, data: PROBLEM}} : {})
}

async function saveWith(failure: Error) {
    get.mockResolvedValue({status: 200, data: {results: [{tenantId: "main", namespace: "io.kestra", flowId: "flow", running: 2, queued: 0}], total: 1}})
    put.mockRejectedValue(failure)

    const wrapper = i18nMount(ConcurrencyLimits, {
        attachTo: document.body,
        global: {stubs: {TopNavBar: true, Empty: true}},
    })
    await flushPromises()
    await wrapper.get("[data-test=edit-running]").trigger("click")
    await flushPromises()
    document.body.querySelector<HTMLButtonElement>("[data-test=save-running]")?.click()
    await flushPromises()
    wrapper.unmount()
    await flushPromises()
}

describe("ConcurrencyLimits", () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    afterEach(() => {
        document.body.innerHTML = ""
        document.body.classList.remove("kel-popup-parent--hidden")
    })

    it("shouldNotToastTwiceWhenTheFailureIsAlreadyReportedCentrally", async () => {
        await saveWith(httpError(PROBLEM))

        expect(put).toHaveBeenCalledOnce()
        expect(toastError).not.toHaveBeenCalled()
    })

    it("shouldToastWhenTheFailureHasNoResponse", async () => {
        await saveWith(httpError(undefined))

        expect(toastError).toHaveBeenCalledOnce()
        expect(toastError).toHaveBeenCalledWith("concurrency_limit.save_error")
    })
})
