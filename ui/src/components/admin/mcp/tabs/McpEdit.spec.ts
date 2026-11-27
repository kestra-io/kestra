import {describe, it, expect, vi} from "vitest"
import {computed} from "vue"
import {createPinia} from "pinia"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../../../tests/unit/i18nMount"
import {useUnsavedChangesStore} from "../../../../stores/unsavedChanges"

const {push, create} = vi.hoisted(() => ({push: vi.fn(), create: vi.fn()}))

vi.mock("vue-router", () => ({
    useRoute: () => ({params: {tenant: "main"}, query: {}}),
    useRouter: () => ({push}),
}))
vi.mock("../../../../stores/mcp", () => ({useMcpStore: () => ({server: undefined, create, update: vi.fn()})}))
vi.mock("override/stores/misc", () => ({useMiscStore: () => ({configs: {edition: "OSS"}})}))
vi.mock("override/stores/auth", () => ({useAuthStore: () => ({auths: {oauths: []}, user: undefined, loadAuths: vi.fn()})}))
vi.mock("../useHelpers", () => ({useHelpers: () => ({listRoute: computed(() => ({name: "admin/mcp-servers/list"}))})}))
vi.mock("../../../../utils/toast", () => ({useToast: () => ({saved: vi.fn()})}))

import McpEdit from "./McpEdit.vue"

describe("McpEdit", () => {
    it("asks before leaving once a new server is edited, and not after it is created", async () => {
        create.mockResolvedValue({id: "docs"})
        const pinia = createPinia()
        const wrapper = i18nMount(McpEdit, {global: {plugins: [pinia]}})
        const unsavedChanges = useUnsavedChangesStore(pinia)
        await flushPromises()
        expect(unsavedChanges.unsavedChange).toBe(false)

        await wrapper.find(".id-input input").setValue("docs")
        expect(unsavedChanges.unsavedChange).toBe(true)

        await wrapper.find("form").trigger("submit")
        await flushPromises()
        expect(create).toHaveBeenCalled()
        expect(unsavedChanges.unsavedChange).toBe(false)
    })
})
