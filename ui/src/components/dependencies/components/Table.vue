<template>
    <section id="filtering">
        <KsSearch
            v-model="search"
            :placeholder="$t(`dependency.search.placeholders.${props.subtype === ASSET ? 'asset' : 'default'}`)"
            clearable
        />

        <KsSelect
            v-model="namespace"
            :placeholder="$t('dependency.search.namespace.select')"
            clearable
            filterable
        >
            <KsOption
                v-for="item in namespaces"
                :key="item.value"
                :label="item.label"
                :value="item.value"
            />
        </KsSelect>

        <KsSwitch v-if="$props.subtype === ASSET" v-model="flow" :activeText="$t('dependency.search.flow.display')" />

        <!-- Numerals only, so it needs no translated string. At a few hundred nodes the
             list length is the only way to tell whether a filter did anything. -->
        <KsText size="small" class="result-count">{{ results.length }} / {{ nodeCount }}</KsText>
    </section>

    <KsTable
        :data="results"
        :emptyText="$t('dependency.search.no_results', {term: search})"
        :showHeader="false"
        class="nodes"
        @row-click="(row: { data: Node }) => emits('select', row.data.id)"
        @row-dblclick="(row: { data: Node }) => emits('open', row.data)"
        @cell-mouse-enter="(row: { data: Node }) => emits('hover', row.data.id)"
        @cell-mouse-leave="() => emits('hover', undefined)"
        :rowClassName="({row}: { row: { data: Node } }) => row.data.id === props.selected ? 'selected' : ''"
    >
        <KsTableColumn>
            <template #default="{row}">
                <section id="row" :class="statusOf(row.data) ? `status-${statusOf(row.data)}` : undefined">
                    <!-- Same glyph vocabulary as the canvas card, from the shared map, so the
                         list and the graph never describe one state two ways. Suppressed when
                         nothing is tracked, where it would be a column of question marks.
                         Colour goes through KsIcon's prop, not a class: the class lands on the
                         ElIcon root where a kel-icon rule already sets colour and wins. -->
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
                            <!-- subtype === ASSET is this component's stand-in for the dagView
                                 gate: only kestra-ee's AssetDependencies view mounts the table
                                 with the ASSET subtype prop. -->
                            <code v-if="isAssetView" class="name" :title="row.data.flow">
                                <!-- Three zones so width is given up in the right order: the
                                     prefix shared by every visible row goes first, then the
                                     rest of the path, and the final segment never truncates
                                     because it is the only part that names the asset. Muted
                                     rather than elided, so the row keeps one stable label
                                     whatever the filter, and stays searchable. -->
                                <span v-if="prefixOf(row.data.flow)" class="name-prefix">{{ prefixOf(row.data.flow) }}</span><span v-if="pathOf(row.data.flow)" class="name-path">{{ pathOf(row.data.flow) }}</span><span class="name-leaf">{{ leafOf(row.data.flow) }}</span>
                            </code>
                            <Link
                                v-else
                                :node="row.data"
                                :subtype="row.data.metadata.subtype"
                            />
                        </div>

                        <!-- Assets carry no namespace, so this line was rendering empty under
                             almost every row. The other three views do populate it. -->
                        <p v-if="row.data.namespace" class="description">
                            {{ row.data.namespace }}
                        </p>
                    </section>

                    <section id="right">
                        <!-- A glyph says "stale", not "stale since when". Without the age the
                             list can only pose the question and the canvas card has to answer
                             it, so this is what stops a round-trip per asset.

                             Compact (`1h`, `2d3h`) rather than KsDateAgo's "an hour ago":
                             at this density the words cost more width than they carry, and
                             humanDuration is already localised so it needs no new key. The
                             full timestamp stays reachable on the title. -->
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
                            :to="row.to"
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

    import Link from "./Link.vue"
    import {KsExecutionStatus} from "@kestra-io/design-system"

    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"

    import {statusIconOf, statusColorOf, compactAge, normalizeStatus} from "../utils/assetStatus"
    import {NODE, FLOW, EXECUTION, NAMESPACE, ASSET} from "../utils/types"
    import type {Types, Node, Element} from "../utils/types"

    import {useI18n} from "vue-i18n"
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

    const emits = defineEmits<{
        (e: "select", id: Node["id"]): void;
        (e: "hover", id?: Node["id"]): void;
        (e: "open", node: Node): void;
    }>()
    const props = defineProps<{
        elements: Element[];
        highlightShown?: (nodeIDs: string[]) => void;
        selected: Node["id"] | undefined;
        subtype?: Types;
    }>()

    /** Equivalent to Dependencies' dagView gate: only the asset view passes the ASSET subtype. */
    const isAssetView = computed(() => props.subtype === ASSET)

    const focusSelectedRow = () => {
        const row = document.querySelector<HTMLElement>(".kel-table__row.selected")

        if (!row) return

        row.scrollIntoView({behavior: "smooth", block: "center"})
    }

    watch(
        () => props.selected,
        async (ID) => {
            if (!ID) return

            await nextTick()

            focusSelectedRow()
        },
    )

    const search = ref("")
    const namespace = ref<string | undefined>(undefined)
    const flow = ref<boolean>(true)

    const NO_NAMESPACE_VALUE = "__NO_NAMESPACE__"

    const isNodeElement = (e: Element): e is {data: Node} => e?.data?.type === NODE

    const namespaces = computed(() => {
        const unique = new Set<string>(
            props.elements
                ?.filter((e): e is {data: Node} => isNodeElement(e) && !!e.data.namespace)
                .map(e => e.data.namespace),
        )

        return [
            ...Array.from(unique).map((ns) => ({
                label: ns,
                value: ns,
            })),
            ...(props.subtype === ASSET ?  [{
                label: t("dependency.search.namespace.no_namespace"),
                value: NO_NAMESPACE_VALUE,
            }] : []),
        ]
    })

    const results = computed(() => {
        const query = search.value.trim().toLowerCase()

        const filtered = props.elements
            .filter(isNodeElement)
            .filter(({data}) => flow.value || data.metadata.subtype !== FLOW)
            .filter(({data}) => {
                if (!namespace.value) return true

                if (namespace.value === NO_NAMESPACE_VALUE) {
                    return data.namespace === undefined
                }

                return data.namespace === namespace.value
            })
            .filter(({data}) => {
                if (!query) return true

                return (
                    data.flow?.toLowerCase().includes(query) ||
                    data.namespace?.toLowerCase().includes(query)
                )
            })

        // The open route is resolved here rather than called from the template: Element Plus
        // renders this column's slot from its own instance, where a setup binding is not in scope.
        return filtered.map((element) => ({...element, to: isAssetView.value ? openRoute(element.data) : baseOpenRoute(element.data)}))
    })

    /**
     * Denominator for the count. The include-flows switch is a filter like any other, so it
     * belongs here rather than only in the numerator: counting against the unfiltered total
     * left the pane reading "15 / 17" forever, which looks like a filter stuck on.
     */
    const nodeCount = computed(() => props.elements
        .filter(isNodeElement)
        .filter(({data}) => flow.value || data.metadata.subtype !== FLOW)
        .length)

    /** Nothing tracked anywhere means the glyph column is a row of question marks. */
    const showStatus = computed(() => results.value.some(({data}) =>
        data.metadata.subtype === ASSET && (data.metadata as {status?: string}).status
        && (data.metadata as {status?: string}).status !== "unknown"))

    const updatedOf = (node: Node): string | undefined =>
        (node.metadata.subtype === ASSET ? (node.metadata as {updated?: string}).updated : undefined)

    /**
     * Telling the parent what to highlight is a side effect, so it belongs in a watch rather
     * than in the body of `results`, where it re-dimmed every canvas node on each keystroke
     * from inside a render pass. Keyed on a joined string, not the array: per AGENTS.md a
     * watch source has to be a primitive to compare by value instead of by identity.
     */
    const shownIDs = computed(() => results.value.flatMap((r) => (r.data.id !== undefined ? [r.data.id] : [])))

    watch(
        () => shownIDs.value.join(","),
        () => props.highlightShown?.(shownIDs.value),
        {immediate: true},
    )

    /** Freshness state, on assets only: the other three views have nothing to show here. */
    const statusOf = (node: Node): string | undefined =>
        (node.metadata.subtype === ASSET ? normalizeStatus((node.metadata as {status?: string}).status) : undefined)

    /**
     * Longest dotted prefix shared by every asset id on screen. In a real graph that is the
     * warehouse project and often the dataset too, identical on every row. It is only muted,
     * never hidden, so it stays searchable and the row keeps one stable label across filters.
     */
    const commonPrefix = computed(() => {
        if (!isAssetView.value) return ""

        const ids = results.value
            .filter(({data}) => data.metadata.subtype === ASSET)
            .map(({data}) => data.flow)
        if (ids.length < 2) return ""

        const segments = ids[0].split(".").slice(0, -1)
        let shared = 0
        while (shared < segments.length) {
            const candidate = segments.slice(0, shared + 1).join(".") + "."
            if (!ids.every((id) => id.startsWith(candidate))) break
            shared++
        }

        return shared ? segments.slice(0, shared).join(".") + "." : ""
    })

    const prefixOf = (id: string): string => (commonPrefix.value && id.startsWith(commonPrefix.value) ? commonPrefix.value : "")

    /** Final dotted segment: the only part that actually names the asset. */
    const leafOf = (id: string): string => id.split(".").pop() || id

    /** Everything between the shared prefix and the leaf, including its trailing dot. */
    const pathOf = (id: string): string => id.slice(prefixOf(id).length, id.length - leafOf(id).length)
</script>

<style scoped lang="scss">
.name {
    display: block;
    max-width: 100%;
    font-size: var(--ks-font-size-sm);
    color: var(--ks-text-primary);
}

section#filtering {
    position: sticky;
    top: 0;
    z-index: 10; // Keeps it above table rows
    padding: 1rem;
    background-color: var(--ks-bg-input);

    :deep(.kel-input__wrapper), :deep(.kel-select__wrapper) {
        margin-bottom: 0.5rem;
        font-size: var(--ks-font-size-sm);
    }
}

.kel-table.nodes {
    outline: none;
    border-radius: 0;
    border-top: 1px solid var(--ks-border-default);

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

.result-count {
    display: block;
    color: var(--ks-text-muted);
}

/**
 * The name is two parts so they can yield width differently: the shared prefix shrinks and
 * ellipsises, the trailing segment never does. End-truncating the whole id would cut exactly
 * the segment that tells two assets apart.
 */
.name {
    display: flex;
    min-width: 0;
}

/* Both context zones shrink; the prefix is first to go because every visible row repeats it. */
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

/* Yields before the asset name does: on a narrow pane a clipped age still reads as an
   order of magnitude, whereas a clipped name identifies nothing. */
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

/**
 * The age carries the same state colour as the glyph, so the pair reads as one signal, and
 * the two surfaces agree: the canvas card colours its age the same way. Neutral is left to
 * the two states that mean "no signal", which is what stops a fresh row and an untracked one
 * from looking identical.
 */
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
    // No vertical padding of its own: the enclosing table cell already contributes its
    // own, and doubling them made a one-line row 56px tall. Horizontal only.
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
        // A high shrink factor, not just a shrinkable one: flex distributes shrinkage in
        // proportion to base size, so the wider left side would otherwise absorb it and clip
        // the asset name while the age kept its full width. Weighting it this way makes the
        // age give up space first, which is the priority that matters.
        flex: 0 8 auto;
        min-width: 0;
        margin-left: 0.5rem;

        & > a {
            flex: 0 0 auto;
        }

        :deep(a:hover .kel-icon) {
            color: var(--ks-text-link);
        }
    }
}

/**
 * The open arrow is per-row, so at a few hundred rows a permanently visible one is a column
 * of gravel down the pane. Kept in the layout with `visibility` rather than `display` so
 * revealing it never reflows the row. The selected row keeps it, since that row is the one
 * the user is acting on.
 */
section#row section#right a {
    visibility: hidden;
}

/* focus-within is not optional: visibility:hidden takes the link out of the tab order, so
   without it a keyboard user has no way to reach the open action at all. */
section#row:hover section#right a,
section#row:focus-within section#right a,
.kel-table__row.selected section#row section#right a {
    visibility: visible;
}
</style>
