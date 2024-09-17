<template>
    <Header />

    <div class="dashboard">
        <el-row :gutter="20" class="mx-0">
            <el-col :xs="24" :md="12" :lg="6">
                <Card
                    :icon="CheckBold"
                    :label="t('dashboard.success_ratio')"
                    value="89%"
                />
            </el-col>
            <el-col :xs="24" :md="12" :lg="6">
                <Card
                    :icon="Alert"
                    :label="t('dashboard.failure_ratio')"
                    value="11%"
                />
            </el-col>
            <el-col :xs="24" :md="12" :lg="6">
                <Card :icon="FileTree" :label="t('flows')" value="338" />
            </el-col>
            <el-col :xs="24" :md="12" :lg="6">
                <Card :icon="LightningBolt" :label="t('triggers')" value="29" />
            </el-col>
        </el-row>

        <el-row :gutter="20" class="mx-0">
            <el-col :xs="24" :lg="16">
                <div>1</div>
            </el-col>
            <el-col :xs="24" :lg="8">
                <div>2</div>
            </el-col>
        </el-row>

        <el-row :gutter="20" class="mx-0">
            <el-col :xs="24" :lg="12">
                <div>1</div>
            </el-col>
            <el-col :xs="24" :lg="12">
                <div>2</div>
            </el-col>
        </el-row>
    </div>
</template>

<script setup>
    import {useI18n} from "vue-i18n";

    import Header from "./components/Header.vue";
    import Card from "./components/Card.vue";

    import CheckBold from "vue-material-design-icons/CheckBold.vue";
    import Alert from "vue-material-design-icons/Alert.vue";
    import LightningBolt from "vue-material-design-icons/LightningBolt.vue";
    import FileTree from "vue-material-design-icons/FileTree.vue";

    const {t} = useI18n({useScope: "global"});
</script>

<style lang="scss" scoped>
$spacing: 20px;

.dashboard {
    padding: $spacing;

    & .el-row {
        width: 100%;

        & .el-col {
            padding-bottom: $spacing;

            & div {
                background: var(--card-bg);
            }
        }
    }
}
</style>
