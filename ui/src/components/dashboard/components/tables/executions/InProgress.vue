<template>
    <div class="h-100 p-4">
        <div class="d-flex justify-content-between align-items-center">
            <span class="fs-6 fw-bold">
                {{ t("dashboard.executions_in_progress") }}
            </span>
            <RouterLink
                :to="{name: 'executions/list',
                      query:{state:[
                          State.RUNNING,
                          State.RESTARTED,
                          State.CREATED,
                          State.PAUSED,
                          State.RETRYING,
                          State.QUEUED,
                          State.KILLING
                      ]}}"
            >
                <el-button type="primary" size="small" text>
                    {{ t("dashboard.see_all") }}
                </el-button>
            </RouterLink>
        </div>

        <div class="pt-4" v-if="executions.results.length">
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
                <el-table-column :label="$t('state')">
                    <template #default="scope">
                        <Status size="small" :status="scope.row.state.current" />
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

        <NoData v-else />
    </div>
</template>

<script setup>
    import {onBeforeMount, ref} from "vue";
    import {useStore} from "vuex";
    import {useI18n} from "vue-i18n";

    import moment from "moment";

    import {State} from "@kestra-io/ui-libs";

    import Status from "../../../../Status.vue";
    import NoData from "../../../../layout/NoData.vue";

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
    color: var(--ks-content-id);
}

.inprogress {
    background: var(--ks-background-body);
    & a {
        color: #8e71f7;

        html.dark & {
            color: #e0e0fc;
        }
    }
}
</style>
