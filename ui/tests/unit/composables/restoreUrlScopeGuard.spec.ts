import {afterEach, describe, expect, it} from "vitest"
import {dirname, resolve} from "node:path"
import {fileURLToPath} from "node:url"
import {defineComponent, h, ref} from "vue"
import {mount} from "@vue/test-utils"
import {createMemoryHistory, createRouter, RouteParamsRawGeneric} from "vue-router"
import useRestoreUrl from "../../../src/composables/useRestoreUrl"
import {findRestoreUrlConsumers} from "./restoreUrlScopeGuard"

const SRC = resolve(dirname(fileURLToPath(import.meta.url)), "../../../src")

const CONSUMERS = [
    "components/admin/triggers/TriggersManage.vue",
    "components/dashboard/Dashboard.vue",
    "components/executions/Executions.vue",
    "components/flows/Flows.vue",
    "components/flows/FlowsSearch.vue",
    "components/flows/blueprints/BlueprintsBrowser.vue",
    "components/kv/KVTable.vue",
    "components/logs/LogsWrapper.vue",
    "components/secrets/Secrets.vue",
]

interface Scope {
    label: string;
    name: string;
    path: string;
    a: RouteParamsRawGeneric;
    b: RouteParamsRawGeneric;
    /** The `scope` the page passes for a and b, when its route does not name the entity it shows. */
    scopes?: [string, string];
}

const SCOPES: Scope[] = [
    {
        label: "two flows in one namespace",
        name: "flows/update/executions",
        path: "/:tenant?/flows/edit/:namespace/:id/executions",
        a: {tenant: "main", namespace: "company.team", id: "flowA"},
        b: {tenant: "main", namespace: "company.team", id: "flowB"},
    },
    {
        label: "the same flow id in two namespaces",
        name: "flows/update/executions",
        path: "/:tenant?/flows/edit/:namespace/:id/executions",
        a: {tenant: "main", namespace: "company.alpha", id: "shared"},
        b: {tenant: "main", namespace: "company.beta", id: "shared"},
    },
    {
        label: "two namespaces",
        name: "namespaces/update/executions",
        path: "/:tenant?/namespaces/edit/:id/executions",
        a: {tenant: "main", id: "company.alpha"},
        b: {tenant: "main", id: "company.beta"},
    },
    {
        label: "two blueprint kinds",
        name: "blueprints",
        path: "/:tenant?/blueprints/:kind/:tab",
        a: {tenant: "main", kind: "flow", tab: "community"},
        b: {tenant: "main", kind: "app", tab: "community"},
    },
    {
        label: "two tenants on a global page",
        name: "executions/list",
        path: "/:tenant?/executions",
        a: {tenant: "alpha"},
        b: {tenant: "beta"},
    },
    {
        label: "two dashboards",
        name: "home",
        path: "/:tenant?/dashboards/:dashboard?",
        a: {tenant: "main", dashboard: "dashA"},
        b: {tenant: "main", dashboard: "dashB"},
        scopes: ["dashA", "dashB"],
    },
]

async function keysFor({name, path, a, b, scopes}: Scope): Promise<[string, string]> {
    const router = createRouter({
        history: createMemoryHistory(),
        routes: [{name, path, component: {template: "<div/>"}}],
    })

    const read = async (params: RouteParamsRawGeneric, scope?: string) => {
        await router.push({name, params})
        const wrapper = mount(
            defineComponent({setup: () => useRestoreUrl({restoreUrl: false, scope: scope ? ref(scope) : undefined}), render: () => h("div")}),
            {global: {plugins: [router]}},
        )
        const key = wrapper.vm.localStorageName as unknown as string
        wrapper.unmount()
        return key
    }

    return [await read(a, scopes?.[0]), await read(b, scopes?.[1])]
}

describe("useRestoreUrl scoping", () => {
    afterEach(() => {
        window.sessionStorage.clear()
    })

    it("has a declared scope for every page that saves its URL query", () => {
        const found = findRestoreUrlConsumers(SRC)
        const undeclared = found.filter((file) => !CONSUMERS.includes(file))
        const stale = CONSUMERS.filter((file) => !found.includes(file))

        expect(
            {undeclared, stale},
            "A page calling useRestoreUrl saves its filters under a key derived from the route.\n"
            + "Add it to CONSUMERS. If it renders one entity (a flow, a namespace, a blueprint\n"
            + "kind), add a SCOPES case proving two different entities get different keys.\n"
            + "Remove stale entries when a page stops using the composable.",
        ).toEqual({undeclared: [], stale: []})
    })

    it.each(SCOPES)("gives $label their own saved state", async (scope) => {
        const [a, b] = await keysFor(scope)

        expect(a, `${scope.label} share the key ${a} — one's filters restore on the other`).not.toBe(b)
    })
})
