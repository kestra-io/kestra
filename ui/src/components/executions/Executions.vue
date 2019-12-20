<template>
    <div>
        <b-table responsive="xl" striped hover :items="executions" :fields="fields">
            <template v-slot:cell(details)="row">
                <router-link
                    class="btn btn-default"
                    :to="`/execution/${row.item.namespace}/${row.item.flowId}/${row.item.id}`"
                >
                    <b-button size="sm">
                        <eye id="edit-action" />
                    </b-button>
                </router-link>
            </template>
            <template v-slot:cell(date)="row">
                <div
                    class="status-wrapper"
                >{{row.item.state.histories[0].date | date('YYYY/MM/DD HH:mm:ss')}}</div>
            </template>
            <template v-slot:cell(state.current)="row">
                <div class="status-wrapper">
                    <status class="status" :status="row.item.state.current" />
                </div>
            </template>
            <template v-slot:cell(flowId)="row">
                <router-link
                    :to="{name: 'flow', params: {namespace: row.item.namespace, id: row.item.flowId}}"
                >{{row.item.flowId}}</router-link>
            </template>
            <template v-slot:cell(namespace)="row">
                <router-link
                    :to="{name: 'flows', query: {namespace: row.item.namespace}}"
                >{{row.item.namespace}}</router-link>
            </template>
            <template v-slot:cell(id)="row">
                <router-link :to="{name: 'execution', params: row.item}">{{row.item.id}}</router-link>
            </template>
        </b-table>
        <b-row>
            <b-col offset-md="4" sm="5" md="4">
                <b-form-group
                    :label="$t('Per page')"
                    label-cols-sm="6"
                    label-cols-md="8"
                    label-cols-lg="6"
                    label-align-sm="right"
                    label-size="sm"
                    label-for="sizeSelect"
                    class="mb-0"
                >
                    <b-form-select
                        v-model="size"
                        @change="loadExecutions"
                        id="sizeSelect"
                        size="sm"
                        :options="pageOptions"
                    ></b-form-select>
                </b-form-group>
            </b-col>
            <b-col sm="6" xs="12" md="4">
                <b-pagination
                    @change="loadExecutions"
                    v-model="page"
                    :total-rows="total"
                    :per-page="size"
                    align="fill"
                    size="sm"
                    class="my-0"
                ></b-pagination>
            </b-col>
        </b-row>
    </div>
</template>

<script>
import { mapState } from "vuex";
import Eye from "vue-material-design-icons/Eye";
import Status from "../Status";
import RouteContext from "../../mixins/routeContext";

export default {
    mixins: [RouteContext],
    components: { Status, Eye },
    data() {
        return {
            file: undefined,
            page: 1,
            size: 5,
            pageOptions: [5, 10, 25, 50, 100]
        };
    },
    created() {
        this.loadExecutions();
    },
    computed: {
        ...mapState("execution", ["executions", "total"]),
        routeInfo() {
            return {
                title: this.$t("search"),
                breadcrumb: [
                    { label: "executions", link: { name: "executionRaw" } }
                ]
            };
        },
        fields() {
            const title = title => {
                return this.$t(title).capitalize();
            };
            return [
                {
                    key: "date",
                    label: title("create date"),
                    class: "text-center"
                },
                {
                    key: "namespace",
                    label: title("namespace"),
                    class: "text-center"
                },
                {
                    key: "flowId",
                    label: title("flow"),
                    class: "text-center"
                },
                {
                    key: "id",
                    label: title("id"),
                    class: "text-center"
                },
                {
                    key: "state.current",
                    label: title("state"),
                    class: "text-center"
                },
                {
                    key: "details",
                    label: title("details"),
                    class: "text-center"
                }
            ];
        }
    },
    watch: {
        $route() {
            this.loadExecutions();
        }
    },
    methods: {
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
        loadExecutions() {
            //setTimeout is for pagination settings are properly updated
            if (this.$route.params.namespace) {
                setTimeout(() => {
                    this.$store.dispatch("execution/loadExecutions", {
                        namespace: this.$route.params.namespace,
                        flowId: this.$route.params.id,
                        size: this.size,
                        page: this.page
                    });
                });
            } else {
                setTimeout(() => {
                    this.$store.dispatch("execution/findExecutions", {
                        size: this.size,
                        page: this.page,
                        q: "*"
                    });
                });
            }
        }
    }
};
</script>
<style scoped>
.upload-file-wrapper {
    margin-right: 15px;
}
.status-wrapper {
    padding-top: 10px;
}
</style>
