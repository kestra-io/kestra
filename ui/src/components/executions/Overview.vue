<template>
    <div v-if="execution">
        <b-row class="mb-3 text-right">
            <b-col>
                <restart :execution="execution" @restart="restart" />

                <status :status="execution.state.current" />
            </b-col>
        </b-row>
        <b-table responsive="xl" striped hover bordered :items="items" class="mb-0">
            <template v-slot:cell(value)="row">
                <router-link
                    v-if="row.item.link"
                    :to="{name: 'execution', params: row.item.link}"
                >{{row.item.value}}</router-link>
                <span v-else>{{row.item.value}}</span>
            </template>
        </b-table>
        <div v-if="execution.inputs">
            <hr />
            <h2>{{$t('inputs') | cap}}</h2>
            <b-table
                responsive="xl"
                striped
                hover
                bordered
                :items="inputs"
                :fields="fields"
                class="mb-0"
            >
                <template v-slot:cell(value)="row">
                    <span
                        v-if="['string', 'optional', 'float', 'int', 'instant'].includes(row.item.key)"
                    >{{row.item.value}}</span>
                    <b-link
                        class="btn btn-primary"
                        v-if="['optionalFile', 'file'].includes(row.item.key)"
                        target="_blank"
                        :href="itemUrl(row.item.value)"
                    >{{$t('download') | cap}}</b-link>
                </template>
            </b-table>
        </div>
    </div>
</template>
<script>
import { mapState } from "vuex";
import Status from "../Status";
import humanizeDuration from "humanize-duration";
import { apiRoot } from "../../http";
import Restart from "./Restart";

const ts = date => new Date(date).getTime();

export default {
    components: {
        Status,
        Restart
    },
    methods: {
        itemUrl(value) {
            return `${apiRoot}executions/${this.execution.id}/file?filePath=${value.uri}&type=${value.type}`;
        },
        restart() {
            this.$emit("follow");
        }
    },
    watch: {
        $route() {
            if (this.execution.id !== this.$route.params.id) {
                this.$store.dispatch(
                    "execution/loadExecution",
                    this.$route.params
                );
            }
        }
    },
    computed: {
        ...mapState("execution", ["execution"]),
        fields() {
            return [
                {
                    key: "key",
                    label: this.$t("name")
                },
                {
                    key: "value",
                    label: this.$t("value")
                }
            ];
        },
        items() {
            const startTs = this.execution.state.histories[0].date;
            const stopTs = this.execution.state.histories[
                this.execution.state.histories.length - 1
            ].date;
            const delta = ts(stopTs) - ts(startTs);
            const duration = this.$moment.duration(delta);
            const humanDuration = humanizeDuration(duration);
            const stepCount = this.execution.taskRunList
                ? this.execution.taskRunList.length
                : 0;

            let ret = [
                { key: this.$t("namespace"), value: this.execution.namespace },
                { key: this.$t("flow"), value: this.execution.flowId },
                {
                    key: this.$t("revision"),
                    value: this.execution.flowRevision
                },
                { key: this.$t("created date"), value: startTs },
                { key: this.$t("updated date"), value: stopTs },
                { key: this.$t("duration"), value: humanDuration },
                { key: this.$t("steps"), value: stepCount }
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

            return ret;
        },
        inputs() {
            const inputs = [];
            for (const key in this.execution.inputs) {
                inputs.push({ key, value: this.execution.inputs[key] });
            }
            return inputs;
        }
    }
};
</script>
<style scoped lang="scss">
/deep/ thead {
    display: none;
}
</style>