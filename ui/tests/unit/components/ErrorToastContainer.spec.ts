import {describe, it, expect, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import en from "../../../src/translations/en.json"

// The "Fix with AI" button only renders in a flow editor context.
vi.mock("vue-router", () => ({useRoute: () => ({name: "flows/update", params: {id: "my-flow"}})}))

const promptCopilot = vi.fn()
vi.mock("override/stores/misc", () => ({useMiscStore: () => ({promptCopilot})}))

import ErrorToastContainer from "../../../src/components/ErrorToastContainer.vue"

const i18n = createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false, messages: en})
const stubs = {
    KsButton: {name: "KsButton", template: "<button><slot /></button>"},
    KsMarkdown: {name: "KsMarkdown", template: "<div />"},
    KsId: {name: "KsId", props: ["value"], template: "<span class=\"ks-id\">{{ value }}</span>"},
}

const mountContainer = (props: Record<string, unknown>) =>
    mount(ErrorToastContainer, {
        props: {detail: "", items: [], ...props},
        global: {plugins: [i18n], stubs},
    })

describe("ErrorToastContainer", () => {
    it("opens the copilot seeded with the error prompt, and closes the toast", async () => {
        promptCopilot.mockReset()
        const onClose = vi.fn()
        const w = mountContainer({
            detail: "boom",
            items: [{detail: "bad type", pointer: "/tasks/0/type", path: "tasks[my-task].type"}],
            onClose,
        })

        await w.find("button").trigger("click")

        expect(onClose).toHaveBeenCalledOnce()
        expect(promptCopilot).toHaveBeenCalledWith(
            "Fix the following error in the flow:\nboom\n\ntasks[my-task].type: bad type",
            {title: "Fix flow my-flow", newThread: true},
        )
    })

    it("shows the human path and keeps the JSON Pointer as the tooltip", () => {
        // The path names the task by id, which is what the user sees in their YAML; the pointer is
        // index-based and only useful to a machine.
        const w = mountContainer({
            items: [{detail: "must not be null", pointer: "/tasks/0/type", path: "tasks[my-task].type"}],
        })

        const code = w.find("code")
        expect(code.text()).toBe("tasks[my-task].type")
        expect(code.attributes("title")).toBe("/tasks/0/type")
    })

    it("falls back to the pointer when there is no friendly path", () => {
        const w = mountContainer({items: [{detail: "must not be null", pointer: "/namespace"}]})

        expect(w.find("code").text()).toBe("/namespace")
    })

    it("renders the detail as markdown when there are no field errors", () => {
        const w = mountContainer({detail: "A flow with id 'x' already exists.", items: []})

        expect(w.findComponent({name: "KsMarkdown"}).exists()).toBe(true)
        expect(w.find("ul").exists()).toBe(false)
    })

    it("shows a copyable trace id only when one is present", () => {
        expect(mountContainer({detail: "boom"}).find(".ks-id").exists()).toBe(false)

        const w = mountContainer({detail: "boom", traceId: "4bf92f3577b34da6a3ce929d0e0e4736"})
        expect(w.find(".ks-id").text()).toBe("4bf92f3577b34da6a3ce929d0e0e4736")
    })

    it("localizes a field error that carries its own problem type", () => {
        // How bulk operations get per-item translations without the server sending i18n keys.
        const w = mountContainer({
            items: [{
                detail: "The server is temporarily unavailable. Please try again later.",
                path: "executions[abc]",
                type: "https://kestra.io/docs/api-reference/problems/service-unavailable",
            }],
        })

        expect(w.find("li").text()).toContain(en.en.errors.problems["service-unavailable"].detail)
    })
})
