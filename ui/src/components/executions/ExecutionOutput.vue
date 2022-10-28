<template>
    <div v-if="execution && outputs">
        <b-navbar toggleable="lg" type="light" variant="light">
            <b-navbar-toggle target="nav-collapse" />
            <b-collapse id="nav-collapse" is-nav>
                <b-nav-form>
                    <v-select
                        v-model="filter"
                        :reduce="option => option.value"
                        @input="onSearch"
                        :options="selectOptions"
                        :placeholder="$t('display output for specific task') + '...'"
                    />
                    <span id="debug-btn-wrapper">
                        <b-btn
                            :disabled="!filter"
                            v-b-modal="`debug-expression-modal`"
                        >
                            {{ $t("eval.title") }}
                        </b-btn>
                    </span>
                    <b-tooltip
                        v-if="!filter"
                        placement="right"
                        target="debug-btn-wrapper"
                    >
                        {{ $t("eval.tooltip") }}
                    </b-tooltip>
                </b-nav-form>
            </b-collapse>
        </b-navbar>

        <b-table
            :responsive="true"
            striped
            hover
            :fields="fields"
            :items="outputs"
            class="mb-0"
            show-empty
        >
            <template #empty>
                <span class="text-muted">{{ $t('no result') }}</span>
            </template>

            <template #cell(key)="row">
                <code>{{ row.item.key }}</code>
            </template>

            <template #cell(value)="row">
                <var>{{ row.item.value }}</var>
            </template>

            <template #cell(output)="row">
                <var-value :execution="execution" :value="row.item.output" />
            </template>
        </b-table>

        <b-modal
            hide-backdrop
            id="debug-expression-modal"
            modal-class="right"
            size="xl"
        >
            <template #modal-header>
                <h5>{{ $t("eval.title") }}</h5>
            </template>

            <template>
                <editor class="mb-2" ref="editorContainer" :full-height="false" @onSave="onDebugExpression(filter, $event)" :input="true" :navbar="false" value="" />
                <editor v-if="debugExpression" :read-only="true" :full-height="false" :navbar="false" :minimap="false" :value="debugExpression" :lang="isJson ? 'json' : ''" />
                <b-alert class="debug-error" variant="danger" show v-if="debugError">
                    <p><strong>{{ debugError }}</strong></p>
                    <pre class="mb-0">{{ debugStackTrace }}</pre>
                </b-alert>
            </template>

            <template #modal-footer>
                <b-button
                    variant="secondary"
                    @click="onDebugExpression(filter, $refs.editorContainer.editor.getValue())"
                >
                    {{ $t("eval.title") }}
                </b-button>
            </template>
        </b-modal>
    </div>
</template>
<script>
    import {mapState} from "vuex";
    import VarValue from "./VarValue";
    import Utils from "../../utils/utils";
    import Editor from "../../components/inputs/Editor";
    import Vue from "vue"

    export default {
        components: {
            VarValue,
            Editor,
        },
        data() {
            return {
                filter: "",
                debugExpression: "",
                isJson: false,
                debugError: "",
                debugStackTrace: "",
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
                Vue.axios.post(`/api/v1/executions/${this.execution.id}/eval/${taskRunId}`, expression, {
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
            fields() {
                return [
                    {
                        key: "task",
                        label: this.$t("task")
                    },
                    {
                        key: "value",
                        label: this.$t("value")
                    },
                    {
                        key: "key",
                        label: this.$t("name")
                    },
                    {
                        key: "output",
                        label: this.$t("output")
                    }
                ];
            },
            outputs() {
                const outputs = [];
                for (const taskRun of this.execution.taskRunList || []) {
                    const token = taskRun.id;
                    if (!this.filter || token === this.filter) {
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
