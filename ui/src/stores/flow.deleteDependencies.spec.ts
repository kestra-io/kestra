import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {setActivePinia} from "pinia"
import {createMemoryHistory, createRouter} from "vue-router"
import {flushPromises} from "@vue/test-utils"
import {designSystemAsyncComponentsReady, KsMarkdown, KsMessageBox} from "@kestra-io/design-system"
import {h, type VNode} from "vue"
import {i18nMount} from "../../tests/unit/i18nMount"
import {routedPinia} from "../../tests/unit/memoryRouter"
import {useFlowStore} from "./flow"
import {deleteFlow, flowDependencies} from "@kestra-io/kestra-sdk/flows"

vi.mock("@kestra-io/kestra-sdk/flows", () => ({
    flowDependencies: vi.fn(),
    deleteFlow: vi.fn(),
}))

describe("flow deletion dependency links", () => {
    afterEach(() => vi.restoreAllMocks())

    beforeEach(() => {
        setActivePinia(routedPinia())
        vi.mocked(flowDependencies).mockReset()
        vi.mocked(deleteFlow).mockReset().mockResolvedValue(undefined)
        vi.spyOn(KsMessageBox, "confirm").mockResolvedValue("confirm" as Awaited<ReturnType<typeof KsMessageBox.confirm>>)
    })

    async function renderConfirmation(tenant?: string, base = "/ui/") {
        const router = createRouter({
            history: createMemoryHistory(base),
            routes: [{
                name: "flows/update/overview",
                path: "/:tenant?/flows/edit/:namespace/:id/overview",
                component: {render: () => null},
            }],
        })
        await router.push({
            name: "flows/update/overview",
            params: {tenant, namespace: "company.team", id: "child"},
        })
        const store = useFlowStore()
        store.flowYaml = "id: child\nnamespace: company.team\ntasks: []\n"
        await store.deleteFlowAndDependencies(router)

        const message = vi.mocked(KsMessageBox.confirm).mock.lastCall?.[0] as VNode
        const wrapper = i18nMount({render: () => h(KsMarkdown, {content: message.props?.content})})
        await designSystemAsyncComponentsReady()
        await flushPromises()
        return wrapper
    }

    it.each([
        ["acme", "/kestra/ui/", "/kestra/ui/acme/flows/edit/other.team/parent/overview"],
        [undefined, "/ui/", "/ui/flows/edit/other.team/parent/overview"],
    ])("links to the dependent flow with tenant %s and base %s", async (tenant, base, href) => {
        vi.mocked(flowDependencies).mockResolvedValue({nodes: [
            {uid: "child", namespace: "company.team", id: "child"},
            {uid: "parent", namespace: "other.team", id: "parent"},
            {uid: "other-child", namespace: "other.team", id: "child"},
        ]})

        const wrapper = await renderConfirmation(tenant, base)
        const links = wrapper.findAll("a")
        expect(links).toHaveLength(2)
        expect(links[0].text()).toBe("other.team.parent")
        expect(links[0].attributes("href")).toBe(href)
        expect(links[0].attributes("target")).toBe("_blank")
        expect(links[0].attributes("rel")).toContain("noopener")
        expect(links[1].text()).toBe("other.team.child")
        expect(links[1].attributes("href")).toBe(href.replace("/parent/", "/child/"))
        expect(flowDependencies).toHaveBeenCalledWith({namespace: "company.team", id: "child", destinationOnly: true})
        expect(deleteFlow).toHaveBeenCalledWith({namespace: "company.team", id: "child"})
    })

    it("escapes dependency text and encodes route parameters", async () => {
        vi.mocked(flowDependencies).mockResolvedValue({nodes: [
            {uid: "parent", namespace: "other.<i>ns</i>", id: "parent<b>x</b>&amp;\"#?"},
        ]})

        const wrapper = await renderConfirmation()
        const link = wrapper.get("a")
        expect(link.text()).toBe("other.<i>ns</i>.parent<b>x</b>&amp;\"#?")
        expect(link.findAll("i, b")).toHaveLength(0)
        expect(link.findAll("code")).toHaveLength(1)
        expect(link.attributes("href")).toBe("/ui/flows/edit/other.%3Ci%3Ens%3C%2Fi%3E/parent%3Cb%3Ex%3C%2Fb%3E&amp;%22%23%3F/overview")
    })

    it("renders dependency warning inside the alert box", async () => {
        vi.mocked(flowDependencies).mockResolvedValue({nodes: [
            {uid: "child", namespace: "company.team", id: "child"},
            {uid: "parent", namespace: "other.team", id: "parent"},
        ]})

        const wrapper = await renderConfirmation()
        const alertBox = wrapper.find("div[role='alert']")
        expect(alertBox.exists()).toBe(true)
        expect(alertBox.text()).toContain("dependencies delete flow")
        expect(alertBox.findAll("a")).toHaveLength(1)
    })

    it("keeps the ordinary confirmation when there are no other flows", async () => {
        vi.mocked(flowDependencies).mockResolvedValue({nodes: [
            {uid: "child", namespace: "company.team", id: "child"},
        ]})

        const wrapper = await renderConfirmation()
        expect(wrapper.findAll("a")).toHaveLength(0)
        expect(wrapper.text()).toContain("delete confirm")
        expect(wrapper.text()).not.toContain("dependencies delete flow")
    })
})
