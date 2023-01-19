<template>
    <el-card
        shadow="never"
        :header="title"
    >
        <el-row :span="12" :gutter="15" v-if="count > 0" class="home-summary">
            <el-col :md="6" :lg="12" :xl="6">
                <status-pie :data="data" />
            </el-col>
            <el-col :md="6" :lg="12" :xl="6">
                <home-summary-status-label class="mt-sm-4 mt-md-0 mt-lg-4 mt-xl-0" :data="data" />
            </el-col>
        </el-row>
        <span v-else>
            <el-alert type="info" :closable="false">
                {{ $t("no result") }}
            </el-alert>
        </span>
    </el-card>
</template>
<script>
    import StatusPie from "./StatusPie.vue";
    import HomeSummaryStatusLabel from "./HomeSummaryStatusLabel.vue"

    export default {
        components: {
            StatusPie,
            HomeSummaryStatusLabel
        },
        props: {
            title: {
                type: String,
                required: true
            },
            data: {
                type: Object,
                required: false,
                default: () => {},
            },
        },
        computed: {
            count() {
                return this.data ? Object.values(this.data.executionCounts).reduce((a, b) => a + b, 0) : 0;
            }
        }
    };
</script>

