import {describe, it, expect} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import FieldNavBreadcrumb from "../../../src/components/no-code/components/FieldNavBreadcrumb.vue"

const i18n = createI18n({
    legacy: false,
    locale: "en",
    messages: {en: {no_code: {nav: {back: "Back", breadcrumb_aria: "Breadcrumb"}}}},
})

const frames = [
    {path: "workerSelector", label: "workerSelector", schema: {}},
    {path: "workerSelector.match", label: "match", schema: {}},
]

function render() {
    return mount(FieldNavBreadcrumb, {
        props: {frames, rootLabel: "backup_users_db"},
        global: {plugins: [i18n]},
    })
}

const crumb = (wrapper: ReturnType<typeof render>, text: string) =>
    wrapper.findAll("button").find((b) => b.text() === text)

describe("FieldNavBreadcrumb", () => {
    it("shows the root label and one crumb per frame", () => {
        const wrapper = render()
        expect(crumb(wrapper, "backup_users_db")).toBeDefined()
        expect(crumb(wrapper, "workerSelector")).toBeDefined()
        expect(crumb(wrapper, "match")).toBeDefined()
    })

    it("navigates to the root when the root crumb is clicked", async () => {
        const wrapper = render()
        await crumb(wrapper, "backup_users_db")!.trigger("click")
        expect(wrapper.emitted("navigate")).toEqual([[-1]])
    })

    it("navigates to a frame by its index", async () => {
        const wrapper = render()
        await crumb(wrapper, "workerSelector")!.trigger("click")
        expect(wrapper.emitted("navigate")).toEqual([[0]])
    })

    it("disables the current (last) crumb so it can't navigate to itself", async () => {
        const wrapper = render()
        const current = crumb(wrapper, "match")!
        expect(current.attributes("disabled")).toBeDefined()
        await current.trigger("click")
        expect(wrapper.emitted("navigate")).toBeUndefined()
    })

    it("emits back when the back button is clicked", async () => {
        const wrapper = render()
        await wrapper.get("button[aria-label='Back']").trigger("click")
        expect(wrapper.emitted("back")).toHaveLength(1)
    })
})
