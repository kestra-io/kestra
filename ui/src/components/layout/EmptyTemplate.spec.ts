import {describe, expect, test} from "vitest"
import {mount} from "@vue/test-utils"
import EmptyTemplate from "./EmptyTemplate.vue"

describe("EmptyTemplate", () => {
    test("renders the default slot", () => {
        const wrapper = mount(EmptyTemplate, {slots: {default: "<p data-test=\"content\">Nothing here yet</p>"}})

        expect(wrapper.get("[data-test=content]").text()).toBe("Nothing here yet")
    })
})
