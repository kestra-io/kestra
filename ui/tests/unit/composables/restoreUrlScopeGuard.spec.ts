import {afterEach, describe, expect, it} from "vitest"
import {dirname, resolve} from "node:path"
import {fileURLToPath} from "node:url"
import {defineComponent, h} from "vue"
import {mount} from "@vue/test-utils"
import {createMemoryHistory, createRouter, RouteParamsRawGeneric} from "vue-router"
import useRestoreUrl from "../../../src/composables/useRestoreUrl"
import {findRestoreUrlConsumers} from "./restoreUrlScopeGuard"

const SRC = resolve(dirname(fileURLToPath(import.meta.url)), "../../../src")

// Every page that saves its URL query. A new entry here is a new chance to leak one entity's
// filters onto another (kestra-io/kestra#20135), so adding one means adding the matching
// SCOPES case below — or, when the page is genuinely global, saying so in GLOBAL.
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
]

// Shrink-only. `dashboard` is appended to the URL after mount, so keying on it would change the
// key mid-restore and break the re-assert loop in goToRestoreUrl() — see the issue for the two
// approaches that would actually fix it.
const KNOWN_UNSCOPED: Scope[] = [
    {
        label: "two dashboards (https://github.com/kestra-io/kestra/issues/20142)",
        name: "home",
        path: "/:tenant?/dashboards/:dashboard?",
        a: {tenant: "main", dashboard: "dashA"},
        b: {tenant: "main", dashboard: "dashB"},
    },
]

async function keysFor({name, path, a, b}: Scope): Promise<[string, string]> {
    const router = createRouter({
        history: createMemoryHistory(),
        routes: [{name, path, component: {template: "<div/>"}}],
    })

    const read = async (params: RouteParamsRawGeneric) => {
        await router.push({name, params})
        const wrapper = mount(
            defineComponent({setup: () => useRestoreUrl({restoreUrl: false}), render: () => h("div")}),
            {global: {plugins: [router]}},
        )
        const key = wrapper.vm.localStorageName as unknown as string
        wrapper.unmount()
        return key
    }

    return [await read(a), await read(b)]
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

    it.each(KNOWN_UNSCOPED)("still shares one key for $label", async (scope) => {
        const [a, b] = await keysFor(scope)

        expect(
            a,
            `${scope.label} no longer share a key — move this case from KNOWN_UNSCOPED to SCOPES.`,
        ).toBe(b)
    })
})
