<template>
    <el-table :data="props.nodes" :show-header="false">
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
    import Link from "./Link.vue";
    import Status from "../../Status.vue";

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import {FLOW, EXECUTION} from "../../../../scripts/product/dependencies";

    const props = defineProps<{ nodes: { data: Node }[] }>();
</script>

<style scoped lang="scss">
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
