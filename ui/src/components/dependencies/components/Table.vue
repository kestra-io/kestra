<template>
    <section id="input">
        <el-input
            v-model="search"
            :placeholder="t('deps_search.placeholder')"
            clearable
        />
    </section>

    <el-table
        :data="results"
        :empty-text="t('deps_search.no_results', {term: search})"
        :show-header="false"
        class="nodes"
        @row-click="(row: { data: Node }) => console.log(row.data)"
    >
        <el-table-column>
            <template #default="{row}">
                <section id="row">
                    <section id="left">
                        <Link :node="row.data" :subtype="row.data.metadata.subtype" />

                        <p class="description">
                            {{ row.data.metadata.subtype === FLOW ? row.data.namespace : `${row.data.namespace}.${row.data.flow}` }}
                        </p>
                    </section>

                    <section id="right">
                        <span v-if="row.data.metadata.subtype === FLOW">
                            {{ t("revision") }}: {{ row.data.metadata.revision }}
                        </span>
                        <Status
                            v-else-if="row.data.metadata.subtype === EXECUTION"
                            :status="row.data.metadata.state"
                            size="small"
                        />
                    </section>
                </section>
            </template>
        </el-table-column>
    </el-table>
</template>

<script setup lang="ts">
    import {ref, computed} from "vue";

    import Link from "./Link.vue";
    import Status from "../../Status.vue";

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import {FLOW, EXECUTION, type Node} from "../../../../scripts/product/dependencies";

    const props = defineProps<{ nodes: { data: Node }[] }>();

    const search = ref("");
    const results = computed(() => {
        const f = search.value.trim().toLowerCase();

        if (!f) return props.nodes;

        return props.nodes.filter(({data}) => {
            const {flow, namespace} = data;

            return flow.toLowerCase().includes(f) || namespace.toLowerCase().includes(f);
        });
    });
</script>

<style scoped lang="scss">
section#input {
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
}

section#row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    max-width: 100%;
    padding: 0.75rem 0 0.75rem 0.75rem;
    font-size: var(--font-size-xs);
    color: var(--ks-button-content-primary);

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

        & p.description {
            margin: 0;
        }
    }

    & section#right {
        flex-shrink: 0;
        margin-left: 0.5rem;
    }
}
</style>
