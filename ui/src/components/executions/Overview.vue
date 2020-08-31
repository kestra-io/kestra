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
                    :to="{name: 'executionEdit', params: row.item.link}"
                >{{row.item.value}}</router-link>
                <span v-else>{{row.item.value}}</span>
            </template>
        </b-table>
        <div v-if="execution.inputs">
            <h5>{{$t('inputs')}}</h5>
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
                    <b-link
                        v-if="isFile(row.item)"
                        class="btn btn-primary"
                        target="_blank"
                        :href="itemUrl(row.item.value)"
                    >{{$t('download')}}</b-link>
                    <span v-else>{{row.item.value}}</span>
                </template>
            </b-table>
        </div>

        <div v-if="variables.length > 0" class="mt-4">
            <h5>{{$t('variables')}}</h5>
            <b-table
                responsive="xl"
                striped
                hover
                bordered
                :items="this.variables"
                :fields="fields"
                class="mb-0"
            >
                <template v-slot:cell(key)="row">
                    <code>{{row.item.key}}</code>
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
        isFile(item) {
            return `${this.execution.inputs[item.key]}`.startsWith("kestra:///")
        },
        itemUrl(value) {
            return `${apiRoot}executions/${this.execution.id}/file?path=${value}`;
        },
        restart() {
            this.$emit("follow");
        },
        flat(object) {
            return Object.assign({}, ...function _flatten(child, path = []) {
                return [].concat(...Object.keys(child).map(key => typeof child[key] === 'object'
                    ? _flatten(child[key], path.concat([key]))
                    : ({ [path.concat([key]).join(".")] : child[key] })
                ));
            }(object));
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
                { key: this.$t("created date"), value: this.$moment(startTs).format('LLLL') },
                { key: this.$t("updated date"), value: this.$moment(stopTs).format('LLLL') },
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
        },
        variables() {
            const variables = [];

            if (this.execution.variables !== undefined) {
                const flat = this.flat(this.execution.variables);
                for (const key in flat) {

                    let date = this.$moment(flat[key]);

                    if (date.isValid()) {
                        variables.push({key, value: date.format('LLLL')});
                    } else {
                        variables.push({key, value: flat[key]});
                    }
                }
            }

            return variables;
        }
    },

};
</script>
<style scoped lang="scss">
/deep/ thead {
    display: none;
}
</style>
