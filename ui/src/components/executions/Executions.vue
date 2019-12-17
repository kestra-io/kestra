<template>
    <div>
        <b-row>
            <b-col>
                <h1 class="text-capitalize">
                    <router-link to="/flows">{{$t('flows')}}</router-link>
                    &gt; {{$t('executions')}}
                </h1>
            </b-col>
        </b-row>
        <hr />
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
            <template v-slot:cell(state.current)="row">
                <div class="status-wrapper">
                    <status class="status" :status="row.item.state.current" />
                </div>
            </template>
            <template v-slot:cell(flowId)="row">
                <router-link
                    :to="{name: 'flowsEdit', params: {namespace: row.item.namespace, id: row.item.flowId}}"
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
            <b-col offset-md="8" sm="5" md class="my-1">
                <b-form-group
                    :label="$t('Per page')"
                    label-cols-sm="6"
                    label-cols-md="4"
                    label-cols-lg="3"
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
            <b-col sm="6" xs="12" md="2">
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
        <bottom-line>
            <ul class="navbar-nav ml-auto">
                <li v-if="$route.name === 'executions'" class="nav-item">
                    <b-button id="add-flow" @click="triggerExecution">
                        <b-tooltip target="add-flow" triggers="hover">{{$t('trigger execution')}}</b-tooltip>
                        <span class="text-capitalize">{{$t('create')}}</span>
                        <plus />
                    </b-button>
                </li>
            </ul>
        </bottom-line>
    </div>
</template>

<script>
import { mapState } from "vuex";
import BottomLine from "../layout/BottomLine";
import Eye from "vue-material-design-icons/Eye";
import Plus from "vue-material-design-icons/Plus";
import Status from "../Status";
export default {
    components: { BottomLine, Status, Eye, Plus },
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
        fields() {
            const title = title => {
                return this.$t(title).capitalize();
            };
            return [
                {
                    key: "id",
                    label: title("id"),
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
            if (this.$route.params.flowId) {
                setTimeout(() => {
                    this.$store.dispatch(
                        "execution/loadExecutions",
                        this.$route.params
                    );
                });
            } else {
                setTimeout(() => {
                    this.$store.dispatch("execution/findExecutions", {
                        size: this.size,
                        page: this.page
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
