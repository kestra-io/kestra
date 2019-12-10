<template>
    <div>
        <b-row>
            <b-col md="1"></b-col>
            <b-col>
                <h1 class="text-capitalize">
                    <router-link to="/flows">{{$t('flows')}}</router-link>
                    &gt; {{$t('executions')}}
                </h1>
            </b-col>
        </b-row>
        <hr />
        <b-table striped hover :items="executions" :fields="fields"></b-table>
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
    </div>
</template>

<script>
import { mapState } from "vuex";

export default {
    data() {
        return {
            page: 1,
            perPage: 5,
            pageOptions: [5, 10, 25, 50, 100]
        };
    },
    created() {
        console.log('executions route params', this.$route.params)
        this.loadExecutions();
    },
    computed: {
        ...mapState("execution", ["executions", "total"]),
        fields() {
            const title = title => {
                return this.$t(title).capitalize()
            }
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
        loadExecutions() {
            //setTimeout is for pagination settings are properly updated
            setTimeout(() => {
                this.$store.dispatch("execution/loadExecutions", {
                    flow: this.$route.params.flowId,
                    perPage: this.perPage,
                    page: this.page
                });
            });
        }
    }
};
</script>
