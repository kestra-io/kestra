import {describe, it, expect, vi} from "vitest"
import {defineComponent, h} from "vue"
import {flushPromises} from "@vue/test-utils"
import en from "../../translations/en.json"

vi.mock("vue-router", () => ({
    useRoute: () => ({name: "flows/update", params: {}}),
    useRouter: () => ({push: vi.fn()}),
}))

const toastError = vi.hoisted(() => vi.fn())
vi.mock("../../utils/toast", () => ({
    useToast: () => ({saved: vi.fn(), success: vi.fn(), error: toastError, confirm: vi.fn(), warning: vi.fn()}),
}))

const flowStore = vi.hoisted(() => ({
    publishDraft: vi.fn(),
    haveChange: false,
    hasDirtyEditorFiles: false,
    isCreating: false,
    flowErrors: [],
    isReadOnly: false,
    isAllowedEdit: true,
    flow: {namespace: "company.team", id: "my-flow", draft: true},
}))
vi.mock("../../stores/flow", () => ({
    useFlowStore: () => flowStore,
    isSuccessfulFlowSaveOutcome: vi.fn(),
}))
vi.mock("../../stores/executions", () => ({useExecutionsStore: () => ({})}))
vi.mock("../../stores/productTour", () => ({useProductTourStore: () => ({isGuidedActive: false})}))
vi.mock("../../stores/playground", () => ({usePlaygroundStore: () => ({})}))
vi.mock("../../stores/plugins", () => ({usePluginsStore: () => ({})}))
vi.mock("override/stores/misc", () => ({useMiscStore: () => ({configs: {edition: "OSS"}})}))
vi.mock("../../stores/core", () => ({useCoreStore: () => ({})}))

import {useFlowEditorActions} from "./useFlowEditorActions"
import {i18nMount} from "../../../tests/unit/i18nMount"

describe("useFlowEditorActions 401 handling (#21400)", () => {
    it("toasts the localized errors.401 message when publishDraft gets a 401", async () => {
        flowStore.publishDraft.mockRejectedValueOnce({status: 401})

        let actions: ReturnType<typeof useFlowEditorActions> | undefined
        const Probe = defineComponent({
            setup() {
                actions = useFlowEditorActions()
                return () => h("div")
            },
        })
        // en.json wraps its strings in a top-level locale key, so pass the inner map.
        i18nMount(Probe, {messages: en.en as Record<string, unknown>} as never)

        await actions!.publishDraft()
        await flushPromises()

        expect(toastError).toHaveBeenCalledWith(
            "You need to be authenticated to access this page.",
            "Unauthorized",
            {duration: 2000},
        )
    })
})
