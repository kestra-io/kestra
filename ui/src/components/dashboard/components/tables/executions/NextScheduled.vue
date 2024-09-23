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
                <el-table-column
                    :label="$t('state')"
                    width="100"
                    class-name="next-toggle"
                >
                    <template #default="scope">
                        <el-switch
                            :model-value="!scope.row.triggerContext.disabled"
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
                            <code>
                                {{ scope.row.triggerContext.triggerId }}
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
                                    id: scope.row.triggerContext.namespace,
                                },
                            }"
                        >
                            {{ scope.row.triggerContext.namespace }}
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
                            {{ scope.row.triggerContext.flowId }}
                        </RouterLink>
                    </template>
                </el-table-column>
                <el-table-column :label="$t('dashboard.next_execution_date')">
                    <template #default="scope">
                        {{
                            moment(
                                scope.row.triggerContext.nextExecutionDate,
                            ).format("lll")
                        }}
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
                executions.value = response;
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

.next-toggle {
    padding: 8px 0 0 0 !important;
}
</style>
