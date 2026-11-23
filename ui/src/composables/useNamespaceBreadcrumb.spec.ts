import {beforeEach, describe, expect, it, vi} from "vitest"
import {ref} from "vue"
import type {KsBreadcrumbLoader} from "@kestra-io/design-system"

const state = vi.hoisted(() => ({tenant: "", canList: true, ids: [] as string[], edition: "EE", creatable: [] as string[]}))
const all = vi.hoisted(() => vi.fn())

vi.mock("vue-i18n", () => ({
    useI18n: () => ({t: (key: string) => key}),
}))

vi.mock("override/utils/route", () => ({
    apiUrl: () => state.tenant,
}))

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {
        hasAnyActionOnAnyNamespace: () => state.canList,
        isAllowed: (permission: string, act: string, namespace: string) => permission === "NAMESPACE" && act === "CREATE"
            && state.creatable.some((prefix) => namespace === prefix || namespace.startsWith(`${prefix}.`)),
    }}),
}))

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: {edition: state.edition}}),
}))

vi.mock("./useNamespaces", () => ({
    default: () => ({all}),
}))

import {useNamespaceBreadcrumb} from "./useNamespaceBreadcrumb"

const TREE = ["acme", "acme.data", "acme.data.raw", "acme.ops", "lone", "lone.leaf", "solo", "solo.only", "solo.only.deep", "system"]

const labels = async (loader?: KsBreadcrumbLoader) => (await loader?.())?.map((entry) => entry.label)

let tenants = 0

describe("useNamespaceBreadcrumb", () => {
    beforeEach(() => {
        // The tree is cached per tenant for the session, so every test starts on a tenant of its own.
        state.tenant = `tenant-${++tenants}`
        state.canList = true
        state.ids = [...TREE]
        state.edition = "EE"
        state.creatable = []
        all.mockReset()
        all.mockImplementation(async () => state.ids.map((id) => ({id})))
    })

    it("should hide a level's menu when its only entry is the page itself", async () => {
        const leaf = useNamespaceBreadcrumb("lone.leaf").value
        const withChildren = useNamespaceBreadcrumb("solo.only").value

        expect(await labels(leaf[2].siblings)).toEqual([])
        expect(await labels(withChildren[2].siblings)).toEqual(["only"])
    })

    it("should list only direct children, flying grandchildren out of the entry that holds them", async () => {
        const [, acme] = useNamespaceBreadcrumb("acme").value

        const children = (await acme.children?.()) ?? []

        expect(children.map((entry) => entry.label)).toEqual(["data", "ops"])
        expect(await labels(children[0].children)).toEqual(["raw"])
        expect(children[1].children).toBeUndefined()
    })

    it("should end the fly-out of a namespace the user may create in with a link creating a child of it", async () => {
        state.creatable = ["acme"]
        const [, acme] = useNamespaceBreadcrumb("acme.data").value

        const children = (await acme.children?.()) ?? []
        const topLevel = (await acme.siblings?.()) ?? []

        expect(children.map((entry) => entry.label)).toEqual(["data", "ops", "create_child_namespace"])
        expect(children[2].link).toEqual({name: "namespaces/create", params: {tab: "edit"}, query: {id: "acme."}})
        expect(await labels(children[1].children)).toEqual(["create_child_namespace"])
        expect(topLevel.map((entry) => entry.label)).toEqual(["acme", "lone", "solo", "system"])
        expect(await labels(topLevel[1].children)).toEqual(["leaf"])
    })

    it("should keep a leaf page's menu when creating a child of it is offered", async () => {
        state.creatable = ["lone"]
        const [, , leaf] = useNamespaceBreadcrumb("lone.leaf").value

        const siblings = (await leaf.siblings?.()) ?? []

        expect(siblings.map((entry) => entry.label)).toEqual(["leaf"])
        expect(await labels(siblings[0].children)).toEqual(["create_child_namespace"])
    })

    it("should offer no child creation under the system namespace", async () => {
        state.creatable = ["system"]
        const [, system] = useNamespaceBreadcrumb("system").value

        const topLevel = (await system.siblings?.()) ?? []

        expect(await labels(system.children)).toEqual([])
        expect(topLevel.find((entry) => entry.label === "system")?.children).toBeUndefined()
    })

    it("should offer no namespace creation in the open source edition, which has none", async () => {
        state.edition = "OSS"
        state.creatable = ["acme"]
        const [, acme] = useNamespaceBreadcrumb("acme.data").value

        const children = (await acme.children?.()) ?? []

        expect(children.map((entry) => entry.label)).toEqual(["data", "ops"])
        expect(children[1].children).toBeUndefined()
    })

    it("should fetch the tree once per tenant, across every level and namespace", async () => {
        const namespace = ref("acme.data")
        const items = useNamespaceBreadcrumb(namespace)

        await Promise.all(items.value.slice(1).flatMap((item) => [item.siblings?.(), item.children?.()]))
        namespace.value = "acme.ops"
        await items.value[2].siblings?.()
        expect(all).toHaveBeenCalledTimes(1)

        state.tenant = "another-tenant"
        namespace.value = "acme.data"
        await items.value[2].siblings?.()
        expect(all).toHaveBeenCalledTimes(2)
    })

    it("should fetch the tree again when it lacks the namespace being viewed", async () => {
        const namespace = ref("acme.data")
        const items = useNamespaceBreadcrumb(namespace)
        await items.value[2].siblings?.()

        state.ids = [...state.ids, "acme.new"]
        namespace.value = "acme.new"

        expect(await labels(items.value[2].siblings)).toEqual(["data", "ops", "new"])
        expect(all).toHaveBeenCalledTimes(2)
    })

    it("should fetch the tree again after a failed attempt", async () => {
        all.mockRejectedValueOnce(new Error("Namespace search failed."))
        const namespace = ref("acme.data")
        const items = useNamespaceBreadcrumb(namespace)
        await expect(items.value[2].siblings?.()).rejects.toThrow()

        namespace.value = "acme.ops"

        expect(await labels(items.value[2].siblings)).toEqual(["data", "ops"])
    })

    it("should offer no menu and fetch nothing without the namespace LIST permission", async () => {
        state.canList = false
        const [, acme, data] = useNamespaceBreadcrumb("acme.data").value

        expect(await labels(acme.siblings)).toEqual([])
        expect(await labels(data.children)).toEqual([])
        expect(all).not.toHaveBeenCalled()
    })
})
