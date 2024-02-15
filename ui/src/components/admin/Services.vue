<template>
    <top-nav-bar :title="routeInfo.title" />
    <section class="container">
        <nav>
            <collapse>
                <el-form-item>
                    <refresh-button class="float-right" @refresh="loadData" />
                </el-form-item>
            </collapse>
        </nav>
        <el-table
            :data="services"
            ref="table"
            :default-sort="{prop: 'hostname', order: 'ascending'}"
            stripe
            table-layout="auto"
            fixed
        >
            <el-table-column prop="id" sortable :sort-orders="['ascending', 'descending']" :label="$t('id')">
                <template #default="scope">
                    <id :value="scope.row.id" :shrink="true" />
                </template>
            </el-table-column>
            <el-table-column prop="type" sortable :sort-orders="['ascending', 'descending']" :label="$t('type')" />
            <el-table-column prop="state" sortable :sort-orders="['ascending', 'descending']" :label="$t('state')" />
            <el-table-column prop="server.hostname" sortable :sort-orders="['ascending', 'descending']" :label="$t('hostname')" />
            <el-table-column prop="server.type" sortable :sort-orders="['ascending', 'descending']" :label="$t('server type')" />
            <el-table-column prop="server.version" sortable :sort-orders="['ascending', 'descending']" :label="$t('version')" />
            <el-table-column prop="createdAt" sortable :sort-orders="['ascending', 'descending']" :label="$t('started date')">
                <template #default="scope">
                    <date-ago class-name="text-muted small" :inverted="true" :date="scope.row.createdAt" />
                </template>
            </el-table-column>
            <el-table-column prop="updatedAt" sortable :sort-orders="['ascending', 'descending']" :label="$t('healthcheck date')">
                <template #default="scope">
                    <date-ago class-name="text-muted small" :inverted="true" :date="scope.row.updatedAt" />
                </template>
            </el-table-column>
        </el-table>
    </section>
</template>
<script>
    import RouteContext from "../../mixins/routeContext";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import RefreshButton from "../../components/layout/RefreshButton.vue";
    import Collapse from "../../components/layout/Collapse.vue";
    import DateAgo from "../layout/DateAgo.vue";
    import Id from "../Id.vue";

    export default {
        mixins: [RouteContext],
        components: {DateAgo, RefreshButton, Collapse, TopNavBar, Id},
        data() {
            return {
                services: undefined,
            };
        },
        created() {
            this.loadData();
        },
        methods: {
            loadData() {
                this.$store.dispatch("service/findAll").then(services => {
                    this.services = services;
                });
            }
        },
        computed: {
            routeInfo() {
                return {
                    title: this.$t("services")
                }
            }
        }
    };
</script>