import * as KvAPI from "@kestra-io/kestra-sdk/kv"
import * as NamespaceAPI from "@kestra-io/kestra-sdk/namespaces"
import * as FilesAPI from "@kestra-io/kestra-sdk/files"
import type {ContextSectionProvider} from "./types"
import {namespaceFileChips} from "./namespaceFileExpression"
import {escapePebbleLiteral} from "./pebbleExpression"

function namespaceParams(namespace: string, tenant?: string) {
    return tenant ? {namespace, tenant} : {namespace}
}

function tenantParams(tenant?: string) {
    return tenant ? {tenant} : {}
}

// The backend's page-size cap (PageableUtils.MAX_PAGE_SIZE) — without it listAllKeys defaults to 10.
// Reads page 1 only: a namespace with more than this many of its own keys silently drops the rest,
// while the inherited keys below are unbounded — an asymmetry worth revisiting if it comes up.
const KV_LIST_PAGE_SIZE = 1000

export const kvContextSectionProvider: ContextSectionProvider = async ({namespace, tenant, t}) => {
    // listKeysWithInheritence deliberately excludes the namespace's own keys (it lists what's
    // inherited from ancestors only), so its own keys come from a separate, namespace-filtered call.
    const [own, inherited] = await Promise.all([
        KvAPI.listAllKeys({
            ...tenantParams(tenant),
            size: KV_LIST_PAGE_SIZE,
            filters: [{field: "namespace", operation: "EQUALS", value: namespace}],
        }),
        KvAPI.listKeysWithInheritence(namespaceParams(namespace, tenant)),
    ])
    const keys = [...new Set([
        ...(own?.results ?? []).map(entry => entry.key),
        ...(inherited ?? []).map(entry => entry.key),
    ].filter((key): key is string => Boolean(key)))]
    if (!keys.length) return null

    return {
        key: "namespaceKv",
        label: t("block_editor.namespace_kv"),
        isNew: true,
        chips: keys.map(key => ({label: key, expr: `{{ kv('${escapePebbleLiteral(key)}') }}`})),
    }
}

export const secretsContextSectionProvider: ContextSectionProvider = async ({namespace, tenant, t}) => {
    const inherited = await NamespaceAPI.inheritedSecrets(namespaceParams(namespace, tenant))
    const names = [...new Set(Object.values(inherited ?? {}).flat())]
    if (!names.length) return null

    return {
        key: "namespaceSecrets",
        label: t("block_editor.namespace_secrets"),
        isNew: true,
        chips: names.map(name => ({label: name, expr: `{{ secret('${escapePebbleLiteral(name)}') }}`})),
    }
}

export const namespaceFilesContextSectionProvider: ContextSectionProvider = async ({namespace, tenant, t}) => {
    // An empty q matches nothing server-side; "*" is the namespace-files search's own match-all
    // query. Unbounded and unvirtualized: a namespace with very many files renders every one of them.
    const paths = await FilesAPI.searchNamespaceFiles({...namespaceParams(namespace, tenant), q: "*"})
    if (!paths?.length) return null

    return {
        key: "namespaceFiles",
        label: t("block_editor.namespace_files"),
        isNew: true,
        chips: paths.flatMap(namespaceFileChips),
    }
}

export const OSS_CONTEXT_SECTION_PROVIDERS: ContextSectionProvider[] = [
    kvContextSectionProvider,
    secretsContextSectionProvider,
    namespaceFilesContextSectionProvider,
]
