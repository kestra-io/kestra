import {describe, it, expect, vi, beforeEach} from "vitest"
import {ref, nextTick} from "vue"
import {mount} from "@vue/test-utils"

const canInstall = ref(false)
const dismissed = ref(false)
const promptInstall = vi.fn().mockResolvedValue(undefined)
const dismiss = vi.fn()

vi.mock("../composables/usePwaInstall", () => ({
    usePwaInstall: () => ({
        canInstall,
        dismissed,
        promptInstall,
        dismiss,
    }),
}))

import PwaInstallPrompt from "./PwaInstallPrompt.vue"

function mountComponent() {
    return mount(PwaInstallPrompt, {
        global: {
            mocks: {
                $t: (key: string) => key,
            },
            stubs: {
                KsAlert: {
                    name: "KsAlert",
                    props: {
                        type: String,
                        title: String,
                        closable: Boolean,
                    },
                    emits: ["close"],
                    template: `
                        <div class="ks-alert" data-test="ks-alert">
                            <span class="ks-alert-title">{{ title }}</span>
                            <button v-if="closable" class="ks-alert-close" data-test="alert-close" @click="$emit('close')">close</button>
                            <slot />
                        </div>
                    `,
                },
                KsButton: {
                    name: "KsButton",
                    props: ["type", "size", "text"],
                    emits: ["click"],
                    template: `
                        <button class="ks-button" :class="[type, text ? 'is-text' : '']" @click="$emit('click')">
                            <slot />
                        </button>
                    `,
                },
            },
        },
    })
}

describe("PwaInstallPrompt", () => {
    beforeEach(() => {
        vi.clearAllMocks()
        canInstall.value = false
        dismissed.value = false
    })

    it("renders nothing when the browser cannot install", () => {
        canInstall.value = false
        dismissed.value = false

        const wrapper = mountComponent()

        expect(wrapper.find(".pwa-install-prompt").exists()).toBe(false)
        expect(wrapper.find("[data-test='ks-alert']").exists()).toBe(false)
    })

    it("renders nothing when it can install but the prompt was dismissed", () => {
        canInstall.value = true
        dismissed.value = true

        const wrapper = mountComponent()

        expect(wrapper.find(".pwa-install-prompt").exists()).toBe(false)
        expect(wrapper.find("[data-test='ks-alert']").exists()).toBe(false)
    })

    it("renders the banner when it can install and was not dismissed", () => {
        canInstall.value = true
        dismissed.value = false

        const wrapper = mountComponent()

        expect(wrapper.find(".pwa-install-prompt").exists()).toBe(true)
        expect(wrapper.find(".pwa-install-description").text()).toBe("pwa.install_description")
    })

    it("calls promptInstall when the install button is clicked", async () => {
        canInstall.value = true
        dismissed.value = false

        const wrapper = mountComponent()
        const buttons = wrapper.findAll("button")
        const installBtn = buttons.find(b => b.text().includes("pwa.install"))

        expect(installBtn).toBeDefined()
        await installBtn!.trigger("click")

        expect(promptInstall).toHaveBeenCalledTimes(1)
    })

    it("calls dismiss when the alert close control is clicked", async () => {
        canInstall.value = true
        dismissed.value = false

        const wrapper = mountComponent()
        const alertCloseBtn = wrapper.find("[data-test='alert-close']")

        expect(alertCloseBtn.exists()).toBe(true)
        await alertCloseBtn.trigger("click")

        expect(dismiss).toHaveBeenCalledTimes(1)
    })

    it("calls dismiss when the dismiss button is clicked", async () => {
        canInstall.value = true
        dismissed.value = false

        const wrapper = mountComponent()
        const buttons = wrapper.findAll("button")
        const dismissBtn = buttons.find(b => b.text().includes("pwa.dismiss"))

        expect(dismissBtn).toBeDefined()
        await dismissBtn!.trigger("click")

        expect(dismiss).toHaveBeenCalledTimes(1)
    })

    it("hides the banner without a remount when becoming dismissed", async () => {
        canInstall.value = true
        dismissed.value = false

        const wrapper = mountComponent()
        expect(wrapper.find(".pwa-install-prompt").exists()).toBe(true)

        dismissed.value = true
        await nextTick()

        expect(wrapper.find(".pwa-install-prompt").exists()).toBe(false)
    })
})
