<template>
    <top-nav-bar :title="routeInfo.title" />
    <section class="container" v-if="ready">
        <div>
            <data-table
                @page-changed="onPageChanged"
                ref="dataTable"
                :total="total"
            >
                <template #navbar>
                    <el-form-item>
                        <search-field />
                    </el-form-item>
                    <el-form-item>
                        <namespace-select
                            data-type="flow"
                            :value="$route.query.namespace"
                            @update:model-value="onDataTableValue('namespace', $event)"
                        />
                    </el-form-item>
                    <el-form-item>
                        <el-select v-model="state" clearable :placeholder="$t('triggers_state.state')">
                            <el-option
                                v-for="(s, index) in states"
                                :key="index"
                                :label="s.label"
                                :value="s.value"
                            />
                        </el-select>
                    </el-form-item>
                    <el-form-item>
                        <refresh-button class="float-right" @refresh="load(onDataLoaded)" />
                    </el-form-item>
                </template>
                <template #table>
                    <select-table
                        :data="triggersMerged"
                        ref="selectTable"
                        :default-sort="{prop: 'flowId', order: 'ascending'}"
                        stripe
                        table-layout="auto"
                        fixed
                        @sort-change="onSort"
                        @selection-change="onSelectionChange"
                    >
                        <template #select-actions>
                            <bulk-select
                                :select-all="queryBulkAction"
                                :selections="selection"
                                :total="total"
                                @update:select-all="toggleAllSelection"
                                @unselect="toggleAllUnselected"
                            >
                                <el-button>
                                    {{ $t('enable') }}
                                </el-button>
                                <el-button>
                                    {{ $t('disable') }}
                                </el-button>
                                <el-button>
                                    {{ $t('unlock') }}
                                </el-button>
                                <el-button>
                                    {{ $t('pause backfill') }}
                                </el-button>
                                <el-button>
                                    {{ $t('continue backfill') }}
                                </el-button>
                                <el-button>
                                    {{ $t('delete backfill') }}
                                </el-button>
                            </bulk-select>
                        </template>
                        <el-table-column
                            prop="triggerId"
                            sortable="custom"
                            :sort-orders="['ascending', 'descending']"
                            :label="$t('id')"
                        />
                        <el-table-column
                            sortable="custom"
                            :sort-orders="['ascending', 'descending']"
                            :label="$t('flow')"
                        >
                            <template #default="scope">
                                <router-link
                                    :to="{name: 'flows/update', params: {namespace: scope.row.namespace, id: scope.row.flowId}}"
                                >
                                    {{ $filters.invisibleSpace(scope.row.flowId) }}
                                </router-link>
                                <markdown-tooltip
                                    :id="scope.row.namespace + '-' + scope.row.flowId"
                                    :description="scope.row.description"
                                    :title="scope.row.namespace + '.' + scope.row.flowId"
                                />
                            </template>
                        </el-table-column>
                        <el-table-column
                            prop="namespace"
                            sortable="custom"
                            :sort-orders="['ascending', 'descending']"
                            :label="$t('namespace')"
                        >
                            <template #default="scope">
                                {{ $filters.invisibleSpace(scope.row.namespace) }}
                            </template>
                        </el-table-column>

                        <el-table-column :label="$t('current execution')">
                            <template #default="scope">
                                <router-link
                                    v-if="scope.row.executionId"
                                    :to="{name: 'executions/update', params: {namespace: scope.row.namespace, flowId: scope.row.flowId, id: scope.row.executionId}}"
                                >
                                    <id :value="scope.row.executionId" :shrink="true" />
                                </router-link>
                            </template>
                        </el-table-column>

                        <el-table-column :label="$t('state')">
                            <template #default="scope">
                                <status v-if="scope.row.executionCurrentState" :status="scope.row.executionCurrentState" size="small" />
                            </template>
                        </el-table-column>
                        <el-table-column :label="$t('date')">
                            <template #default="scope">
                                <date-ago :inverted="true" :date="scope.row.date" />
                            </template>
                        </el-table-column>
                        <el-table-column :label="$t('updated date')">
                            <template #default="scope">
                                <date-ago :inverted="true" :date="scope.row.updatedDate" />
                            </template>
                        </el-table-column>
                        <el-table-column :label="$t('next execution date')">
                            <template #default="scope">
                                <date-ago :inverted="true" :date="scope.row.nextExecutionDate" />
                            </template>
                        </el-table-column>
                        <el-table-column :label="$t('evaluation lock date')">
                            <template #default="scope">
                                <date-ago :inverted="true" :date="scope.row.nextExecutionDatevaluateRunningDate" />
                            </template>
                        </el-table-column>
                        <el-table-column v-if="user.hasAnyAction(permission.EXECUTION, action.UPDATE)" column-key="action" class-name="row-action">
                            <template #default="scope">
                                <el-button size="small" v-if="scope.row.executionId || scope.row.evaluateRunningDate">
                                    <kicon
                                        :tooltip="$t(`unlock trigger.tooltip.${scope.row.executionId ? 'execution' : 'evaluation'}`)"
                                        placement="left"
                                        @click="triggerToUnlock = scope.row"
                                    >
                                        <lock-off />
                                    </kicon>
                                </el-button>
                            </template>
                        </el-table-column>

                        <el-table-column column-key="disable" class-name="row-action">
                            <template #default="scope">
                                <el-switch
                                    v-if="!scope.row.missingSource"
                                    size="small"
                                    :active-text="$t('enabled')"
                                    :model-value="!scope.row.disabled"
                                    @change="setDisabled(scope.row, $event)"
                                    class="switch-text"
                                    :active-action-icon="Check"
                                />
                                <el-tooltip v-else :content="'flow source not found'" effect="light">
                                    <AlertCircle class="trigger-issue-icon" />
                                </el-tooltip>
                            </template>
                        </el-table-column>
                    </select-table>
                </template>
            </data-table>

            <el-dialog v-model="triggerToUnlock" destroy-on-close :append-to-body="true">
                <template #header>
                    <span v-html="$t('unlock trigger.confirmation')" />
                </template>
                {{ $t("unlock trigger.warning") }}
                <template #footer>
                    <el-button :icon="LockOff" @click="unlock" type="primary">
                        {{ $t('unlock trigger.button') }}
                    </el-button>
                </template>
            </el-dialog>
        </div>
    </section>
</template>
<script setup>
    import LockOff from "vue-material-design-icons/LockOff.vue";
    import Kicon from "../Kicon.vue";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import TopNavBar from "../layout/TopNavBar.vue";
    import Check from "vue-material-design-icons/Check.vue";
    import AlertCircle from "vue-material-design-icons/AlertCircle.vue";
    import SelectTable from "../layout/SelectTable.vue";
    import BulkSelect from "../layout/BulkSelect.vue";
</script>
<script>
    import NamespaceSelect from "../namespace/NamespaceSelect.vue";
    import RouteContext from "../../mixins/routeContext";
    import RestoreUrl from "../../mixins/restoreUrl";
    import SearchField from "../layout/SearchField.vue";
    import DataTable from "../layout/DataTable.vue";
    import DataTableActions from "../../mixins/dataTableActions";
    import MarkdownTooltip from "../layout/MarkdownTooltip.vue";
    import RefreshButton from "../layout/RefreshButton.vue";
    import DateAgo from "../layout/DateAgo.vue";
    import Id from "../Id.vue";
    import Status from "../Status.vue";
    import {mapState} from "vuex";
    import SelectTableActions from "../../mixins/selectTableActions";

    export default {
        mixins: [RouteContext, RestoreUrl, DataTableActions, SelectTableActions],
        components: {
            RefreshButton,
            MarkdownTooltip,
            DataTable,
            SearchField,
            NamespaceSelect,
            DateAgo,
            Status,
            Id,
        },
        data() {
            return {
                triggers: undefined,
                total: undefined,
                triggerToUnlock: undefined,
                state: undefined,
                states: [
                    {label: this.$t("triggers_state.options.enabled"), value: "ENABLED"},
                    {label: this.$t("triggers_state.options.disabled"), value: "DISABLED"}
                ],
                selection: null
            };
        },
        methods: {
            onSelectionChange(selection) {
                this.selection = selection;
            },
            loadData(callback) {
                this.$store.dispatch("trigger/search", {
                    namespace: this.$route.query.namespace,
                    q: this.$route.query.q,
                    size: parseInt(this.$route.query.size || 25),
                    page: parseInt(this.$route.query.page || 1),
                    sort: this.$route.query.sort || "triggerId:asc"
                }).then(triggersData => {
                    this.triggers = triggersData.results;
                    this.total = triggersData.total;
                    if (callback) {
                        callback();
                    }
                });
            },
            async unlock() {
                const namespace = this.triggerToUnlock.namespace;
                const flowId = this.triggerToUnlock.flowId;
                const triggerId = this.triggerToUnlock.triggerId;
                const unlockedTrigger = await this.$store.dispatch("trigger/unlock", {
                    namespace: namespace,
                    flowId: flowId,
                    triggerId: triggerId
                });

                this.$message({
                    message: this.$t("unlock trigger.success"),
                    type: "success"
                });

                const triggerIdx = this.triggers.findIndex(trigger => trigger.namespace === namespace && trigger.flowId === flowId && trigger.triggerId === triggerId);
                if (triggerIdx !== -1) {
                    this.triggers[triggerIdx] = unlockedTrigger;
                }

                this.triggerToUnlock = undefined;
            },
            setDisabled(trigger, value) {
                if (trigger.codeDisabled) {
                    this.$message({
                        message: this.$t("triggerflow disabled"),
                        type: "error",
                        showClose: true,
                        duration: 1500
                    });
                    return;
                }
                this.$store.dispatch("trigger/update", {...trigger, disabled: !value})
                    .then(_ => {
                        this.loadData();
                    })
            }
        },
        computed: {
            ...mapState("auth", ["user"]),
            routeInfo() {
                return {
                    title: this.$t("triggers")
                }
            },
            triggersMerged() {
                const all = this.triggers.map(triggers => {
                    return {
                        ...triggers?.abstractTrigger,
                        ...triggers.triggerContext,
                        codeDisabled: triggers?.abstractTrigger?.disabled,
                        // if we have no abstract trigger, it means that flow or trigger definition hasn't been found
                        missingSource: !triggers.abstractTrigger
                    }
                })

                if(!this.state) return all;

                const disabled = this.state === "DISABLED" ? true : false;
                return all.filter(trigger => trigger.disabled === disabled);
            }
        }
    };
</script>
<style>
    .trigger-issue-icon{
        color: var(--bs-warning);
        font-size: 1.4em;
    }
</style>