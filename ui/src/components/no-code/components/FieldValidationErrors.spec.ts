import {describe, expect, test} from "vitest"
import AlertCircleOutline from "vue-material-design-icons/AlertCircleOutline.vue"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import FieldValidationErrors from "./FieldValidationErrors.vue"

const mountErrors = (errors: string[], dataTest?: string) =>
    i18nMount(FieldValidationErrors, {props: {errors, ...(dataTest === undefined ? {} : {dataTest})}})

describe("FieldValidationErrors", () => {
    test("renders one entry per error, in order", () => {
        const errors = ["First validation error", "Second validation error", "Third validation error"]
        const wrapper = mountErrors(errors)

        const entries = wrapper.findAll('[data-test="field-validation-error"]')
        expect(entries).toHaveLength(3)
        expect(entries.map((entry) => entry.text())).toEqual(errors)
    })

    test("renders an alert wrapper for errors", () => {
        const wrapper = mountErrors(["Required field"])

        expect(wrapper.attributes("role")).toBe("alert")
    })

    test("renders no wrapper for an empty array", () => {
        const wrapper = mountErrors([])

        expect(wrapper.html()).toBe("<!--v-if-->")
    })

    test("renders duplicate errors as separate entries", () => {
        const wrapper = mountErrors(["Required field", "Required field"])

        const entries = wrapper.findAll('[data-test="field-validation-error"]')
        expect(entries).toHaveLength(2)
        expect(entries.map((entry) => entry.text())).toEqual(["Required field", "Required field"])
    })

    test("uses the default data-test value on every entry", () => {
        const wrapper = mountErrors(["Required field", "Invalid value"])

        const entries = wrapper.findAll('[data-test="field-validation-error"]')
        expect(entries).toHaveLength(2)
        expect(entries.map((entry) => entry.attributes("data-test"))).toEqual([
            "field-validation-error", "field-validation-error",
        ])
    })

    test("uses a custom data-test value on every entry", () => {
        const wrapper = mountErrors(["Required field", "Invalid value"], "custom-field-error")

        const entries = wrapper.findAll('[data-test="custom-field-error"]')
        expect(entries).toHaveLength(2)
        expect(entries.map((entry) => entry.attributes("data-test"))).toEqual([
            "custom-field-error", "custom-field-error",
        ])
    })

    test("hides the alert icon from assistive technology", () => {
        const wrapper = mountErrors(["Required field"])

        const icon = wrapper.findComponent(AlertCircleOutline)
        expect(icon.exists()).toBe(true)
        expect(icon.attributes("aria-hidden")).toBe("true")
    })
})
