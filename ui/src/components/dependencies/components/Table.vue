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
                <section id="row">
                    <section id="left">
                        <div id="link">
                            <code class="name">{{ row.data.flow }}</code>
                        </div>

                        <p class="description">
                            {{ row.data.namespace }}
                        </p>
                    </section>

                    <section id="right">
                        <KsExecutionStatus
                            v-if="row.data.metadata.subtype === EXECUTION && row.data.metadata.state"
                            :status="row.data.metadata.state"
                            size="small"
                        />
                        <RouterLink
                            :to="row.to"
                            :title="$t('open')"
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

    import {KsExecutionStatus} from "@kestra-io/design-system"

    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"

    import {NODE, FLOW, EXECUTION, ASSET} from "../utils/types"
    import type {Types, Node, Element} from "../utils/types"

    import {useI18n} from "vue-i18n"
    const {t} = useI18n({useScope: "global"})

    /** Where a row's open arrow goes; executions carry their own execution id. */
    const openRoute = (node: Node) => {
        const {subtype} = node.metadata
        if (subtype === ASSET) return {name: "assets/update", params: {assetId: node.flow}}
        if (subtype === EXECUTION && "id" in node.metadata && node.metadata.id) {
            return {name: "executions/update", params: {namespace: node.namespace, flowId: node.flow, id: node.metadata.id}}
        }
        return {name: "flows/update", params: {namespace: node.namespace, id: node.flow}}
    }

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

        // Pass the IDs of the currently shown nodes to the parent component for highlighting in the graph.
        const IDs = filtered.flatMap(r => (r.data.id !== undefined ? [r.data.id] : []))
        props.highlightShown?.(IDs)

        // The open route is resolved here rather than called from the template: Element Plus
        // renders this column's slot from its own instance, where a setup binding is not in scope.
        return filtered.map((element) => ({...element, to: openRoute(element.data)}))
    })
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

section#row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    max-width: 100%;
    padding: 0.75rem 0 0.75rem 0.75rem;
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
        flex-shrink: 0;
        margin-left: 0.5rem;

        :deep(a:hover .kel-icon) {
            color: var(--ks-text-link);
        }
    }
}
</style>
