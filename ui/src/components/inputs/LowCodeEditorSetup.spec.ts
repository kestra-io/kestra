import {afterEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {ref} from "vue"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}}),
    useRouter: () => ({push: vi.fn(), replace: vi.fn(), beforeEach: vi.fn(), afterEach: vi.fn()}),
}))

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn()}),
}))

vi.mock("../../remoteComponents/useFederatedModule", () => ({
    useFederatedModule: () => ({
        RemoteComponent: {render: () => null},
        taskAdditionalInfoRemote: ref({}),
        manifestReady: ref(true),
        resolveRemoteComponent: vi.fn(),
        resolveRemoteComponent2: vi.fn(),
        componentTypeFor: vi.fn(),
    }),
}))

const {i18nMount} = await import("../../../tests/unit/i18nMount")
const {usePluginsStore} = await import("../../stores/plugins")
const {default: LowCodeEditor} = await import("./LowCodeEditor.vue")

describe("LowCodeEditor setup", () => {
    afterEach(() => localStorage.clear())

    it("shouldSetUpInReadOnlyExecutionModeWithoutReadingUndeclaredState", () => {
        setActivePinia(createPinia())
        usePluginsStore().fetchIcons = vi.fn()

        const wrapper = i18nMount(LowCodeEditor, {
            props: {
                flowGraph: {nodes: [], clusters: [], edges: []},
                flowId: "flow",
                namespace: "ns",
                execution: {id: "execution-id"},
                isReadOnly: true,
            },
            global: {stubs: {Topology: true}},
        })

        expect(wrapper.exists()).toBe(true)
    })
})
