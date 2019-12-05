<template>
    <div>
        <h1>
            Flows
            <router-link to="/flows/add">
                <b-button id="add-flow">
                    <plus />
                </b-button>
            </router-link>
        </h1>
        <b-tooltip target="add-flow" triggers="hover">Add flow</b-tooltip>
        <b-table striped hover :items="flows" :fields="fields">
            <template v-slot:cell(edit)="row">
                <router-link
                    class="btn btn-default"
                    :to="`/flows/edit/${row.item.namespace}/${row.item.id}`"
                >
                    <wrench id="edit-action" />
                </router-link>
            </template>
        </b-table>
        <b-row>
            <b-col offset-md="8" sm="5" md class="my-1">
                <b-form-group
                    label="Per page"
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
import Wrench from "vue-material-design-icons/Wrench";
import Plus from "vue-material-design-icons/Plus";

export default {
    components: { Wrench, Plus },
    data() {
        return {
            page: 1,
            perPage: 5,
            pageOptions: [5, 10, 25, 50, 100],
        };
    },
    created() {
        this.loadFlows();
    },
    computed: {
        ...mapState("flow", ["flows", "total"]),
        fields () {
            return [
                {
                    key: "id",
                    label: this.$t("Id"),
                    class: "text-center"
                },
                {
                    key: "namespace",
                    label: this.$t("Namespace"),
                    class: "text-center"
                },
                {
                    key: "revision",
                    label: this.$t("Revision"),
                    class: "text-center"
                },
                {
                    key: "edit",
                    label: "Edit",
                    class: "text-center"
                }
            ]
        }
    },
    methods: {
        loadFlows() {
            //setTimeout is for pagination settings are properly updated
            setTimeout(() => {
                this.$store.dispatch("flow/loadFlows", {
                    namespace: "org.floworc.myns",
                    perPage: this.perPage,
                    page: this.page
                });
            });
        }
    }
};
</script>

<style scoped lang="scss">
</style>
