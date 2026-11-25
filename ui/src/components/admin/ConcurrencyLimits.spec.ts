import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, type VueWrapper} from "@vue/test-utils"
import {i18nMount} from "../../../tests/unit/i18nMount"
import ConcurrencyLimits from "./ConcurrencyLimits.vue"

const {toastError, put, get} = vi.hoisted(() => ({toastError: vi.fn(), put: vi.fn(), get: vi.fn()}))

vi.mock("../../utils/toast", () => ({useToast: () => ({requestError: toastError})}))
vi.mock("@kestra-io/kestra-sdk", () => ({useClient: () => ({get, put})}))
vi.mock("override/utils/route", () => ({apiUrlWithTenant: () => "/api/v1/main"}))
vi.mock("override/stores/auth", () => ({useAuthStore: () => ({user: {instanceOwner: true}})}))
vi.mock("../../composables/useRouteContext", () => ({default: () => {}}))

const PROBLEM = {type: "https://kestra.io/problems/validation-failed", title: "Validation failed", status: 422, detail: "running: must be greater than or equal to 0"}

const MAX_RUNNING_COUNT = 2_147_483_647

function httpError(response: object | undefined) {
    return Object.assign(new Error("Request failed"), response ? {status: 422, problem: PROBLEM, response: {status: 422, data: PROBLEM}} : {})
}

function mockRows() {
    get.mockResolvedValue({status: 200, data: {results: [{tenantId: "main", namespace: "io.kestra", flowId: "flow", running: 2, queued: 0}], total: 1}})
}

/** Mounts the page and opens the edit dialog of the only row. */
async function mountWithOpenDialog() {
    mockRows()
    const wrapper = i18nMount(ConcurrencyLimits, {
        attachTo: document.body,
        global: {stubs: {TopNavBar: true, Empty: true}},
    })
    await flushPromises()
    await wrapper.get("[data-test=edit-running]").trigger("click")
    await flushPromises()
    return wrapper
}

/** The dialog is teleported to the body, so it is queried from there rather than from the wrapper. */
function saveButton() {
    const button = document.body.querySelector<HTMLButtonElement>("[data-test=save-running]")
    if (!button) {
        throw new Error("Save button not found")
    }
    return button
}

function errorMessage() {
    return document.body.querySelector<HTMLElement>("[data-test=running-count-error]")
}

/** Types a value into the dialog's number input, the way a user would. */
async function typeRunningCount(value: number) {
    const input = document.body.querySelector<HTMLInputElement>("[data-test=running-count] input")
    if (!input) {
        throw new Error("Running count input not found")
    }
    input.value = String(value)
    input.dispatchEvent(new Event("input", {bubbles: true}))
    input.dispatchEvent(new Event("change", {bubbles: true}))
    await flushPromises()
}

const isDisabled = (button: HTMLButtonElement) => button.disabled || button.hasAttribute("disabled")

async function saveWith(failure: Error) {
    put.mockRejectedValue(failure)

    const wrapper = await mountWithOpenDialog()
    saveButton().click()
    await flushPromises()
    wrapper.unmount()
    await flushPromises()
}

describe("ConcurrencyLimits", () => {
    let wrapper: VueWrapper | undefined

    beforeEach(() => {
        vi.clearAllMocks()
        wrapper = undefined
    })

    afterEach(() => {
        wrapper?.unmount()
        document.body.innerHTML = ""
        document.body.classList.remove("kel-popup-parent--hidden")
    })

    it("shouldReportTheSaveFailure", async () => {
        await saveWith(httpError(undefined))

        expect(toastError).toHaveBeenCalledOnce()
        expect(toastError).toHaveBeenCalledWith(expect.any(Error), "concurrency_limit.save_error")
    })

    describe("running count range", () => {
        it("startsValidWithTheCurrentValue", async () => {
            wrapper = await mountWithOpenDialog()

            expect(isDisabled(saveButton())).toBe(false)
            expect(errorMessage()).toBeNull()
        })

        it.each([0, 5, MAX_RUNNING_COUNT])("acceptsTheValue %s", async (value) => {
            wrapper = await mountWithOpenDialog()
            await typeRunningCount(value)

            expect(isDisabled(saveButton())).toBe(false)
            expect(errorMessage()).toBeNull()
        })

        it.each([MAX_RUNNING_COUNT + 1, 99_999_999_999, -1])("rejectsTheValue %s", async (value) => {
            wrapper = await mountWithOpenDialog()
            await typeRunningCount(value)

            expect(isDisabled(saveButton())).toBe(true)
            expect(errorMessage()).not.toBeNull()
        })

        it("neverSendsAnInvalidValueToTheServer", async () => {
            wrapper = await mountWithOpenDialog()
            await typeRunningCount(99_999_999_999)

            // The button is disabled, but the handler must hold on its own as well.
            saveButton().click()
            await flushPromises()

            expect(put).not.toHaveBeenCalled()
        })

        it("sendsAValidValueToTheServer", async () => {
            put.mockResolvedValue({})
            wrapper = await mountWithOpenDialog()
            await typeRunningCount(MAX_RUNNING_COUNT)

            saveButton().click()
            await flushPromises()

            expect(put).toHaveBeenCalledOnce()
            expect(put).toHaveBeenCalledWith("/api/v1/main/concurrency-limit", {
                namespace: "io.kestra",
                flowId: "flow",
                running: MAX_RUNNING_COUNT,
            })
        })
    })
})
