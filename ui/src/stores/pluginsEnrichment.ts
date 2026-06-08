import {defineStore} from "pinia"
import {ref} from "vue"
import axios from "axios"
import {API_URL} from "./api"
import type {Plugin} from "../utils/pluginUtils"

// https://api.kestra.io/v1/plugins/pluginsInformation
type PluginsInformationEntry = {
    blueprints?: number;
    lastReleasedAt?: string;
    usageCount?: number;
}

// https://api.kestra.io/v1/plugins/metadata
type MetadataEntry = {
    group: string;
    createdBy?: string;
    managedBy?: string;
    description?: string;
    body?: string;
}

export type PluginEnrichment = {
    blueprintCount?: number;
    lastReleasedAt?: string;
    usageCount?: number;
    createdBy?: string;
    managedBy?: string;
    description?: string;
    body?: string;
}

// https://api.kestra.io/v1/plugins/{cls}/versions
export type PluginVersion = {
    version: string;
    publishedAt?: string | null;
    minKestraCompatibilityVersion?: string | null;
    releaseNotesUrl?: string | null;
}

const PUBLIC_API_TIMEOUT_MS = 5000

export const usePluginsEnrichmentStore = defineStore("pluginsEnrichment", () => {
    const enrichments = ref<Record<string, PluginEnrichment>>({})
    const loaded = ref(false)
    const failed = ref(false)
    let pending: Promise<void> | null = null

    const versionsByCls = ref<Record<string, PluginVersion[]>>({})
    const versionsPending: Record<string, Promise<PluginVersion[]>> = {}

    function upsert(key: string, patch: Partial<PluginEnrichment>) {
        enrichments.value[key] = {...(enrichments.value[key] ?? {}), ...patch}
    }

    async function fetchEnrichment(): Promise<void> {
        if (loaded.value || failed.value) return
        if (pending) return pending

        const controller = new AbortController()
        const timeout = setTimeout(() => controller.abort(), PUBLIC_API_TIMEOUT_MS)

        const informationPromise = axios.get<{byPlugin: Record<string, PluginsInformationEntry>}>(
            `${API_URL}/v1/plugins/pluginsInformation?icons=false`,
            {signal: controller.signal},
        )
        const metadataPromise = axios.get<MetadataEntry[]>(
            `${API_URL}/v1/plugins/metadata`,
            {signal: controller.signal},
        )

        pending = Promise.allSettled([informationPromise, metadataPromise]).then(([info, meta]) => {
            if (info.status === "fulfilled") {
                for (const [key, entry] of Object.entries(info.value.data?.byPlugin ?? {})) {
                    upsert(key, {
                        blueprintCount: entry.blueprints,
                        lastReleasedAt: entry.lastReleasedAt,
                        usageCount: entry.usageCount,
                    })
                }
            }
            if (meta.status === "fulfilled") {
                for (const entry of meta.value.data ?? []) {
                    if (!entry.group) continue
                    upsert(entry.group, {
                        createdBy: entry.createdBy,
                        managedBy: entry.managedBy,
                        description: entry.description,
                        body: entry.body,
                    })
                }
            }
            if (info.status === "fulfilled" || meta.status === "fulfilled") {
                loaded.value = true
            } else {
                failed.value = true
                console.warn("Plugin enrichment unavailable", {info, meta})
            }
        }).finally(() => {
            clearTimeout(timeout)
            pending = null
        })

        return pending
    }

    function getEnrichment(plugin: Plugin | null | undefined): PluginEnrichment | null {
        if (!plugin) return null
        const key = plugin.subGroup ?? plugin.group
        return enrichments.value[key] ?? null
    }

    async function fetchVersions(cls: string): Promise<PluginVersion[]> {
        const cached = versionsByCls.value[cls]
        if (cached) return cached
        const inflight = versionsPending[cls]
        if (inflight) return inflight

        const controller = new AbortController()
        const timeout = setTimeout(() => controller.abort(), PUBLIC_API_TIMEOUT_MS)

        const promise = axios.get<PluginVersion[]>(`${API_URL}/v1/plugins/${cls}/versions`, {signal: controller.signal})
            .then(response => {
                const data = response.data ?? []
                versionsByCls.value[cls] = data
                return data
            })
            .catch(err => {
                console.warn("Failed to load plugin versions", cls, err)
                return [] as PluginVersion[]
            })
            .finally(() => {
                clearTimeout(timeout)
                delete versionsPending[cls]
            })

        versionsPending[cls] = promise
        return promise
    }

    function getVersions(cls: string | undefined | null): PluginVersion[] {
        if (!cls) return []
        return versionsByCls.value[cls] ?? []
    }

    return {
        enrichments,
        loaded,
        failed,
        fetchEnrichment,
        getEnrichment,
        fetchVersions,
        getVersions,
    }
})
