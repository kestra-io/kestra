import {computed, toValue, type MaybeRefOrGetter} from "vue"
import {useI18n} from "vue-i18n"
import type {KsBreadcrumbItem} from "@kestra-io/design-system"
import FolderOutline from "vue-material-design-icons/FolderOutline.vue"
import useNamespaces from "./useNamespaces"
import {useAuthStore} from "override/stores/auth"
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

/**
 * The folder path from `root` down to `namespace`, one breadcrumb item per level. A level's menu lists the
 * namespaces under its parent and every entry with children flies them out the same way, all of it served
 * by one fetch of the namespace tree per tenant. Flows are deliberately not listed: they would cost one
 * request per level for a rarely used shortcut.
 */
export function useNamespaceBreadcrumb(namespace: MaybeRefOrGetter<string | undefined>, {tab = "overview", root}: Options = {}) {
    const {t} = useI18n({useScope: "global"})

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

        const entriesUnder = async (parent: string): Promise<KsBreadcrumbItem[]> => {
            const ids = await treeIds()
            return childrenOf(ids, parent).map((id) => ({
                label: id.slice(id.lastIndexOf(".") + 1),
                link: link(id),
                icon: FolderOutline,
                tooltip: id,
                current: id === current,
                children: childrenOf(ids, id).length ? () => entriesUnder(id) : undefined,
            }))
        }

        // A menu that would only repeat the page it is on, with nothing to drill into, is not offered.
        const beside = async (id: string): Promise<KsBreadcrumbItem[]> => {
            const entries = await entriesUnder(id.substring(0, id.lastIndexOf(".")))
            return entries.length === 1 && entries[0].current && !entries[0].children ? [] : entries
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
                    children: () => entriesUnder(id),
                }
            }),
        ]
    })
}
