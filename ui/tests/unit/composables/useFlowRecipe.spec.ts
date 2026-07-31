import {describe, it, expect, vi} from "vitest"
import {mount, flushPromises} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {defineComponent, h} from "vue"

const installedPlugins: unknown[] = []

vi.mock("../../../src/stores/plugins", () => ({
    usePluginsStore: () => ({ensurePlugins: vi.fn().mockResolvedValue(installedPlugins)}),
}))

import {useFlowRecipe} from "../../../src/composables/useFlowRecipe"
import {NOTIFY_TASK_CONFIGS} from "../../../src/utils/recipeToYaml"

const i18n = createI18n({legacy: false, locale: "en", missingWarn: false, fallbackWarn: false, messages: {en: {}}})

function setup() {
    let api!: ReturnType<typeof useFlowRecipe>
    const Comp = defineComponent({setup() {
        api = useFlowRecipe()
        return () => h("div")
    }})
    mount(Comp, {global: {plugins: [i18n]}})
    return api
}

describe("useFlowRecipe", () => {
    it("is invalid until a notify channel is chosen", () => {
        const r = setup()
        expect(r.hasNotifyChannel.value).toBe(false)
        expect(r.isValid.value).toBe(false)

        r.toggleNotify("slack")
        expect(r.hasNotifyChannel.value).toBe(true)
    })

    it("an execution trigger needs a channel and at least one state", () => {
        const r = setup()
        r.toggleNotify("slack")
        expect(r.recipe.triggerType).toBe("execution")
        expect(r.isValid.value).toBe(true)

        r.recipe.states.slice().forEach((s) => r.toggleState(s))
        expect(r.recipe.states).toHaveLength(0)
        expect(r.isValid.value).toBe(false)
    })

    it("a schedule trigger needs a cron", () => {
        const r = setup()
        r.toggleNotify("email")
        r.recipe.triggerType = "schedule"
        expect(r.isValid.value).toBe(true)

        r.recipe.cron = ""
        expect(r.isValid.value).toBe(false)
    })

    it("a webhook trigger needs a key", () => {
        const r = setup()
        r.toggleNotify("teams")
        r.recipe.triggerType = "webhook"
        expect(r.isValid.value).toBe(false)

        r.recipe.webhookKey = "deploy"
        expect(r.isValid.value).toBe(true)
    })

    it("an other trigger needs a selected trigger type, and re-selecting clears it", () => {
        const r = setup()
        r.toggleNotify("slack")
        r.recipe.triggerType = "other"
        expect(r.isValid.value).toBe(false)

        r.setOtherTriggerType("io.kestra.plugin.core.trigger.Flow")
        expect(r.isValid.value).toBe(true)

        r.setOtherTriggerType("io.kestra.plugin.core.trigger.Flow")
        expect(r.recipe.otherTriggerType).toBe("")
    })

    it("assumes every channel is available until plugins report otherwise", () => {
        const r = setup()
        expect(r.channelAvailability.value).toEqual({slack: true, teams: true, email: true, custom: true})
    })

    it("treats the custom channel as a valid notification choice", () => {
        const r = setup()
        r.toggleNotify("custom")
        expect(r.hasNotifyChannel.value).toBe(true)
    })

    it("does not leak state changes into the next recipe", () => {
        const first = setup()
        first.toggleState("SUCCESS")
        first.toggleState("FAILED")
        first.toggleNotify("slack")

        const second = setup()
        expect(second.recipe.states).toEqual(["FAILED", "WARNING"])
        expect(second.recipe.notify.slack).toBe(false)
    })

    it("stops counting a channel whose plugin is missing for the current trigger type", async () => {
        // Given — only the execution-trigger Slack task is installed
        installedPlugins.length = 0
        installedPlugins.push({tasks: [{cls: NOTIFY_TASK_CONFIGS.slack.executionFqcn}]})

        const r = setup()
        await flushPromises()
        r.toggleNotify("slack")
        expect(r.channelAvailability.value.slack).toBe(true)
        expect(r.isValid.value).toBe(true)

        // When — the webhook variant is not installed
        r.recipe.triggerType = "webhook"
        r.recipe.webhookKey = "my-key"

        // Then — the selection survives, but it no longer makes the recipe valid
        expect(r.recipe.notify.slack).toBe(true)
        expect(r.channelAvailability.value.slack).toBe(false)
        expect(r.hasNotifyChannel.value).toBe(false)
        expect(r.isValid.value).toBe(false)
        expect(r.unavailableSelectedChannels.value).toEqual(["slack"])

        // And — switching back restores it
        r.recipe.triggerType = "execution"
        expect(r.hasNotifyChannel.value).toBe(true)
        expect(r.unavailableSelectedChannels.value).toEqual([])

        installedPlugins.length = 0
    })

    it("reset restores the default watched states", () => {
        const r = setup()
        r.toggleState("FAILED")
        r.toggleState("KILLED")

        r.reset()
        expect(r.recipe.states).toEqual(["FAILED", "WARNING"])
    })

    it("reset clears the chosen channels and inputs", () => {
        const r = setup()
        r.toggleNotify("slack")
        r.recipe.webhookKey = "x"

        r.reset()
        expect(r.hasNotifyChannel.value).toBe(false)
        expect(r.recipe.webhookKey).toBe("")
    })
})
