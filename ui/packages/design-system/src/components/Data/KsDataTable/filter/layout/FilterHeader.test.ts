import {describe, test, expect} from "vitest"
import FilterHeader from "./FilterHeader.vue"
import {i18nMount} from "../../../../../../tests/units/i18nMount"

describe("FilterHeader", () => {
    test("renders the label", () => {
        const wrapper = i18nMount(FilterHeader, {
            props: {label: "Status"},
        })
        const label = wrapper.find(".filter-label")
        expect(label.exists()).toBe(true)
        expect(label.text()).toBe("Status")
    })

    test("renders description only when passed", () => {
        const withoutDesc = i18nMount(FilterHeader, {
            props: {label: "Status"},
        })
        expect(withoutDesc.find(".filter-description").exists()).toBe(false)

        const withDesc = i18nMount(FilterHeader, {
            props: {label: "Status", description: "Filter by execution status"},
        })
        const desc = withDesc.find(".filter-description")
        expect(desc.exists()).toBe(true)
        expect(desc.text()).toBe("Filter by execution status")
    })

    test("renders trailing slot ahead of the close button", () => {
        const wrapper = i18nMount(FilterHeader, {
            props: {label: "Status"},
            slots: {
                trailing: "<span class=\"custom-action\">Action</span>",
            },
        })

        const trailingContainer = wrapper.find(".filter-header-trailing")
        expect(trailingContainer.exists()).toBe(true)

        const children = trailingContainer.element.children
        expect(children.length).toBe(2)
        expect(children[0].classList.contains("custom-action")).toBe(true)
        expect(children[0].textContent).toBe("Action")
        expect(children[1].tagName.toLowerCase()).toBe("button")
    })

    test("clicking the close button emits close", async () => {
        const wrapper = i18nMount(FilterHeader, {
            props: {label: "Status"},
        })

        const button = wrapper.find(".filter-header-trailing button")
        expect(button.exists()).toBe(true)

        await button.trigger("click")
        expect(wrapper.emitted("close")).toHaveLength(1)
    })

    test("the close button carries an aria-label", () => {
        const wrapper = i18nMount(FilterHeader, {
            props: {label: "Status"},
        })

        const button = wrapper.find(".filter-header-trailing button")
        expect(button.attributes("aria-label")).toBeTruthy()
        expect(button.attributes("aria-label")).toBe("filter.close")
    })
})
