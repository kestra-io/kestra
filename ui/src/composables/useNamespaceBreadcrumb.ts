import {computed, toValue, type MaybeRefOrGetter} from "vue"
import {useI18n} from "vue-i18n"
import type {KsBreadcrumbItem, KsBreadcrumbLoader} from "@kestra-io/design-system"
import type {PagedResultsFlow} from "@kestra-io/kestra-sdk"
import FileTreeOutline from "vue-material-design-icons/FileTreeOutline.vue"
import FolderOutline from "vue-material-design-icons/FolderOutline.vue"
import Plus from "vue-material-design-icons/Plus.vue"
import useNamespaces from "./useNamespaces"
import {useSystemNamespace} from "./useSystemNamespace"
import {useAuthStore} from "override/stores/auth"
import {useMiscStore} from "override/stores/misc"
import {useFlowStore} from "../stores/flow"
import {apiUrl} from "override/utils/route"
import resource from "../models/resource"
import action from "../models/action"
import {NAMESPACE_PARENT_ROUTE} from "../utils/namespaceTabRoutes"

interface Options {
    /** The namespace tab a level links to. */
    tab?: string;
    /** The first crumb when the page belongs to another section; the namespaces list otherwise. */
    root?: MaybeRefOrGetter<KsBreadcrumbItem | undefined>;
}

const namespaceIdsByTenant = new Map<string, Promise<string[]>>()

// Kept for the session, and fetched again once it lacks the namespace being viewed, which is how one created since shows up.
async function namespaceIds(current: string | undefined): Promise<string[]> {
    if (!useAuthStore().user?.hasAnyActionOnAnyNamespace(resource.NAMESPACE, action.LIST)) return []

    const tenant = apiUrl()
    const cached = namespaceIdsByTenant.get(tenant)
    if (cached && (!current || (await cached).includes(current))) return cached

    const fetched = useNamespaces(1000).all().then((namespaces) => namespaces.map((entry) => entry.id))
    namespaceIdsByTenant.set(tenant, fetched)
    fetched.catch(() => namespaceIdsByTenant.delete(tenant))
    return fetched
}

const flowsByNamespace = new Map<string, Promise<PagedResultsFlow>>()

const canReadFlows = (namespace: string) => Boolean(useAuthStore().user?.isAllowed(resource.FLOW, action.VIEW, namespace))

/** One page per namespace however many flows it holds, kept ten seconds so moving around inside an open menu asks once. */
async function flowEntries(namespace: string, t: ReturnType<typeof useI18n>["t"], current?: string): Promise<KsBreadcrumbItem[]> {
    if (!canReadFlows(namespace)) return []

    const key = `${apiUrl()}|${namespace}`
    if (!flowsByNamespace.has(key)) {
        const search = useFlowStore().findFlows({"filters[namespace][EQUALS]": namespace, size: 20, sort: "id:asc", commit: false})
        flowsByNamespace.set(key, search)
        search.catch(() => flowsByNamespace.delete(key))
        setTimeout(() => flowsByNamespace.delete(key), 10_000)
    }

    const page = await flowsByNamespace.get(key)?.catch(() => undefined)
    const ids = page?.results.map((flow) => flow.id) ?? []
    return [
        ...ids.map((id, index) => ({
            label: id,
            divided: index === 0,
            link: {name: "flows/update", params: {namespace, id}},
            icon: FileTreeOutline,
            current: id === current,
        })),
        ...((page?.total ?? 0) > ids.length ? [{
            label: t("view_all_flows", {count: page?.total}),
            link: {name: `${NAMESPACE_PARENT_ROUTE}/flows`, params: {id: namespace}},
        }] : []),
    ]
}

/** The menu next to a flow's name: the flows of its namespace, fetched when the menu opens and never while the page loads. */
export function useFlowSiblings(namespace: MaybeRefOrGetter<string | undefined>, flowId: MaybeRefOrGetter<string | undefined>) {
    const {t} = useI18n({useScope: "global"})

    const currentNamespace = computed(() => toValue(namespace))
    const currentFlow = computed(() => toValue(flowId))

    return computed<KsBreadcrumbLoader | undefined>(() => {
        const where = currentNamespace.value
        const current = currentFlow.value
        return where && canReadFlows(where) ? Object.assign(() => flowEntries(where, t, current), {lazy: true}) : undefined
    })
}

/**
 * The folder path from `root` down to `namespace`, one breadcrumb item per level. A level's menu lists the
 * namespaces under its parent and every entry with children flies them out the same way, all of it served
 * by one fetch of the namespace tree per tenant. Where the user may create a namespace, the fly-out also offers
 * a link creating a child of the entry. A namespace's flows come last and only in its fly-out, which loads
 * when it opens, so the bar itself never asks for them.
 */
export function useNamespaceBreadcrumb(namespace: MaybeRefOrGetter<string | undefined>, {tab = "overview", root}: Options = {}) {
    const {t} = useI18n({useScope: "global"})
    const systemNamespace = useSystemNamespace()

    const link = (id: string) => ({name: `${NAMESPACE_PARENT_ROUTE}/${tab}`, params: {id}})

    // Settled to a string first, so a navigation that keeps the namespace (a tab switch) neither rebuilds
    // the items nor fetches the tree again.
    const currentNamespace = computed(() => toValue(namespace))

    return computed<KsBreadcrumbItem[]>(() => {
        const current = currentNamespace.value
        const parts = current?.split(".") ?? []

        let tree: Promise<string[]> | undefined
        const treeIds = () => (tree ??= namespaceIds(current))

        const childrenOf = (ids: string[], parent: string) => {
            const prefix = parent ? `${parent}.` : ""
            return ids.filter((id) => id.startsWith(prefix) && !id.slice(prefix.length).includes("."))
        }

        const canCreateUnder = (id: string) => id !== systemNamespace.value && useMiscStore().configs?.edition !== "OSS"
            && Boolean(useAuthStore().user?.isAllowed(resource.NAMESPACE, action.CREATE, id))

        const drillable = (ids: string[], id: string) => childrenOf(ids, id).length > 0 || canCreateUnder(id)

        const entriesUnder = async (parent: string): Promise<KsBreadcrumbItem[]> => {
            const ids = await treeIds()
            return childrenOf(ids, parent).map((id) => ({
                label: id.slice(id.lastIndexOf(".") + 1),
                link: link(id),
                icon: FolderOutline,
                tooltip: id,
                current: id === current,
                children: drillable(ids, id) || canReadFlows(id) ? () => contentOf(id) : undefined,
            }))
        }

        const contentOf = async (id: string): Promise<KsBreadcrumbItem[]> => [
            ...await entriesUnder(id),
            ...(canCreateUnder(id) ? [{
                label: t("create_child_namespace"),
                link: {name: "namespaces/create", params: {tab: "edit"}, query: {id: `${id}.`}},
                icon: Plus,
            }] : []),
            ...await flowEntries(id, t),
        ]

        // A menu that would only repeat the page it is on, with nothing to drill into, is not offered.
        const beside = async (id: string): Promise<KsBreadcrumbItem[]> => {
            const entries = await entriesUnder(id.substring(0, id.lastIndexOf(".")))
            return entries.length === 1 && entries[0].current && !drillable(await treeIds(), id) ? [] : entries
        }

        return [
            toValue(root) ?? {label: t("namespaces"), link: {name: "namespaces/list"}},
            ...parts.map((label, index) => {
                const id = parts.slice(0, index + 1).join(".")
                return {
                    label,
                    link: link(id),
                    icon: FolderOutline,
                    tooltip: id,
                    siblings: () => beside(id),
                    children: () => contentOf(id),
                }
            }),
        ]
    })
}
