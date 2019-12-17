<template>
    <div>
        <b-row>
            <b-col>
                <h1 class="text-capitalize wrap">{{$t('flows')}}</h1>
            </b-col>
        </b-row>
        <hr />
        <b-row>
            <b-col sm="12" md="4">
                <namespace-selector @onNamespaceSelect="onNamespaceSelect" />
            </b-col>
        </b-row>
        <b-table responsive="xl" striped hover :items="flows" :fields="fields">
            <template v-slot:cell(actions)="row">
                <b-tooltip
                    :target="`edit-action-${row.item.id}`"
                >{{$t('edit flow') | cap}} {{row.item.id}}</b-tooltip>
                <b-tooltip
                    :target="`find-action-${row.item.id}`"
                >{{$tc('display flow {id} executions', null, row.item) | cap}}</b-tooltip>
                <b-tooltip
                    :target="`trigger-action-${row.item.id}`"
                >{{$t('trigger execution for flow') | cap }} {{row.item.id}}</b-tooltip>
                <router-link
                    class="btn btn-default"
                    :to="`/flows/edit/${row.item.namespace}/${row.item.id}`"
                >
                    <b-button :id="`edit-action-${row.item.id}`" variant="secondary" size="sm">
                        <edit :id="`edit-action-${row.item.id}`" />
                    </b-button>
                </router-link>
                <router-link
                    :id="`find-action-${row.item.id}`"
                    class="btn btn-default"
                    :to="`/executions/${row.item.namespace}/${row.item.id}`"
                >
                    <b-button variant="primary" size="sm">
                        <search />
                    </b-button>
                </router-link>
                <router-link
                    :id="`trigger-action-${row.item.id}`"
                    :to="{name: 'executionConfiguration', params: row.item}"
                >
                    <b-button variant="info" size="sm">
                        <trigger />
                    </b-button>
                </router-link>
            </template>
            <template v-slot:cell(namespace)="row">
                <a
                    href
                    @click.prevent="onNamespaceSelect(row.item.namespace)"
                >{{row.item.namespace}}</a>
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
                    label-for="perPageSelect"
                    class="mb-0"
                >
                    <b-form-select
                        v-model="perPage"
                        @change="loadFlows"
                        id="perPageSelect"
                        size="sm"
                        :options="pageOptions"
                    ></b-form-select>
                </b-form-group>
            </b-col>
            <b-col sm="6" xs="12" md="2">
                <b-pagination
                    @change="loadFlows"
                    v-model="page"
                    :total-rows="total"
                    :per-page="perPage"
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
import Trigger from "vue-material-design-icons/Cogs";
import Search from "vue-material-design-icons/Magnify";
import Edit from "vue-material-design-icons/Pencil";
import NamespaceSelector from "../namespace/Selector";

export default {
    components: {
        Trigger,
        Search,
        Edit,
        NamespaceSelector
    },
    data() {
        return {
            page: 1, //TODO put in store
            perPage: 10,
            pageOptions: [5, 10, 25, 50, 100]
        };
    },
    created() {
        this.onNamespaceSelect(this.$route.query.namespace);
    },
    watch: {
        $route() {
            this.onNamespaceSelect(this.$route.query.namespace);
        }
    },
    computed: {
        ...mapState("flow", ["flows", "total"]),
        ...mapState("namespace", ["namespace", "namespace"]),
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
                    key: "revision",
                    label: title("revision"),
                    class: "text-center"
                },
                {
                    key: "actions",
                    label: title("actions"),
                    class: "text-center"
                }
            ];
        }
    },
    methods: {
        loadFlows() {
            //setTimeout is for pagination settings are properly updated
            setTimeout(() => {
                if (this.namespace) {
                    this.$store.dispatch("flow/loadFlows", {
                        namespace: this.namespace,
                        perPage: this.perPage,
                        page: this.page
                    });
                } else {
                    this.$store.dispatch("flow/findFlows", {
                        q: "*",
                        perPage: this.perPage,
                        page: this.page
                    });
                }
            });
        },
        onNamespaceSelect(namespace) {
            if (this.$route.query.namespace !== namespace) {
                this.$router.push({ query: { namespace } });
                this.page = 1;
            }
            this.$store.commit("namespace/setNamespace", namespace);
            this.loadFlows();
        }
    }
};
</script>