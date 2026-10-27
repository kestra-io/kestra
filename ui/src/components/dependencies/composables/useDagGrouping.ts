import {ref, computed} from "vue"
import {useI18n} from "vue-i18n"
import {stringUtils} from "@kestra-io/design-system"
import {ASSET} from "../utils/types"
import type {Edge, Node} from "../utils/types"

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

const CONSUMER_FIELD = "consumer"
const CONSUMED_BY = "CONSUMED_BY"

const accessorFor = (field: typeof GROUP_FIELDS[number]) => (node: Node) =>
    (field.assetOnly && node.metadata.subtype !== ASSET ? NOT_APPLICABLE : field.of(node))

export function useDagGrouping(getNodes: () => Node[], getEdges: () => Edge[] = () => []) {
    const {t} = useI18n({useScope: "global"})

    const groupField = ref("")

    const nodes = computed(() => getNodes())

    const consumersByAsset = computed(() => {
        const index = new Map<string, string[]>()
        getEdges()
            .filter((edge) => edge.kind === CONSUMED_BY)
            .forEach((edge) => index.set(edge.source, [...(index.get(edge.source) ?? []), edge.target]))
        return index
    })

    const consumerField = computed<GroupField[]>(() => {
        const groups = new Set([...consumersByAsset.value.values()].flat()).size
        return groups > 0 ? [{key: CONSUMER_FIELD, label: t("dependency.dag.group_consumer"), groups, usable: groups > 1}] : []
    })

    const groupFields = computed<GroupField[]>(() => [...GROUP_FIELDS
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
        .filter((field) => field.groups > 0), ...consumerField.value])

    const groupOf = computed(() => {
        const field = GROUP_FIELDS.find((candidate) => candidate.key === groupField.value)
        return field ? accessorFor(field) : undefined
    })

    /** Every group a node belongs to; a consumer grouping is multi-valued, so it never feeds the lane layout. */
    const membersOf = computed<((node: Node) => string[]) | undefined>(() => {
        if (groupField.value === CONSUMER_FIELD) {
            const index = consumersByAsset.value
            return (node) => (node.metadata.subtype === ASSET ? (index.get(node.id) ?? [""]) : [node.id])
        }

        const accessor = groupOf.value
        return accessor ? (node) => [accessor(node) ?? ""] : undefined
    })

    const groupChips = computed<GroupChip[]>(() => {
        const members = membersOf.value
        if (!members) {
            return []
        }

        const isConsumer = groupField.value === CONSUMER_FIELD
        const counts = new Map<string, number>()
        nodes.value
            .filter((node) => !isConsumer || node.metadata.subtype === ASSET)
            .forEach((node) => members(node).forEach((key) => counts.set(key, (counts.get(key) ?? 0) + 1)))

        const consumers = new Map(nodes.value.map((node) => [node.id, node]))
        const rank = (key: string): number => (key === "" ? 2 : key === NOT_APPLICABLE ? 1 : 0)
        const labelOf = (key: string): string => {
            if (key === NOT_APPLICABLE) {
                return t("flows")
            }
            if (!isConsumer) {
                return key || t("dependency.dag.ungrouped")
            }
            const consumer = consumers.get(key)
            if (key === "") {
                return t("dependency.dag.no_consumer")
            }
            return consumer?.namespace ? `${consumer.namespace}.${consumer.flow}` : t("dependency.dag.hidden_consumer")
        }

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

    return {nodes, groupField, groupFields, groupOf, membersOf, groupChips, dagPriority}
}
