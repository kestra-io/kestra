import {describe, it, expect, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import en from "../../../src/translations/en.json"

// The "Fix with AI" button only renders in a flow editor context.
vi.mock("vue-router", () => ({useRoute: () => ({name: "flows/update"})}))

const promptCopilot = vi.fn()
vi.mock("override/stores/misc", () => ({useMiscStore: () => ({promptCopilot})}))

import ErrorToastContainer from "../../../src/components/ErrorToastContainer.vue"

const i18n = createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false, messages: en})
const stubs = {
    KsButton: {name: "KsButton", template: "<button><slot /></button>"},
    KsMarkdown: {name: "KsMarkdown", template: "<div />"},
}

describe("ErrorToastContainer — Fix with AI", () => {
    it("opens the v2 copilot seeded with the error prompt, and closes the toast", async () => {
        promptCopilot.mockReset()
        const onClose = vi.fn()
        const w = mount(ErrorToastContainer, {
            props: {
                message: {message: "boom"},
                items: [{path: "tasks[0]", message: "bad type"}],
                onClose,
            },
            global: {plugins: [i18n], stubs},
        })

        await w.find("button").trigger("click")

        expect(onClose).toHaveBeenCalledOnce()
        expect(promptCopilot).toHaveBeenCalledWith(
            "Fix the following error in the flow:\nboom\n\nAt tasks[0]: bad type",
        )
    })
})
