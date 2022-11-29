<template>
    <div v-if="execution && outputs">
        <collapse>
            <el-form-item>
                <el-select
                    filterable
                    clearable
                    :persistent="false"
                    v-model="filter"
                    @input="onSearch"
                    :placeholder="$t('display output for specific task') + '...'"
                >
                    <el-option
                        v-for="item in selectOptions"
                        :key="item.value"
                        :label="item.label"
                        :value="item.value"
                    />
                </el-select>
            </el-form-item>
            <el-form-item>
                <el-tooltip :content="$t('eval.tooltip')">
                    <el-button :disabled="!filter" @click="isModalOpen = !isModalOpen">
                        {{ $t("eval.title") }}
                    </el-button>
                </el-tooltip>
            </el-form-item>
        </collapse>

        <el-drawer
            v-if="isModalOpen"
            v-model="isModalOpen"
            destroy-on-close
            lock-scroll
            :append-to-body="true"
            :title="$t('eval.title')"
        >
            <template #footer>
                <el-button
                    type="primary"
                    @click="onDebugExpression(filter, $refs.editorContainer.editor.getValue())"
                >
                    {{ $t("eval.title") }}
                </el-button>
            </template>

            <editor class="mb-2" ref="editorContainer" :full-height="false" @save="onDebugExpression(filter, $event)" :input="true" :navbar="false" model-value="" />
            <editor v-if="debugExpression" :read-only="true" :full-height="false" :navbar="false" :minimap="false" :model-value="debugExpression" :lang="isJson ? 'json' : ''" />
            <el-alert class="debug-error" type="danger" show-icon v-if="debugError" :closable="false">
                <p><strong>{{ debugError }}</strong></p>
                <pre class="mb-0">{{ debugStackTrace }}</pre>
            </el-alert>
        </el-drawer>

        <el-table
            :data="outputs"
            ref="table"
            :default-sort="{prop: 'state.startDate', order: 'descending'}"
            stripe
            table-layout="auto"
            fixed
        >
            <el-table-column prop="task" sortable :label="$t('task')">
                <template #default="scope">
                    <var>{{ scope.row.task }}</var>
                </template>
            </el-table-column>

            <el-table-column prop="value" sortable :label="$t('value')">
                <template #default="scope">
                    <var>{{ scope.row.value }}</var>
                </template>
            </el-table-column>

            <el-table-column prop="key" sortable :label="$t('name')">
                <template #default="scope">
                    <code>{{ scope.row.key }}</code>
                </template>
            </el-table-column>

            <el-table-column prop="task" :sort-orders="['ascending', 'descending']" :label="$t('output')">
                <template #default="scope">
                    <var-value :execution="execution" :value="scope.row.output" />
                </template>
            </el-table-column>
        </el-table>

    </div>
</template>
<script>
    import {mapState} from "vuex";
    import VarValue from "./VarValue";
    import Utils from "../../utils/utils";
    import Editor from "../../components/inputs/Editor";
    import Collapse from "../layout/Collapse.vue";

    export default {
        components: {
            VarValue,
            Editor,
            Collapse,
        },
        data() {
            return {
                filter: undefined,
                debugExpression: "",
                isJson: false,
                debugError: "",
                debugStackTrace: "",
                isModalOpen: false,
            };
        },
        created() {
            if (this.$route.query.search) {
                this.filter = this.$route.query.search || ""
            }
        },
        watch: {
            $route() {
                if (this.$route.query.search !== this.filter) {
                    this.filter = this.$route.query.search || "";
                }
            }
        },
        methods: {
            onSearch() {
                if (this.filter && this.$route.query.search !== this.filter) {
                    const newRoute = {query: {...this.$route.query}};
                    newRoute.query.search = this.filter;
                    this.$router.push(newRoute);
                } else {
                    const newRoute = {query: {...this.$route.query}};
                    delete newRoute.query.search;
                    this.$router.push(newRoute);
                }
            },
            onDebugExpression(taskRunId, expression) {
                this.$http.post(`/api/v1/executions/${this.execution.id}/eval/${taskRunId}`, expression, {
                    headers: {
                        "Content-type": "text/plain",
                    }
                }).then(response => {
                    try {
                        this.debugExpression = JSON.stringify(JSON.parse(response.data.result), "  ", 2);
                        this.isJson = true;
                    } catch (e) {
                        this.debugExpression = response.data.result;
                        this.isJson = false;
                    }
                    this.debugError = response.data.error;
                    this.debugStackTrace = response.data.stackTrace;
                })
            }
        },
        computed: {
            ...mapState("execution", ["execution"]),
            selectOptions() {
                const options = {};
                for (const taskRun of this.execution.taskRunList || []) {
                    options[taskRun.id] = {
                        label: taskRun.taskId + (taskRun.value ? ` - ${taskRun.value}`: ""),
                        value: taskRun.id
                    }
                }

                return Object.values(options);
            },
            outputs() {
                const outputs = [];
                for (const taskRun of this.execution.taskRunList || []) {
                    const token = taskRun.id;
                    if (this.filter === undefined || token === this.filter) {
                        Utils.executionVars(taskRun.outputs).forEach(output => {
                            const item = {
                                key: output.key,
                                output: output.value,
                                task: taskRun.taskId,
                                value: taskRun.value
                            };

                            outputs.push(item);
                        })
                    }
                }
                return outputs;
            }
        }
    };
</script>
