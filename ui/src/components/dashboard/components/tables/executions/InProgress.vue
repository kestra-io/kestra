<template>
    <div class="p-4">
        <!-- <span class="fs-6 fw-bold">
            {{ t("dashboard.executions_in_progress") }}
        </span> -->
        
        <div class="d-flex justify-content-between align-items-center">
            <span class="fs-6 fw-bold">
                {{ t("dashboard.executions_in_progress") }}
            </span>
            <RouterLink :to="{name: 'executions/list'}">
                <el-button type="primary" size="small" class="seeall" text>
                    {{ t("dashboard.see_all") }}
                </el-button>
            </RouterLink>
        </div>

        <div class="pt-4">
            <el-table
                :data="executions.results"
                class="inprogress"
                :height="240"
            >
                <el-table-column :label="$t('dashboard.id')" width="80">
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
                            <el-tooltip
                                :content="scope.row.namespace"
                                placement="right"
                            >
                                <span class="text-truncate">
                                    {{ scope.row.namespace }}
                                </span>
                            </el-tooltip>
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
                            <el-tooltip
                                :content="scope.row.flowId"
                                placement="right"
                            >
                                <span class="text-truncate">
                                    {{ scope.row.flowId }}
                                </span>
                            </el-tooltip>
                        </RouterLink>
                    </template>
                </el-table-column>
                <el-table-column :label="$t('duration')" width="100">
                    <template #default="scope">
                        {{
                            (
                                moment
                                    .duration(scope.row.state.duration)
                                    .milliseconds() / 1000 || 0
                            ).toFixed(3)
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
    --el-table-tr-bg-color: var(--bs-body-bg) !important;
    background: var(--bs-body-bg);
}

.seeall {
    color: var(--el-color-primary);
}
</style>
