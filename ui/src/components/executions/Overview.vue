<template>
    <div v-if="execution">
        <el-row class="mb-3">
            <el-col :span="12" class="crud-align">
                <crud type="CREATE" permission="EXECUTION" :detail="{executionId: execution.id}" />
            </el-col>
            <el-col :span="12" class="d-flex gap-2 justify-content-end">
                <set-labels :execution="execution" />
                <restart is-replay :execution="execution" class="ms-0" @follow="forwardEvent('follow', $event)" />
                <restart :execution="execution" class="ms-0" @follow="forwardEvent('follow', $event)" />
                <resume :execution="execution" class="ms-0" />
                <kill :execution="execution" class="ms-0" />
                <status :status="execution.state.current" class="ms-0" />
            </el-col>
        </el-row>

        <el-table stripe table-layout="auto" fixed :data="items" :show-header="false" class="mb-0">
            <el-table-column prop="key" :label="$t('key')" />

            <el-table-column prop="value" :label="$t('value')">
                <template #default="scope">
                    <router-link
                        v-if="scope.row.link"
                        :to="{name: 'executions/update', params: scope.row.link}"
                    >
                        {{ scope.row.value }}
                    </router-link>
                    <span v-else-if="scope.row.date">
                        <date-ago :date="scope.row.value" />
                    </span>
                    <span v-else-if="scope.row.duration">
                        <duration :histories="scope.row.value" />
                    </span>
                    <span v-else-if="scope.row.key === $t('labels')">
                        <labels :labels="scope.row.value" :filter-enabled="false" />
                    </span>
                    <span v-else>
                        <span v-if="scope.row.key === $t('revision')">
                            <router-link
                                :to="{name: 'flows/update', params: {id: $route.params.flowId, namespace: $route.params.namespace, tab: 'revisions'}, query: {revisionRight: scope.row.value}}"
                            >{{ scope.row.value }}</router-link>
                        </span>
                        <span v-else>{{ scope.row.value }}</span>
                    </span>
                </template>
            </el-table-column>
        </el-table>

        <div v-if="execution.trigger" class="mt-4">
            <h5>{{ $t("trigger") }}</h5>
            <vars :execution="execution" :data="triggerVariables" />
        </div>

        <div v-if="execution.inputs" class="mt-4">
            <h5>{{ $t("inputs") }}</h5>
            <vars :execution="execution" :data="inputs" />
        </div>

        <div v-if="execution.variables" class="mt-4">
            <h5>{{ $t("variables") }}</h5>
            <vars :execution="execution" :data="execution.variables" />
        </div>

        <div v-if="execution.outputs" class="mt-4">
            <h5>{{ $t("outputs") }}</h5>
            <vars :execution="execution" :data="execution.outputs" />
        </div>
    </div>
</template>
<script>
    import {mapState} from "vuex";
    import Status from "../Status.vue";
    import Vars from "./Vars.vue";
    import SetLabels from "./SetLabels.vue";
    import Restart from "./Restart.vue";
    import Resume from "./Resume.vue";
    import Kill from "./Kill.vue";
    import State from "../../utils/state";
    import DateAgo from "../layout/DateAgo.vue";
    import Crud from "override/components/auth/Crud.vue";
    import Duration from "../layout/Duration.vue";
    import Labels from "../layout/Labels.vue"
    import {toRaw} from "vue";

    export default {
        components: {
            Duration,
            Status,
            SetLabels,
            Restart,
            Vars,
            Resume,
            Kill,
            DateAgo,
            Labels,
            Crud
        },
        emits: ["follow"],
        methods: {
            forwardEvent(type, event) {
                this.$emit(type, event);
            },
            stop() {
                if (!this.execution || State.isRunning(this.execution.state.current)) {
                    return new Date().toISOString(true)
                } else {
                    return this.execution.state.histories[this.execution.state.histories.length - 1].date;
                }
            }
        },
        watch: {
            $route(newValue, oldValue) {
                if (oldValue.name === newValue.name && this.execution.id !== this.$route.params.id) {
                    this.$store.dispatch(
                        "execution/loadExecution",
                        this.$route.params
                    );
                }
            }
        },
        computed: {
            ...mapState("flow", ["flow"]),
            ...mapState("execution", ["execution"]),
            items() {
                if (!this.execution) {
                    return []
                }
                const stepCount = this.execution.taskRunList
                    ? this.execution.taskRunList.length
                    : 0;

                let ret = [
                    {key: this.$t("namespace"), value: this.execution.namespace},
                    {key: this.$t("flow"), value: this.execution.flowId},
                    {
                        key: this.$t("revision"),
                        value: this.execution.flowRevision
                    },
                    {key: this.$t("labels"), value: this.execution.labels},
                    {key: this.$t("created date"), value: this.execution.state.histories[0].date, date: true},
                    {key: this.$t("updated date"), value: this.stop(), date: true},
                    {key: this.$t("duration"), value: this.execution.state.histories, duration: true},
                    {key: this.$t("steps"), value: stepCount},
                    {key: this.$t("attempt"), value: this.execution.metadata.attemptNumber},
                    {key: this.$t("originalCreatedDate"), value: this.execution.metadata.originalCreatedDate, date: true},
                ];

                if (this.execution.parentId) {
                    ret.push({
                        key: this.$t("parent execution"),
                        value: this.execution.parentId,
                        link: {
                            flowId: this.execution.flowId,
                            id: this.execution.parentId,
                            namespace: this.execution.namespace
                        }
                    });
                }

                if (this.execution.originalId && this.execution.originalId !== this.execution.id) {
                    ret.push({
                        key: this.$t("original execution"),
                        value: this.execution.originalId,
                        link: {
                            flowId: this.execution.flowId,
                            id: this.execution.originalId,
                            namespace: this.execution.namespace
                        }
                    });
                }

                return ret;
            },
            inputs() {
                if (!this.flow) {
                    return []
                }

                let inputs = toRaw(this.execution.inputs);
                Object.keys(inputs).forEach(key => {
                    this.flow.inputs.forEach(input => {
                        if (key === input.name && input.type === "SECRET") {
                            inputs[key] = "******";
                        }
                    })
                })
                return inputs;
            },
            // This is used to display correctly trigger variables
            triggerVariables() {
                let trigger = this.execution.trigger
                trigger["trigger"] = this.execution.trigger.variables
                delete trigger["variables"]

                return trigger
            }
        },
    };
</script>
<style scoped lang="scss">
    .crud-align {
        display: flex;
        align-items: center;
    }
</style>
