import {describe, it, expect, vi, beforeEach} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../../tests/unit/i18nMount"

const {getBlueprint, getBlueprintGraph, getGraphFromSourceResponse, canCreate, routeParams} = vi.hoisted(() => ({
    getBlueprint: vi.fn(),
    getBlueprintGraph: vi.fn().mockResolvedValue({}),
    getGraphFromSourceResponse: vi.fn().mockResolvedValue({}),
    canCreate: vi.fn(() => true),
    routeParams: {tab: "custom", tenant: "main"} as Record<string, string>,
}))

vi.mock("vue-router", () => ({
    useRoute: () => ({params: routeParams}),
    useRouter: () => ({push: vi.fn()}),
}))
vi.mock("../../../stores/api", () => ({useApiStore: () => ({posthogEvents: vi.fn()})}))
vi.mock("../../../stores/blueprints", () => ({
    useBlueprintsStore: () => ({getBlueprint, getBlueprintGraph, getBlueprintTags: vi.fn().mockResolvedValue([])}),
}))
vi.mock("../../../stores/flow", () => ({useFlowStore: () => ({getGraphFromSourceResponse})}))
vi.mock("../../../stores/plugins", () => ({usePluginsStore: () => ({fetchIcons: vi.fn(), icons: {}, loadIcon: vi.fn()})}))
vi.mock("../../../composables/useRouteContext", () => ({default: vi.fn()}))
vi.mock("override/composables/blueprintsPermissions", () => ({canCreate}))
vi.mock("../../layout/TopNavBar.vue", () => ({
    default: {name: "TopNavBar", props: ["title"], template: "<header data-test=\"top-nav\">{{ title }}</header>"},
}))
vi.mock("./BlueprintDetailView.vue", () => ({
    default: {
        name: "BlueprintDetailView",
        template: "<div data-test=\"detail-view\"><slot name=\"actions\" :hasMissingPlugins=\"false\" :missingTasks=\"[]\" /></div>",
    },
}))
vi.mock("./BlueprintEmbedView.vue", () => ({
    default: {name: "BlueprintEmbedView", template: "<div data-test=\"embed-view\" />"},
}))

import BlueprintDetailPage from "./BlueprintDetailPage.vue"

const OPEN_IN_EDITOR = "blueprints.detail.openInEditor"

async function mountPage(props: Record<string, unknown> = {}, slots: Record<string, string> = {}) {
    const wrapper = i18nMount(BlueprintDetailPage, {
        props: {blueprintId: "bp-1", ...props},
        slots,
        global: {stubs: {RouterLink: {template: "<a><slot /></a>"}}},
    })
    await flushPromises()
    return wrapper
}

beforeEach(() => {
    vi.clearAllMocks()
    routeParams.tab = "custom"
    canCreate.mockReturnValue(true)
    getBlueprint.mockResolvedValue({id: "bp-1", title: "Sync to S3", source: "id: sync"})
})

describe("BlueprintDetailPage", () => {
    it("renders the full page with its nav bar, and the embed view when embedded", async () => {
        const full = await mountPage()

        expect(full.find("[data-test=top-nav]").text()).toBe("Sync to S3")
        expect(full.find("[data-test=detail-view]").exists()).toBe(true)
        expect(full.find("[data-test=embed-view]").exists()).toBe(false)

        const embedded = await mountPage({embed: true})

        expect(embedded.find("[data-test=embed-view]").exists()).toBe(true)
        expect(embedded.find("[data-test=top-nav]").exists()).toBe(false)
    })

    it("keeps the full view, without the nav bar, for a system blueprint shown embedded", async () => {
        const wrapper = await mountPage({embed: true, system: true})

        expect(wrapper.find("[data-test=detail-view]").exists()).toBe(true)
        expect(wrapper.find("[data-test=top-nav]").exists()).toBe(false)
    })

    it("offers the open-in-editor action by default, only to a user who can create", async () => {
        expect((await mountPage()).find("[data-test=detail-view]").text()).toContain(OPEN_IN_EDITOR)

        canCreate.mockReturnValue(false)

        expect((await mountPage()).find("[data-test=detail-view]").text()).not.toContain(OPEN_IN_EDITOR)
    })

    it("lets the caller replace the default action through the actions slot", async () => {
        const wrapper = await mountPage({}, {actions: "<button data-test=\"custom-action\">use</button>"})

        expect(wrapper.find("[data-test=custom-action]").exists()).toBe(true)
        expect(wrapper.text()).not.toContain(OPEN_IN_EDITOR)
    })

    it("loads a community blueprint's graph from the blueprints API", async () => {
        routeParams.tab = "community"

        await mountPage()

        expect(getBlueprintGraph).toHaveBeenCalledWith({type: "community", kind: "flow", id: "bp-1"})
        expect(getGraphFromSourceResponse).not.toHaveBeenCalled()
    })

    it("computes a custom blueprint's graph from its source", async () => {
        await mountPage()

        expect(getGraphFromSourceResponse).toHaveBeenCalledWith({flow: "id: sync"})
        expect(getBlueprintGraph).not.toHaveBeenCalled()
    })

    it("loads no graph for a template", async () => {
        getBlueprint.mockResolvedValue({id: "bp-1", title: "Sync to S3", source: "id: {{ name }}", template: {}})

        await mountPage()

        expect(getGraphFromSourceResponse).not.toHaveBeenCalled()
        expect(getBlueprintGraph).not.toHaveBeenCalled()
    })
})
