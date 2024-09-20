<template>
    <div class="p-4">
        <span class="fs-6 fw-bold">
            {{ t("dashboard.executions_in_progress") }}
        </span>

        <div class="pt-4">
            <el-table
                :data="executions.results"
                class="inprogress"
                :height="240"
            >
                <el-table-column :label="$t('dashboard.id')" width="100">
                    <template #default="scope">
                        <RouterLink
                            :to="{
                                name: 'executions/update',
                                params: {
                                    namespace: scope.row.namespace,
                                    flowId: scope.row.flowId,
                                    id: scope.row.id,
                                },
                            }"
                        >
                            <code>
                                {{ scope.row.id.slice(0, 8) }}
                            </code>
                        </RouterLink>
                    </template>
                </el-table-column>
                <el-table-column :label="$t('namespace')">
                    <template #default="scope">
                        <RouterLink
                            :to="{
                                name: 'namespaces/update',
                                params: {
                                    id: scope.row.namespace,
                                },
                            }"
                        >
                            {{ scope.row.namespace }}
                        </RouterLink>
                    </template>
                </el-table-column>
                <el-table-column :label="$t('flow')">
                    <template #default="scope">
                        <RouterLink
                            :to="{
                                name: 'flows/update',
                                params: {
                                    namespace: scope.row.namespace,
                                    id: scope.row.flowId,
                                },
                            }"
                        >
                            {{ scope.row.flowId }}
                        </RouterLink>
                    </template>
                </el-table-column>
                <el-table-column :label="$t('duration')" width="100">
                    <template #default="scope">
                        {{
                            moment
                                .duration(scope.row.state.duration)
                                .milliseconds() / 1000
                        }}s
                    </template>
                </el-table-column>
                <el-table-column :label="$t('state')" width="100">
                    <template #default="scope">
                        <States :label="scope.row.state.current" />
                    </template>
                </el-table-column>
            </el-table>
            <div class="d-flex justify-content-end">
                <el-pagination
                    v-model:current-page="currentPage"
                    @current-change="loadExecutions"
                    :total="executions.total"
                    layout="prev, pager, next, total"
                    :page-size="5"
                    size="small"
                    class="pt-3"
                />
            </div>
        </div>
    </div>
</template>

<script setup>
    import {onBeforeMount, ref} from "vue";
    import {useStore} from "vuex";
    import {useI18n} from "vue-i18n";

    import moment from "moment";

    import States from "../../States.vue";

    import {RouterLink} from "vue-router";

    const props = defineProps({
        flow: {
            type: String,
            required: false,
            default: null,
        },
        namespace: {
            type: String,
            required: false,
            default: null,
        },
    });

    const store = useStore();
    const {t} = useI18n({useScope: "global"});

    const executions = ref({results: [], total: 0});
    const currentPage = ref(1);

    const loadExecutions = (page = 1) => {
        store
            .dispatch("execution/findExecutions", {
                namespace: props.namespace,
                flowId: props.flow,
                size: 5,
                page,
                state: [
                    "RUNNING",
                    "PAUSED",
                    "RESTARTED",
                    "KILLING",
                    "QUEUED",
                    "RETRYING",
                ],
            })
            .then((response) => {
                if (!response) return;
                executions.value = response;
            });
    };
    onBeforeMount(() => {
        loadExecutions();
    });
</script>

<style lang="scss" scoped>
code {
    color: var(--bs-code-color);
}

.inprogress {
    --el-table-header-bg-color: transparent;
    --el-table-header-text-color: var(--bs-body-color);
    --el-table-tr-bg-color: white;
    outline: 1px solid var(--bs-border-color);
    border-radius: var(--bs-border-radius-lg);
    background-color: transparent;

    html.dark & {
        --el-table-tr-bg-color: transparent;
    }
}
</style>
