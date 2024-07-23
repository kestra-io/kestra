<template>
    <div v-if="execution && outputs">
        <collapse>
            <el-form-item>
                <search-field />
            </el-form-item>
            <el-form-item>
                <el-select
                    filterable
                    clearable
                    :persistent="false"
                    v-model="filter"
                    @input="onSearch"
                    @clear="onClear"
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
                <el-tooltip :content="$t('eval.tooltip')" :persistent="false" transition="" :hide-after="0" effect="light">
                    <el-button :disabled="!filter" @click="isModalOpen = !isModalOpen">
                        {{ $t("eval.title") }}
                    </el-button>
                </el-tooltip>
            </el-form-item>
        </collapse>

        <drawer
            v-if="isModalOpen"
            v-model="isModalOpen"
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

            <editor class="mb-2" ref="editorContainer" :full-height="false" :input="true" :navbar="false" @save="onDebugExpression(filter, $event)" model-value="" />
            <editor v-if="debugExpression" :read-only="true" :full-height="false" :navbar="false" :minimap="false" :model-value="debugExpression" :lang="isJson ? 'json' : ''" />
            <el-alert type="error" v-if="debugError" :closable="false">
                <p><strong>{{ debugError }}</strong></p>
                <pre class="mb-0">{{ debugStackTrace }}</pre>
            </el-alert>
        </drawer>

        <el-table
            :data="outputsPaginated"
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

            <el-table-column prop="value" sortable :label="$t('each value')">
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
                    <sub-flow-link class="ms-2" v-if="scope.row.key == 'executionId'" :execution-id="scope.row.output" />
                </template>
            </el-table-column>
        </el-table>
        <pagination :total="outputs.length" :page="page" :size="size" @page-changed="onPageChanged" />
    </div>
</template>
<script>
    import {mapState} from "vuex";
    import VarValue from "./VarValue.vue";
    import Utils from "../../utils/utils";
    import Editor from "../../components/inputs/Editor.vue";
    import Collapse from "../layout/Collapse.vue";
    import Pagination from "../layout/Pagination.vue";
    import {apiUrl} from "override/utils/route";
    import SubFlowLink from "../flows/SubFlowLink.vue";
    import Drawer from "../Drawer.vue";
    import SearchField from "../layout/SearchField.vue";

    export default {
        components: {
            SubFlowLink,
            Pagination,
            VarValue,
            Editor,
            Collapse,
            Drawer,
            SearchField
        },
        data() {
            return {
                filter: undefined,
                debugExpression: "",
                isJson: false,
                debugError: "",
                debugStackTrace: "",
                isModalOpen: false,
                size: this.$route.query.size ? parseInt(this.$route.query.size) : 25,
                page: this.$route.query.page ? parseInt(this.$route.query.page) : 1,
                isPreviewOpen: false,
                selectedPreview: null
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
            onClear() {
                this.filter = undefined;
            },
            onDebugExpression(taskRunId, expression) {
                this.$http.post(`${apiUrl(this.$store)}/executions/${this.execution.id}/eval/${taskRunId}`, expression, {
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
            },
            onPageChanged(item) {
                this.size = item.size;
                this.page = item.page;

                this.$router.push({
                    query: {
                        ...this.$route.query,
                        size: item.size,
                        page: item.page
                    }
                });
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
            filteredOutputs() {
                return (this.execution.taskRunList || [])
                    .filter((taskRun) => this.filter === undefined || taskRun.id === this.filter)
                    .filter((taskRun) => this.$route.query.q === undefined || (JSON.stringify(taskRun.outputs) || "").indexOf(this.$route.query.q) !== -1)
            },
            outputs() {
                const outputs = [];
                for (const taskRun of this.filteredOutputs) {
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

                return outputs;
            },
            outputsPaginated() {
                return this.outputs.slice((this.page-1)*this.size, this.page*this.size)
            }
        }
    };
</script>
