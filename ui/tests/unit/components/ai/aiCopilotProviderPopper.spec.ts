import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"
import AiCopilot from "../../../../src/components/ai/AiCopilot.vue"
import AiCopilotWrapper from "../../../../src/components/ai/AiCopilotWrapper.vue"
import {useMiscStore} from "override/stores/misc"
import {AI_PROVIDER_POPPER_CLASS, aiGenerationTypes} from "../../../../src/utils/constants"

const PROVIDERS = vi.hoisted(() => [
    {id: "gemini", displayName: "Gemini"},
    {id: "openai", displayName: "OpenAI"},
])

vi.mock("@kestra-io/kestra-sdk/ai", () => ({
    providers: vi.fn().mockResolvedValue(PROVIDERS),
}))

vi.mock("axios", () => ({
    default: {
        get: vi.fn().mockResolvedValue({data: {}}),
        post: vi.fn().mockResolvedValue({data: {}}),
    },
}))

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}, name: "flow"}),
    useRouter: () => ({replace: vi.fn(), push: vi.fn()}),
}))

const globalConfig = {
    plugins: [
        createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false}),
        KestraDesignSystem,
    ],
}

function dispatchPointerDownAndClick(target: Element) {
    // vueuse onClickOutside listens for "click" on window in the CAPTURE phase,
    // so non-bubbling events still reach it. Synthetic MouseEvents have
    // detail === 0, which makes onClickOutside re-evaluate its ignore list on
    // the click itself. bubbles: false keeps the events away from Monaco's
    // body-level clipboard handler, whose cancelled promises would otherwise
    // fail the run as unhandled rejections.
    for (const type of ["pointerdown", "pointerup", "click"]) {
        target.dispatchEvent(new MouseEvent(type, {bubbles: false, composed: true}))
    }
}

describe("AI Copilot provider select popper", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        useMiscStore().configs = {isAiEnabled: true}
    })

    afterEach(() => {
        document.body.innerHTML = ""
        sessionStorage.clear()
    })

    test("provider select dropdown carries the popper class ignored by the click-outside handler", async () => {
        mount(AiCopilot, {
            attachTo: document.body,
            global: globalConfig,
            props: {
                flow: "id: test\nnamespace: test",
                conversationId: "test-conversation",
                generationType: aiGenerationTypes.FLOW,
            },
        })
        await flushPromises()

        // Open the provider select so its popper content is rendered
        const selectTrigger = document.body.querySelector(".kel-select__wrapper")
        expect(selectTrigger).not.toBeNull()
        dispatchPointerDownAndClick(selectTrigger!)
        await flushPromises()

        // The teleported dropdown must carry the class the wrapper's
        // onClickOutside ignores, otherwise clicking an option closes the copilot
        const popper = document.body.querySelector(`.${AI_PROVIDER_POPPER_CLASS}`)
        expect(popper).not.toBeNull()
        expect(popper!.querySelectorAll(".kel-select-dropdown__item").length).toBe(PROVIDERS.length)
    })

    test("clicking a provider option does not close the copilot panel", async () => {
        const wrapper = mount(AiCopilotWrapper, {
            attachTo: document.body,
            global: globalConfig,
            props: {
                flow: "id: test\nnamespace: test",
                generationType: aiGenerationTypes.FLOW,
            },
        })
        await flushPromises()

        wrapper.vm.openAiCopilot()
        await flushPromises()
        expect(wrapper.vm.aiCopilotOpened).toBe(true)

        // Open the provider select dropdown
        const selectTrigger = document.body.querySelector(".kel-select__wrapper")
        expect(selectTrigger).not.toBeNull()
        dispatchPointerDownAndClick(selectTrigger!)
        await flushPromises()

        // Click an option inside the teleported popper — copilot must stay open
        const option = document.body.querySelector(`.${AI_PROVIDER_POPPER_CLASS} .kel-select-dropdown__item`)
        expect(option).not.toBeNull()
        dispatchPointerDownAndClick(option!)
        await flushPromises()
        expect(wrapper.vm.aiCopilotOpened).toBe(true)

        // Control: clicking truly outside still closes the copilot
        const outside = document.createElement("div")
        document.body.appendChild(outside)
        dispatchPointerDownAndClick(outside)
        await flushPromises()
        expect(wrapper.vm.aiCopilotOpened).toBe(false)
    })
})
