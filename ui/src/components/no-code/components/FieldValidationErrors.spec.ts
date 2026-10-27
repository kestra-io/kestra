import {describe, expect, test} from "vitest"
import {mount} from "@vue/test-utils"
import AlertCircleOutline from "vue-material-design-icons/AlertCircleOutline.vue"
import FieldValidationErrors from "./FieldValidationErrors.vue"

const mountErrors = (errors: string[], dataTest?: string) => mount(FieldValidationErrors, {
    props: {
        errors,
        ...(dataTest !== undefined ? {dataTest} : {}),
    },
})

describe("FieldValidationErrors", () => {
    test("renders one entry per error, in order", () => {
        const errors = ["First validation error", "Second validation error", "Third validation error"]
        const wrapper = mountErrors(errors)

        const entries = wrapper.findAll(".field-error")
        expect(entries).toHaveLength(3)
        expect(entries.map((entry) => entry.text())).toEqual(errors)
    })

    test("the wrapper carries role=\"alert\"", () => {
        const wrapper = mountErrors(["Invalid field value"])

        expect(wrapper.find("div").attributes("role")).toBe("alert")
    })

    test("renders no wrapper at all for an empty array", () => {
        const wrapper = mountErrors([])

        expect(wrapper.find("div").exists()).toBe(false)
        expect(wrapper.html()).toBe("<!--v-if-->")
    })

    test("renders duplicate error strings", () => {
        const errors = ["Duplicate error message", "Duplicate error message"]
        const wrapper = mountErrors(errors)

        const entries = wrapper.findAll(".field-error")
        expect(entries).toHaveLength(2)
        expect(entries.map((entry) => entry.text())).toEqual(errors)
    })

    test("applies dataTest to every entry and defaults to field-validation-error", () => {
        const defaultWrapper = mountErrors(["Error A", "Error B"])
        const defaultEntries = defaultWrapper.findAll(".field-error")
        for (const entry of defaultEntries) {
            expect(entry.attributes("data-test")).toBe("field-validation-error")
        }

        const customWrapper = mountErrors(["Custom error"], "custom-validation-test")
        expect(customWrapper.find(".field-error").attributes("data-test")).toBe("custom-validation-test")
    })

    test("renders the icon with aria-hidden", () => {
        const wrapper = mountErrors(["Error with icon"])

        const icon = wrapper.findComponent(AlertCircleOutline)
        expect(icon.exists()).toBe(true)
        expect(icon.attributes("aria-hidden")).toBe("true")
    })
})
