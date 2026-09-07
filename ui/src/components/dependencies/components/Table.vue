<template>
    <TableFilters
        v-model:search="search"
        v-model:namespace="namespace"
        v-model:flow="flow"
        :assetView="isAssetView"
        :namespaces="namespaces"
        :shown="results.length"
        :total="nodeCount"
    />

    <KsTable
        :data="results"
        :emptyText="$t('dependency.search.no_results', {term: search})"
        :showHeader="false"
        class="nodes"
        @row-click="(row: { data: Node }) => emit('select', row.data.id)"
        @row-dblclick="(row: { data: Node }) => emit('open', row.data)"
        @cell-mouse-enter="(row: { data: Node }) => emit('hover', row.data.id)"
        @cell-mouse-leave="() => emit('hover', undefined)"
        :rowClassName="({row}: { row: { data: Node } }) => row.data.id === selected ? 'selected' : ''"
    >
        <KsTableColumn>
            <template #default="{row}">
                <section id="row" :class="statusOf(row.data) ? `status-${statusOf(row.data)}` : undefined">
                    <KsIcon
                        v-if="showStatus && statusOf(row.data)"
                        size="xs"
                        class="row-status"
                        :color="statusColorOf(statusOf(row.data))"
                        :tooltip="$t(`dependency.dag.status.${statusOf(row.data)}`)"
                    >
                        <component :is="statusIconOf(statusOf(row.data))" />
                    </KsIcon>

                    <section id="left">
                        <div id="link">
                            <code v-if="isAssetView" class="name" :title="row.data.flow">
                                <span v-if="prefixOf(row.data.flow)" class="name-prefix">{{ prefixOf(row.data.flow) }}</span><span v-if="pathOf(row.data.flow)" class="name-path">{{ pathOf(row.data.flow) }}</span><span class="name-leaf">{{ leafOf(row.data.flow) }}</span>
                            </code>
                            <Link
                                v-else
                                :node="row.data"
                                :subtype="row.data.metadata.subtype"
                            />
                        </div>

                        <p v-if="row.data.namespace" class="description">
                            {{ row.data.namespace }}
                        </p>
                    </section>

                    <section id="right">
                        <span
                            v-if="isAssetView && compactAge(updatedOf(row.data))"
                            class="row-age"
                            :title="updatedOf(row.data)"
                        >{{ compactAge(updatedOf(row.data)) }}</span>
                        <KsExecutionStatus
                            v-if="row.data.metadata.subtype === EXECUTION && row.data.metadata.state"
                            :status="row.data.metadata.state"
                            size="small"
                        />
                        <RouterLink
                            v-if="isAssetView || [FLOW, NAMESPACE, ASSET].includes(row.data.metadata.subtype)"
                            :to="isAssetView ? openRoute(row.data) : baseOpenRoute(row.data)"
                            :title="isAssetView ? $t('open') : undefined"
                        >
                            <KsIcon size="sm">
                                <OpenInNew />
                            </KsIcon>
                        </RouterLink>
                    </section>
                </section>
            </template>
        </KsTableColumn>
    </KsTable>
</template>

<script setup lang="ts">
    import {watch, nextTick, ref, computed} from "vue"
    import {useI18n} from "vue-i18n"
    import {KsExecutionStatus, stringUtils} from "@kestra-io/design-system"
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"
    import Link from "./Link.vue"
    import TableFilters from "./TableFilters.vue"
    import {statusIconOf, statusColorOf, compactAge, normalizeStatus} from "../utils/assetStatus"
    import {NODE, FLOW, EXECUTION, NAMESPACE, ASSET} from "../utils/types"
    import type {Types, Node, Element} from "../utils/types"

    const {t} = useI18n({useScope: "global"})

    /** Asset view: where a row's open arrow goes; executions carry their own execution id. */
    const openRoute = (node: Node) => {
        const {subtype} = node.metadata
        if (subtype === ASSET) return {name: "assets/update", params: {assetId: node.flow}}
        if (subtype === EXECUTION && "id" in node.metadata && node.metadata.id) {
            return {name: "executions/update", params: {namespace: node.namespace, flowId: node.flow, id: node.metadata.id}}
        }
        return {name: "flows/update", params: {namespace: node.namespace, id: node.flow}}
    }

    /** The pre-DAG arrow target, kept as-is for the flow, execution and namespace views. */
    const baseOpenRoute = (node: Node) => ({
        name: node.metadata.subtype === ASSET ? "assets/update" : "flows/update",
        params: node.metadata.subtype === ASSET
            ? {namespace: node.namespace, assetId: node.flow}
            : {namespace: node.namespace, id: node.flow},
    })

    const emit = defineEmits<{
        select: [id: Node["id"]];
        hover: [id?: Node["id"]];
        open: [node: Node];
    }>()
    const props = defineProps<{
        elements: Element[];
        highlightShown?: (nodeIDs: string[]) => void;
        selected: Node["id"] | undefined;
        subtype?: Types;
    }>()

    /** Equivalent to Dependencies' dagView gate: only the asset view passes the ASSET subtype. */
    const isAssetView = computed(() => props.subtype === ASSET)

    watch(() => props.selected, async (id) => {
        if (!id) {
            return
        }
        await nextTick()
        document.querySelector<HTMLElement>(".kel-table__row.selected")
            ?.scrollIntoView({behavior: "smooth", block: "center"})
    })

    const search = ref("")
    const namespace = ref<string | undefined>(undefined)
    const flow = ref(true)

    const NO_NAMESPACE_VALUE = "__NO_NAMESPACE__"

    const isNodeElement = (e: Element): e is {data: Node} => e?.data?.type === NODE

    const namespaces = computed(() => {
        const unique = new Set(
            props.elements.flatMap((e) => (isNodeElement(e) && e.data.namespace ? [e.data.namespace] : [])),
        )

        return [
            ...[...unique].map((ns) => ({label: ns, value: ns})),
            ...(isAssetView.value ? [{
                label: t("dependency.search.namespace.no_namespace"),
                value: NO_NAMESPACE_VALUE,
            }] : []),
        ]
    })

    /** All rows the include-flows switch leaves visible; the result count uses it as its denominator. */
    const visibleRows = computed(() => props.elements
        .filter(isNodeElement)
        .filter(({data}) => flow.value || data.metadata.subtype !== FLOW))

    const results = computed(() => {
        const query = search.value.trim().toLowerCase()

        return visibleRows.value
            .filter(({data}) => {
                if (!namespace.value) return true
                if (namespace.value === NO_NAMESPACE_VALUE) return data.namespace === undefined
                return data.namespace === namespace.value
            })
            .filter(({data}) => {
                if (!query) return true
                return data.flow?.toLowerCase().includes(query) || data.namespace?.toLowerCase().includes(query)
            })
    })

    const nodeCount = computed(() => visibleRows.value.length)

    /** Nothing tracked anywhere means the glyph column is a row of question marks. */
    const showStatus = computed(() => results.value.some(({data}) => {
        if (data.metadata.subtype !== ASSET) {
            return false
        }
        const {status} = data.metadata as {status?: string}
        return Boolean(status) && status !== "unknown"
    }))

    const updatedOf = (node: Node): string | undefined =>
        (node.metadata.subtype === ASSET ? (node.metadata as {updated?: string}).updated : undefined)

    // A watch keyed on a joined string, per AGENTS.md: emitting from `results` itself would fire inside a render pass.
    const shownIDs = computed(() => results.value.map((r) => r.data.id))

    watch(
        () => shownIDs.value.join(","),
        () => props.highlightShown?.(shownIDs.value),
        {immediate: true},
    )

    /** Freshness state, on assets only: the other three views have nothing to show here. */
    const statusOf = (node: Node): string | undefined =>
        (node.metadata.subtype === ASSET ? normalizeStatus((node.metadata as {status?: string}).status) : undefined)

    /** Longest dotted prefix shared by every asset id on screen, usually the warehouse project and dataset. */
    const commonPrefix = computed(() => {
        if (!isAssetView.value) {
            return ""
        }

        const ids = results.value
            .filter(({data}) => data.metadata.subtype === ASSET)
            .map(({data}) => data.flow)
        if (ids.length < 2) {
            return ""
        }

        // Grow the prefix one dotted segment at a time, as long as every id still starts with it.
        let prefix = ""
        for (const segment of ids[0].split(".").slice(0, -1)) {
            const candidate = `${prefix}${segment}.`
            if (!ids.every((id) => id.startsWith(candidate))) {
                break
            }
            prefix = candidate
        }

        return prefix
    })

    const prefixOf = (id: string): string => (commonPrefix.value && id.startsWith(commonPrefix.value) ? commonPrefix.value : "")

    /** Final dotted segment: the only part that actually names the asset. */
    const leafOf = (id: string): string => stringUtils.afterLastDot(id) || id

    /** Everything between the shared prefix and the leaf, including its trailing dot. */
    const pathOf = (id: string): string => id.slice(prefixOf(id).length, id.length - leafOf(id).length)
</script>

<style scoped lang="scss">
.name {
    display: flex;
    min-width: 0;
    max-width: 100%;
    font-size: var(--ks-font-size-sm);
    color: var(--ks-text-primary);
}

.kel-table.nodes {
    outline: none;
    border-radius: 0;

    :deep(.kel-table__empty-text) {
        width: 100%;
        font-size: var(--ks-font-size-sm);
    }

    & :deep(.kel-table__row.selected) {
        background-color: var(--ks-bg-tag);

        &:hover {
            --kel-table-row-hover-bg-color: var(--ks-bg-tag-hover);
        }
    }
}

.name-prefix,
.name-path {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.name-prefix {
    flex: 0 2 auto;
    color: var(--ks-text-muted);
}

.name-path {
    flex: 0 1 auto;
    color: var(--ks-text-secondary);
}

.name-leaf {
    flex: 0 0 auto;
    white-space: nowrap;
    color: var(--ks-text-primary);
}

.row-age {
    flex: 0 1 auto;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: var(--ks-text-muted);
}

.row-status {
    flex: 0 0 auto;
}

section#row.status-fresh .row-age {
    color: var(--ks-text-success);
}

section#row.status-stale .row-age {
    color: var(--ks-text-warning);
}

section#row.status-failed .row-age {
    color: var(--ks-text-error);
}

section#row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: var(--ks-spacing-2);
    max-width: 100%;
    padding: 0 0 0 var(--ks-spacing-3);
    font-size: var(--ks-font-size-xs);
    cursor: pointer;

    & section#left {
        display: flex;
        flex-direction: column;
        flex: 1;
        min-width: 0;

        & * {
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        & > div#link {
            width: fit-content;
            max-width: 100%;
        }

        & p.description {
            margin: 0;
            color: var(--ks-text-primary);
        }
    }

    & section#right {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        flex: 0 8 auto;
        min-width: 0;
        margin-left: var(--ks-spacing-2);

        & > a {
            flex: 0 0 auto;
        }

        :deep(a:hover .kel-icon) {
            color: var(--ks-text-link);
        }
    }
}

section#row section#right a {
    visibility: hidden;
}

section#row:hover section#right a,
section#row:focus-within section#right a,
.kel-table__row.selected section#row section#right a {
    visibility: visible;
}
</style>
