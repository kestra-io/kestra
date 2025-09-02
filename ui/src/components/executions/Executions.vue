<template>
    <top-nav-bar v-if="topbar" :title="routeInfo.title">
        <template #additional-right v-if="displayButtons">
            <ul>
                <template v-if="$route.name === 'executions/list'">
                    <li>
                        <template v-if="hasAnyExecute">
                            <trigger-flow />
                        </template>
                    </li>
                </template>
                <template v-if="$route.name === 'flows/update'">
                    <li>
                        <template v-if="isAllowedEdit">
                            <el-button :icon="Pencil" size="large" @click="editFlow" :disabled="isReadOnly">
                                {{ $t("edit flow") }}
                            </el-button>
                        </template>
                    </li>
                    <li>
                        <trigger-flow
                            v-if="flowStore.flow"
                            :disabled="flowStore.flow.disabled || isReadOnly"
                            :flow-id="flowStore.flow.id"
                            :namespace="flowStore.flow.namespace"
                        />
                    </li>
                </template>
            </ul>
        </template>
    </top-nav-bar>
    <section
        data-component="FILENAME_PLACEHOLDER"
        :class="{'container padding-bottom': topbar}"
        v-if="ready"
    >
        <data-table
            @page-changed="onPageChanged"
            ref="dataTable"
            :total="executionsStore.total"
            :size="pageSize"
            :page="pageNumber"
            :embed="embed"
        >
            <template #navbar v-if="isDisplayedTop">
                <KestraFilter
                    prefix="executions"
                    :language="namespace === undefined || flowId === undefined ? ExecutionFilterLanguage : FlowExecutionFilterLanguage"
                    :buttons="{
                        refresh: {shown: true, callback: refresh},
                        settings: {shown: true, charts: {shown: true, value: showChart, callback: onShowChartChange}}
                    }"
                    :properties-width="182"
                    :properties="{
                        shown: true,
                        columns: optionalColumns,
                        displayColumns,
                        storageKey: 'executions'
                    }"
                    @update-properties="updateDisplayColumns"
                />
            </template>

            <template v-if="showStatChart()" #top>
                <Sections ref="dashboardComponent" :dashboard="{id: 'default'}" :charts show-default />
            </template>

            <template #table>
                <select-table
                    ref="selectTable"
                    :data="executionsStore.executions"
                    :default-sort="{prop: 'state.startDate', order: 'descending'}"
                    table-layout="auto"
                    fixed
                    @row-dblclick="row => onRowDoubleClick(executionParams(row))"
                    @sort-change="onSort"
                    @selection-change="handleSelectionChange"
                    :selectable="!hidden?.includes('selection') && canCheck"
                    :no-data-text="$t('no_results.executions')"
                >
                    <template #select-actions>
                        <bulk-select
                            :select-all="queryBulkAction"
                            :selections="selection"
                            :total="executionsStore.total"
                            @update:select-all="toggleAllSelection"
                            @unselect="toggleAllUnselected"
                        >
                            <!-- Always visible buttons -->
                            <el-button v-if="canUpdate" :icon="StateMachine" @click="changeStatusDialogVisible = !changeStatusDialogVisible">
                                {{ $t("change state") }}
                            </el-button>
                            <el-button v-if="canUpdate" :icon="Restart" @click="restartExecutions()">
                                {{ $t("restart") }}
                            </el-button>
                            <el-button v-if="canCreate" :icon="PlayBoxMultiple" @click="isOpenReplayModal = !isOpenReplayModal">
                                {{ $t("replay") }}
                            </el-button>
                            <el-button v-if="canUpdate" :icon="StopCircleOutline" @click="killExecutions()">
                                {{ $t("kill") }}
                            </el-button>
                            <el-button v-if="canDelete" :icon="Delete" @click="deleteExecutions()">
                                {{ $t("delete") }}
                            </el-button>

                            <!-- Dropdown with additional actions -->
                            <el-dropdown>
                                <el-button>
                                    <DotsVertical />
                                </el-button>
                                <template #dropdown>
                                    <el-dropdown-menu>
                                        <el-dropdown-item v-if="canUpdate" :icon="LabelMultiple" @click=" isOpenLabelsModal = !isOpenLabelsModal">
                                            {{ $t("Set labels") }}
                                        </el-dropdown-item>
                                        <el-dropdown-item v-if="canUpdate" :icon="PlayBox" @click="resumeExecutions()">
                                            {{ $t("resume") }}
                                        </el-dropdown-item>
                                        <el-dropdown-item v-if="canUpdate" :icon="PauseBox" @click="pauseExecutions()">
                                            {{ $t("pause") }}
                                        </el-dropdown-item>
                                        <el-dropdown-item v-if="canUpdate" :icon="QueueFirstInLastOut" @click="unqueueDialogVisible = true">
                                            {{ $t("unqueue") }}
                                        </el-dropdown-item>
                                        <el-dropdown-item v-if="canUpdate" :icon="RunFast" @click="forceRunExecutions()">
                                            {{ $t("force run") }}
                                        </el-dropdown-item>
                                    </el-dropdown-menu>
                                </template>
                            </el-dropdown>
                        </bulk-select>
                        <el-dialog
                            v-if="isOpenLabelsModal"
                            v-model="isOpenLabelsModal"
                            destroy-on-close
                            :append-to-body="true"
                            align-center
                        >
                            <template #header>
                                <h5>{{ $t("Set labels") }}</h5>
                            </template>
                            <template #footer>
                                <el-button @click="isOpenLabelsModal = false">
                                    {{ $t("cancel") }}
                                </el-button>
                                <el-button type="primary" @click="setLabels()">
                                    {{ $t("ok") }}
                                </el-button>
                            </template>
                            <el-form>
                                <el-form-item :label="$t('execution labels')">
                                    <label-input
                                        :key="executionLabels"
                                        v-model:labels="executionLabels"
                                    />
                                </el-form-item>
                            </el-form>
                        </el-dialog>
                    </template>
                    <template #default>
                        <el-table-column
                            prop="id"
                            sortable="custom"
                            :sort-orders="['ascending', 'descending']"
                            :label="$t('id')"
                        >
                            <template #default="scope">
                                <Id :value="scope.row.id" />
                            </template>
                        </el-table-column>
                        <el-table-column
                            prop="state.startDate"
                            v-if="displayColumn('state.startDate')"
                            sortable="custom"
                            :sort-orders="['ascending', 'descending']"
                            :label="$t('start date')"
                        >
                            <template #default="scope">
                                <date-ago :inverted="true" :date="scope.row.state.startDate" />
                            </template>
                        </el-table-column>
                        <el-table-column
                            prop="state.endDate"
                            v-if="displayColumn('state.endDate')"
                            sortable="custom"
                            :sort-orders="['ascending', 'descending']"
                            :label="$t('end date')"
                        >
                            <template #default="scope">
                                <date-ago :inverted="true" :date="scope.row.state.endDate" />
                            </template>
                        </el-table-column>
                        <el-table-column
                            prop="state.duration"
                            v-if="displayColumn('state.duration')"
                            sortable="custom"
                            :sort-orders="['ascending', 'descending']"
                            :label="$t('duration')"
                        >
                            <template #default="scope">
                                <span v-if="isRunning(scope.row)">
                                    {{ $filters.humanizeDuration(durationFrom(scope.row)) }}
                                </span>
                                <span v-else>
                                    {{ $filters.humanizeDuration(scope.row.state.duration) }}
                                </span>
                            </template>
                        </el-table-column>
                        <el-table-column
                            v-if="$route.name !== 'flows/update' && displayColumn('namespace')"
                            prop="namespace"
                            sortable="custom"
                            :sort-orders="['ascending', 'descending']"
                            :label="$t('namespace')"
                            :formatter="(_, __, cellValue) => $filters.invisibleSpace(cellValue)"
                        />
                        <el-table-column
                            v-if="$route.name !== 'flows/update' && displayColumn('flowId')"
                            prop="flowId"
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
                            </template>
                        </el-table-column>
                        <el-table-column v-if="displayColumn('labels')" :label="$t('labels')">
                            <template #default="scope">
                                <labels :labels="filteredLabels(scope.row.labels)" />
                            </template>
                        </el-table-column>
                        <el-table-column
                            prop="state.current"
                            v-if="displayColumn('state.current')"
                            sortable="custom"
                            :sort-orders="['ascending', 'descending']"
                            :label="$t('state')"
                        >
                            <template #default="scope">
                                <status :status="scope.row.state.current" size="small" />
                            </template>
                        </el-table-column>
                        <el-table-column
                            prop="flowRevision"
                            v-if="displayColumn('flowRevision')"
                            :label="$t('revision')"
                            class-name="shrink"
                        >
                            <template #default="scope">
                                <code class="code-text">{{ scope.row.flowRevision }}</code>
                            </template>
                        </el-table-column>
                        <el-table-column
                            prop="inputs"
                            v-if="displayColumn('inputs')"
                            :label="$t('inputs')"
                            align="center"
                        >
                            <template #default="scope">
                                <el-tooltip effect="light">
                                    <template #content>
                                        <pre class="mb-0">{{ JSON.stringify(scope.row.inputs, null, "\t") }}</pre>
                                    </template>
                                    <div>
                                        <Import v-if="scope.row.inputs" class="fs-5" />
                                    </div>
                                </el-tooltip>
                            </template>
                        </el-table-column>
                        <el-table-column
                            prop="taskRunList.taskId"
                            v-if="displayColumn('taskRunList.taskId')"
                            :label="$t('task id')"
                        >
                            <template #header="scope">
                                <el-tooltip :content="$t('taskid column details')" effect="light">
                                    {{ scope.column.label }}
                                </el-tooltip>
                            </template>
                            <template #default="scope">
                                <code class="code-text">
                                    {{ scope.row.taskRunList?.slice(-1)[0].taskId }}
                                    {{
                                        scope.row.taskRunList?.slice(-1)[0].attempts?.length > 1
                                            ? `(${scope.row.taskRunList?.slice(-1)[0].attempts.length})`
                                            : ""
                                    }}
                                </code>
                            </template>
                        </el-table-column>
                        <el-table-column
                            column-key="action"
                            class-name="row-action"
                            :label="$t('actions')"
                        >
                            <template #default="scope">
                                <router-link
                                    :to="{
                                        name: 'executions/update',
                                        params: {
                                            namespace: scope.row.namespace,
                                            flowId: scope.row.flowId,
                                            id: scope.row.id
                                        },
                                        query: {revision: scope.row.flowRevision}
                                    }"
                                >
                                    <kicon :tooltip="$t('details')" placement="left">
                                        <TextSearch />
                                    </kicon>
                                </router-link>
                            </template>
                        </el-table-column>
                    </template>
                </select-table>
            </template>
        </data-table>
    </section>

    <!-- Dialogs -->
    <el-dialog
        v-if="changeStatusDialogVisible"
        v-model="changeStatusDialogVisible"
        :id="Utils.uid()"
        destroy-on-close
        :append-to-body="true"
        align-center
    >
        <template #header>
            <h5>{{ $t("confirmation") }}</h5>
        </template>
        <template #default>
            <p v-html="changeStatusToast()" />
            <el-select :required="true" v-model="selectedStatus" :persistent="false">
                <el-option
                    v-for="item in states"
                    :key="item.code"
                    :value="item.code"
                >
                    <template #default>
                        <status size="small" :label="false" class="me-1" :status="item.code" />
                        <span v-html="item.label" />
                    </template>
                </el-option>
            </el-select>
        </template>
        <template #footer>
            <el-button @click="changeStatusDialogVisible = false">
                {{ $t('cancel') }}
            </el-button>
            <el-button type="primary" @click="changeStatus()">
                {{ $t('ok') }}
            </el-button>
        </template>
    </el-dialog>

    <el-dialog
        v-if="unqueueDialogVisible"
        v-model="unqueueDialogVisible"
        destroy-on-close
        :append-to-body="true"
    >
        <template #header>
            <h5>{{ $t("confirmation") }}</h5>
        </template>
        <template #default>
            <p v-html="$t('unqueue title multiple', {count: queryBulkAction ? executionsStore.total : selection.length})" />
            <el-select :required="true" v-model="selectedStatus" :persistent="false">
                <el-option
                    v-for="item in unQueuestates"
                    :key="item.code"
                    :value="item.code"
                >
                    <template #default>
                        <status size="small" :label="false" class="me-1" :status="item.code" />
                        <span v-html="item.label" />
                    </template>
                </el-option>
            </el-select>
        </template>
        <template #footer>
            <el-button @click="unqueueDialogVisible = false">
                {{ $t('cancel') }}
            </el-button>
            <el-button type="primary" @click="unqueueExecutions()">
                {{ $t('ok') }}
            </el-button>
        </template>
    </el-dialog>

    <el-dialog
        v-if="isOpenReplayModal"
        v-model="isOpenReplayModal"
        :id="Utils.uid()"
        destroy-on-close
        :append-to-body="true"
        align-center
    >
        <template #header>
            <h5>{{ $t("confirmation") }}</h5>
        </template>
        <template #default>
            <p v-html="changeReplayToast()" />
        </template>
        <template #footer>
            <el-button @click="isOpenReplayModal = false">
                {{ $t('cancel') }}
            </el-button>
            <el-button @click="replayExecutions(true)">
                {{ $t('replay latest revision') }}
            </el-button>
            <el-button type="primary" @click="replayExecutions(false)">
                {{ $t('ok') }}
            </el-button>
        </template>
    </el-dialog>
</template>

<script setup>
    import BulkSelect from "../layout/BulkSelect.vue";
    import SelectTable from "../layout/SelectTable.vue";
    import PlayBox from "vue-material-design-icons/PlayBox.vue";
    import PlayBoxMultiple from "vue-material-design-icons/PlayBoxMultiple.vue";
    import DotsVertical from "vue-material-design-icons/DotsVertical.vue";
    import Restart from "vue-material-design-icons/Restart.vue";
    import Delete from "vue-material-design-icons/Delete.vue";
    import StopCircleOutline from "vue-material-design-icons/StopCircleOutline.vue";
    import Pencil from "vue-material-design-icons/Pencil.vue";
    import Import from "vue-material-design-icons/Import.vue";
    import LabelMultiple from "vue-material-design-icons/LabelMultiple.vue";
    import StateMachine from "vue-material-design-icons/StateMachine.vue";
    import PauseBox from "vue-material-design-icons/PauseBox.vue";
    import KestraFilter from "../filter/KestraFilter.vue"
    import QueueFirstInLastOut from "vue-material-design-icons/QueueFirstInLastOut.vue";
    import RunFast from "vue-material-design-icons/RunFast.vue";
    import ExecutionFilterLanguage from "../../composables/monaco/languages/filters/impl/executionFilterLanguage.ts";
    import FlowExecutionFilterLanguage from "../../composables/monaco/languages/filters/impl/flowExecutionFilterLanguage.js";
    import Sections from "../dashboard/sections/Sections.vue";
</script>

<script>
    // ...existing code...
</script>

<style scoped lang="scss">
    .shadow {
        box-shadow: 0px 2px 4px 0px var(--ks-card-shadow) !important;
    }

    .padding-bottom {
        padding-bottom: 4rem;
    }
    .custom-warning {
        border: 1px solid #ffb703;
        border-radius: 7px;
        box-shadow: 1px 1px 3px 1px #ffb703;

        :deep(.el-alert__title) {
            font-size: 16px;
            color: #ffb703;
            font-weight: bold;
        }

        :deep(.el-alert__description) {
            font-size: 12px;
        }

        :deep(.el-alert__icon) {
            color: #ffb703;
        }
    }
    .code-text {
        color: var(--ks-content-primary);
    }
</style>

<style lang="scss">
    .el-message-box {
        padding: 2rem;
        max-width: initial;
        width: 500px;

        .custom-warning {
            margin: 1rem 0;
        }
    }
</style>