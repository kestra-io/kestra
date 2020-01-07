<template>
    <div>
        <data-table @onPageChanged="loadExecutions" ref="dataTable" :total="total">
            <template v-slot:table>
                <b-table
                    responsive="xl"
                    striped
                    hover
                    bordered
                    :items="executions"
                    :fields="fields"
                    @row-dblclicked="onRowDoubleClick"
                >
                    <template v-slot:cell(details)="row">
                        <router-link :to="{name: 'execution', params: row.item}">
                            <eye id="edit-action" />
                        </router-link>
                    </template>
                    <template
                        v-slot:cell(date)="row"
                    >{{row.item.state.histories[0].date | date('YYYY/MM/DD HH:mm:ss')}}</template>
                    <template v-slot:cell(state.current)="row">
                        <status class="status" :status="row.item.state.current" />
                    </template>
                    <template v-slot:cell(flowId)="row">
                        <router-link
                            :to="{name: 'flow', params: {namespace: row.item.namespace, id: row.item.flowId}}"
                        >{{row.item.flowId}}</router-link>
                    </template>
                    <template v-slot:cell(namespace)="row">
                        <router-link
                            :to="{name: 'flowsList', query: {namespace: row.item.namespace}}"
                        >{{row.item.namespace}}</router-link>
                    </template>
                    <template v-slot:cell(id)="row">
                        <code>{{row.item.id | id}}</code>
                    </template>
                </b-table>
            </template>
        </data-table>
    </div>
</template>

<script>
import { mapState } from "vuex";
import DataTable from "../layout/DataTable";
import Eye from "vue-material-design-icons/Eye";
import Status from "../Status";
import RouteContext from "../../mixins/routeContext";

export default {
    mixins: [RouteContext],
    components: { Status, Eye, DataTable },
    mounted() {
        this.loadExecutions(this.$refs.dataTable.pagination);
    },
    computed: {
        ...mapState("execution", ["executions", "total"]),
        routeInfo() {
            return {
                title: this.$t("executions")
            };
        },
        fields() {
            const title = title => {
                return this.$t(title).capitalize();
            };
            return [
                {
                    key: "id",
                    label: title("id")
                },
                {
                    key: "date",
                    label: title("created date")
                },
                {
                    key: "namespace",
                    label: title("namespace")
                },
                {
                    key: "flowId",
                    label: title("flow")
                },
                {
                    key: "state.current",
                    label: title("state"),
                    class: "text-center"
                },
                {
                    key: "details",
                    label: "",
                    class: "row-action"
                }
            ];
        }
    },
    watch: {
        $route() {
            this.loadExecutions(this.$refs.dataTable.pagination);
        }
    },
    methods: {
        onRowDoubleClick(item) {
            this.$router.push({ name: "execution", params: item });
        },
        triggerExecution() {
            this.$store
                .dispatch("execution/triggerExecution", this.$route.params)
                .then(response => {
                    this.$router.push({
                        name: "execution",
                        params: response.data
                    });
                    this.$bvToast.toast(this.$t("triggered").capitalize(), {
                        title: this.$t("execution").capitalize(),
                        autoHideDelay: 5000,
                        toaster: "b-toaster-top-right",
                        variant: "success"
                    });
                });
        },
        loadExecutions(pagination) {
            if (this.$route.params.namespace) {
                this.$store.dispatch("execution/loadExecutions", {
                    namespace: this.$route.params.namespace,
                    flowId: this.$route.params.id,
                    size: pagination.size,
                    page: pagination.page
                });
            } else {
                this.$store.dispatch("execution/findExecutions", {
                    size: pagination.size,
                    page: pagination.page,
                    q: "*"
                });
            }
        }
    }
};
</script>
