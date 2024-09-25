<template>
    <div class="p-4">
        <span class="fs-6 fw-bold">
            {{ t("dashboard.next_scheduled_executions") }}
        </span>

        <div class="pt-4">
            <el-table
                :data="executions.results"
                class="inprogress"
                :height="240"
            >
                <el-table-column class-name="next-toggle" width="50">
                    <template #default="scope">
                        <el-tooltip
                            v-if="scope.row.tooltip"
                            :content="t('dashboard.trigger_disabled')"
                        >
                            <el-switch
                                disabled
                                :model-value="!scope.row.disabled"
                                @change="
                                    toggleState(scope.row.triggerContext);
                                    scope.row.triggerContext.disabled =
                                        !scope.row.triggerContext.disabled;
                                "
                                :active-icon="Check"
                                size="small"
                                inline-prompt
                            />
                        </el-tooltip>
                        <el-switch
                            v-else
                            :model-value="!scope.row.disabled"
                            @change="
                                toggleState(scope.row.triggerContext);
                                scope.row.triggerContext.disabled =
                                    !scope.row.triggerContext.disabled;
                            "
                            :active-icon="Check"
                            size="small"
                            inline-prompt
                        />
                    </template>
                </el-table-column>
                <el-table-column :label="$t('dashboard.id')" width="100">
                    <template #default="scope">
                        <RouterLink :to="{name: 'admin/triggers'}">
                            <el-tooltip
                                :content="scope.row.triggerContext.triggerId"
                                placement="right"
                            >
                                <code class="text-truncate">
                                    {{ scope.row.triggerContext.triggerId }}
                                </code>
                            </el-tooltip>
                        </RouterLink>
                    </template>
                </el-table-column>
                <el-table-column :label="$t('namespace')">
                    <template #default="scope">
                        <RouterLink
                            :to="{
                                name: 'namespaces/update',
                                params: {
                                    id: scope.row.triggerContext.namespace,
                                },
                            }"
                        >
                            <el-tooltip
                                :content="scope.row.triggerContext.namespace"
                                placement="right"
                            >
                                <span class="text-truncate">
                                    {{ scope.row.triggerContext.namespace }}
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
                                    namespace:
                                        scope.row.triggerContext.namespace,
                                    id: scope.row.triggerContext.flowId,
                                },
                            }"
                        >
                            <el-tooltip
                                :content="scope.row.triggerContext.flowId"
                                placement="right"
                            >
                                <span class="text-truncate">
                                    {{ scope.row.triggerContext.flowId }}
                                </span>
                            </el-tooltip>
                        </RouterLink>
                    </template>
                </el-table-column>
                <el-table-column :label="$t('dashboard.next_execution_date')">
                    <template #default="scope">
                        <el-tooltip
                            v-if="!scope.row.disabled"
                            :content="scope.row.triggerContext.flowId"
                            placement="right"
                        >
                            <span class="text-truncate">
                                {{
                                    moment(
                                        scope.row.triggerContext
                                            .nextExecutionDate,
                                    ).format("lll")
                                }}
                            </span>
                        </el-tooltip>
                        <span v-else>-</span>
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

    import Check from "vue-material-design-icons/Check.vue";

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
            .dispatch("trigger/search", {
                namespace: props.namespace,
                flowId: props.flow,
                size: 5,
                page,
                sort: "nextExecutionDate:asc",
            })
            .then((response) => {
                if (!response) return;
                executions.value = {
                    total: response.total,
                    results: response.results?.map(
                        ({abstractTrigger, triggerContext, ...rest}) => {
                            const disabled =
                                abstractTrigger.disabled || triggerContext.disabled;
                            const tooltip = !!abstractTrigger.disabled;

                            return {
                                ...rest,
                                abstractTrigger,
                                triggerContext,
                                disabled,
                                tooltip,
                            };
                        },
                    ),
                };
            });
    };

    const toggleState = (trigger) => {
        store.dispatch("trigger/update", {
            ...trigger,
            disabled: !trigger.disabled,
        });
    };

    onBeforeMount(() => {
        loadExecutions();
    });
</script>

<style lang="scss">
code {
    color: var(--bs-code-color);
}

.inprogress {
    --el-table-tr-bg-color: var(--bs-body-bg) !important;
    background: var(--bs-body-bg);
}

.next-toggle {
    padding: 8px 0 0 0 !important;
}
</style>
