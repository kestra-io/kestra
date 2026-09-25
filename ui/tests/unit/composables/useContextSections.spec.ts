import {beforeEach, describe, expect, it, vi} from "vitest"
import {defineComponent, h, ref} from "vue"
import {useI18n} from "vue-i18n"
import {i18nMount} from "../i18nMount"

const {kvProvider, extraProvider} = vi.hoisted(() => ({
    kvProvider: vi.fn(async () => ({key: "namespaceKv", labelKey: "block_editor.namespace_kv", chips: [{label: "KEY", expr: "{{ kv('KEY') }}"}]})),
    extraProvider: vi.fn(async () => ({key: "credentials", labelKey: "block_editor.namespace_credentials", chips: [{label: "aws_prod", expr: "{{ credential('aws_prod') }}"}]})),
}))

vi.mock("../../../src/components/flows/contextSections/providers", () => ({
    OSS_CONTEXT_SECTION_PROVIDERS: [kvProvider],
}))

vi.mock("override/components/flows/contextSectionsExtension", () => ({
    useContextSectionsExtension: () => [extraProvider],
}))

import {useContextSections} from "../../../src/composables/useContextSections"
import {resetContextSectionsCache} from "../../../src/components/flows/contextSections/fetchContextSections"

beforeEach(() => {
    kvProvider.mockClear()
    extraProvider.mockClear()
    resetContextSectionsCache()
})

function setup(namespace = ref<string | undefined>("team.a")) {
    let api!: ReturnType<typeof useContextSections>
    const Comp = defineComponent({setup() {
        api = useContextSections(namespace)
        return () => h("div")
    }})
    i18nMount(Comp)
    return api
}

describe("useContextSections", () => {
    it("merges OSS-native sections with the (e.g. EE) extension's", async () => {
        const {sections} = setup()
        await vi.waitFor(() => expect(sections.value).toHaveLength(2))

        expect(sections.value.map((section) => section.key)).toEqual(["namespaceKv", "credentials"])
        expect(sections.value.map((section) => section.label)).toEqual(["block_editor.namespace_kv", "block_editor.namespace_credentials"])
    })

    it("clears the sections when the namespace becomes undefined", async () => {
        const namespace = ref<string | undefined>("team.a")
        const {sections} = setup(namespace)
        await vi.waitFor(() => expect(sections.value).toHaveLength(2))

        namespace.value = undefined
        await vi.waitFor(() => expect(sections.value).toHaveLength(0))
    })

    it("relabels the cached sections when the UI locale changes, instead of stranding them in the old language", async () => {
        let api!: ReturnType<typeof useContextSections>
        let locale!: ReturnType<typeof useI18n<Record<string, unknown>, string>>["locale"]
        const namespace = ref<string | undefined>("team.a")
        const Comp = defineComponent({setup() {
            api = useContextSections(namespace)
            locale = useI18n<Record<string, unknown>, string>().locale
            return () => h("div")
        }})
        i18nMount(Comp, {locales: {
            en: {block_editor: {namespace_kv: "KV keys", namespace_credentials: "Credentials"}},
            fr: {block_editor: {namespace_kv: "Clés KV", namespace_credentials: "Identifiants"}},
        }})
        await vi.waitFor(() => expect(api.sections.value).toHaveLength(2))
        expect(api.sections.value[0].label).toBe("KV keys")

        locale.value = "fr"
        await vi.waitFor(() => expect(api.sections.value[0].label).toBe("Clés KV"))
        expect(kvProvider).toHaveBeenCalledTimes(1)
    })
})
