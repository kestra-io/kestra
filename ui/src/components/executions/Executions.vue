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
        <b-table striped hover :items="executions" :fields="fields">
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
                        @change="loadExecutions"
                        id="perPageSelect"
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
                    :per-page="perPage"
                    align="fill"
                    size="sm"
                    class="my-0"
                ></b-pagination>
            </b-col>
        </b-row>
        <bottom-line>
            <ul class="navbar-nav ml-auto">
                <li class="nav-item upload-file-wrapper">
                    <b-form-file
                        v-model="file"
                        @input="onFileUpload"
                        :state="Boolean(file)"
                        :placeholder="$t('choose file')"
                        drop-placeholder="Drop file here..."
                    ></b-form-file>
                </li>
                <li class="nav-item">
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

export default {
    components: { BottomLine, Eye, Plus },
    data() {
        return {
            file: undefined,
            page: 1,
            perPage: 5,
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
                    this.$bvToast.toast(this.$t("Triggered"), {
                        title: this.$t("Execution"),
                        autoHideDelay: 5000,
                        toaster: "b-toaster-top-right",
                        variant: "success"
                    });
                    this.loadExecutions();
                });
        },
        onFileUpload() {
            console.log("on file upload");
        },
        loadExecutions() {
            //setTimeout is for pagination settings are properly updated
            setTimeout(() => {
                this.$store.dispatch("execution/loadExecutions", {
                    namespace: this.$route.params.namespace,
                    flowId: this.$route.params.flowId,
                    size: this.perPage,
                    page: this.page
                });
            });
        }
    }
};
</script>
<style scoped>
.upload-file-wrapper {
    margin-right: 15px;
}
</style>
