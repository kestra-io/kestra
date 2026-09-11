import {ref, computed} from "vue"
import {useI18n} from "vue-i18n"
import {stringUtils} from "@kestra-io/design-system"
import {ASSET} from "../utils/types"
import type {Node} from "../utils/types"

export interface GroupField {
    key: string;
    label: string;
    groups: number;
    usable: boolean;
}

export interface GroupChip {
    key: string;
    label: string;
    count: number;
}

/** Bucket for nodes a field cannot apply to, kept distinct from Ungrouped; the leading space avoids key collisions. */
const NOT_APPLICABLE = " not-applicable"

const pluginOf = (type?: string): string | undefined => {
    if (!type) {
        return undefined
    }
    const segments = type.split(".")
    if (type.startsWith("io.kestra.plugin.")) {
        return segments.length >= 4 ? segments[3] : undefined
    }
    return segments.length >= 2 ? segments[segments.length - 2] : undefined
}

const schemaOf = (id: string): string | undefined => {
    const segments = id.split(".")
    return segments.length >= 3 ? segments[segments.length - 2] : undefined
}

const GROUP_FIELDS = [
    {
        key: "dataset",
        labelKey: "dependency.dag.group_dataset",
        assetOnly: true,
        of: (node: Node) => (node.metadata as {schema?: string}).schema ?? schemaOf(node.flow),
    },
    {
        key: "type",
        labelKey: "type",
        assetOnly: true,
        of: (node: Node) => stringUtils.afterLastDot((node.metadata as {assetType?: string}).assetType ?? "") || undefined,
    },
    {
        key: "producer",
        labelKey: "plugins.names",
        assetOnly: true,
        of: (node: Node) => pluginOf((node.metadata as {producer?: string}).producer),
    },
    {
        key: "system",
        labelKey: "dependency.dag.system",
        assetOnly: true,
        of: (node: Node) => (node.metadata as {system?: string}).system,
    },
    {
        key: "namespace",
        labelKey: "namespace",
        assetOnly: false,
        of: (node: Node) => node.namespace,
    },
] as const

const accessorFor = (field: typeof GROUP_FIELDS[number]) => (node: Node) =>
    (field.assetOnly && node.metadata.subtype !== ASSET ? NOT_APPLICABLE : field.of(node))

export function useDagGrouping(getNodes: () => Node[]) {
    const {t} = useI18n({useScope: "global"})

    const groupField = ref("")

    const nodes = computed(() => getNodes())

    const groupFields = computed<GroupField[]>(() => GROUP_FIELDS
        .map((field) => {
            const accessor = accessorFor(field)
            const groups = new Set(nodes.value.map(accessor).filter(Boolean)).size

            return {
                key: field.key,
                label: t(field.labelKey),
                groups,
                // One group says nothing, and a lane per node is a diagonal rather than a grouping.
                usable: groups > 1 && groups < nodes.value.length,
            }
        })
        .filter((field) => field.groups > 0))

    const groupOf = computed(() => {
        const field = GROUP_FIELDS.find((candidate) => candidate.key === groupField.value)
        return field ? accessorFor(field) : undefined
    })

    const groupChips = computed<GroupChip[]>(() => {
        const accessor = groupOf.value
        if (!accessor) {
            return []
        }

        const counts = new Map<string, number>()
        nodes.value.forEach((node) => {
            const key = accessor(node) ?? ""
            counts.set(key, (counts.get(key) ?? 0) + 1)
        })

        const rank = (key: string): number => (key === "" ? 2 : key === NOT_APPLICABLE ? 1 : 0)
        const labelOf = (key: string): string =>
            (key === NOT_APPLICABLE ? t("flows") : key || t("dependency.dag.ungrouped"))

        return [...counts.entries()]
            .sort(([a], [b]) => (rank(a) - rank(b)) || (a < b ? -1 : 1))
            .map(([key, count]) => ({key, count, label: labelOf(key)}))
    })

    const dagPriority = computed(() => {
        const accessor = groupOf.value
        if (!accessor) {
            return undefined
        }

        const rank = new Map(groupChips.value.map((chip, index) => [chip.key, index]))
        const byNode = new Map(nodes.value.map((node) => [node.id, rank.get(accessor(node) ?? "") ?? 0]))
        return (id: string) => byNode.get(id) ?? 0
    })

    return {nodes, groupField, groupFields, groupOf, groupChips, dagPriority}
}
