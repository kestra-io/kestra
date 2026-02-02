<template>
    <section id="input">
        <el-input
            v-model="search"
            :placeholder="$t(props.subtype === ASSET ? 'dependency.search.asset_placeholder' : 'dependency.search.placeholder')"
            clearable
        />
    </section>

    <el-table
        :data="results"
        :emptyText="$t('dependency.search.no_results', {term: search})"
        :showHeader="false"
        class="nodes"
        @row-click="(row: { data: Node }) => emits('select', row.data.id)"
        :rowClassName="({row}: { row: { data: Node } }) => row.data.id === props.selected ? 'selected' : ''"
    >
        <el-table-column>
            <template #default="{row}">
                <section id="row">
                    <section id="left">
                        <div id="link">
                            <Link
                                :node="row.data"
                                :subtype="row.data.metadata.subtype"
                            />
                        </div>

                        <p class="description">
                            {{ row.data.namespace }}
                        </p>
                    </section>

                    <section id="right">
                        <Status
                            v-if="row.data.metadata.subtype === EXECUTION && row.data.metadata.state"
                            :status="row.data.metadata.state"
                            size="small"
                        />
                        <RouterLink
                            v-if="[FLOW, NAMESPACE, ASSET].includes(row.data.metadata.subtype)"
                            :to="{
                                name: row.data.metadata.subtype === ASSET ? 'assets/update' : 'flows/update',
                                params: row.data.metadata.subtype === ASSET 
                                    ? {namespace: row.data.namespace, assetId: row.data.flow}
                                    : {namespace: row.data.namespace, id: row.data.flow}
                            }"
                        >
                            <el-icon :size="16">
                                <OpenInNew />
                            </el-icon>
                        </RouterLink>
                    </section>
                </section>
            </template>
        </el-table-column>
    </el-table>
</template>

<script setup lang="ts">
    import {watch, nextTick, ref, computed} from "vue";

    import type cytoscape from "cytoscape";

    import Link from "./Link.vue";
    import {Status} from "@kestra-io/ui-libs";

    import OpenInNew from "vue-material-design-icons/OpenInNew.vue";

    import {NODE, FLOW, EXECUTION, NAMESPACE, ASSET, type Node} from "../utils/types";

    const emits = defineEmits<{ (e: "select", id: Node["id"]): void }>();
    const props = defineProps<{
        elements: cytoscape.ElementDefinition[];
        selected: Node["id"] | undefined;
        subtype?: typeof FLOW | typeof EXECUTION | typeof NAMESPACE | typeof ASSET;
    }>();

    const focusSelectedRow = () => {
        const row = document.querySelector<HTMLElement>(".el-table__row.selected");

        if (!row) return;

        row.scrollIntoView({behavior: "smooth", block: "center"});
    };

    watch(
        () => props.selected,
        async (ID) => {
            if (!ID) return;

            await nextTick();

            focusSelectedRow();
        },
    );

    const search = ref("");
    const results = computed(() => {
        const f = search.value.trim().toLowerCase();

        const NODES = props.elements.filter(({data}) => data.type === NODE);

        if (!f) return NODES;

        return NODES.filter(({data}) => {
            const {flow, namespace} = data;

            return (
                flow?.toLowerCase().includes(f) ||
                namespace?.toLowerCase().includes(f)
            );
        });
    });
</script>

<style scoped lang="scss">
section#input {
    position: sticky;
    top: 0;
    z-index: 10; // Keeps it above table rows
    padding: 0.5rem;
    background-color: var(--ks-background-input);

    :deep(.el-input__wrapper) {
        box-shadow: none !important;
        font-size: var(--font-size-sm);
    }
}

.el-table.nodes {
    outline: none;
    border-radius: 0;
    border-top: 1px solid var(--ks-border-primary);

    :deep(.el-table__empty-text) {
        width: 100%;
        font-size: var(--font-size-sm);
    }

    & :deep(.el-table__row.selected) {
        background-color: var(--ks-tag-background);

        &:hover {
            --el-table-row-hover-bg-color: var(--ks-tag-background-hover);
        }
    }
}

section#row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    max-width: 100%;
    padding: 0.75rem 0 0.75rem 0.75rem;
    font-size: var(--font-size-xs);
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
        }

        & p.description {
            margin: 0;
            color: var(--ks-content-primary);
        }
    }

    & section#right {
        flex-shrink: 0;
        margin-left: 0.5rem;

        :deep(a:hover .el-icon) {
            color: var(--ks-content-link-hover);
        }
    }
}
</style>
