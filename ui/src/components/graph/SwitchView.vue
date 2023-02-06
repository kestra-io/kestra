<template>
    <el-button-group size="small">
        <el-tooltip :disabled="!flowError" :content="flowErrorContent" raw-content>
            <el-button :type="flowError ? 'danger' : 'success'" :icon="flowError ? Close : Check" />
        </el-tooltip>
        <el-tooltip :content="$t('source')">
            <el-button type="primary" @click="switchView('source')" :icon="FileDocumentEdit" />
        </el-tooltip>
        <el-tooltip :content="$t('source and topology')">
            <el-button type="primary" @click="switchView('combined')" :icon="FileChart" />
        </el-tooltip>
        <el-tooltip :content="$t('topology')">
            <el-button type="primary" @click="switchView('topology')" :icon="Graph" />
        </el-tooltip>
    </el-button-group>
</template>
<script setup>
    import FileDocumentEdit from "vue-material-design-icons/FileDocumentEdit.vue";
    import Graph from "vue-material-design-icons/Graph.vue";
    import FileChart from "vue-material-design-icons/FileChart.vue";
    import Check from "vue-material-design-icons/Check.vue";
    import Close from "vue-material-design-icons/Close.vue";
</script>
<script>
    import {mapGetters} from "vuex";

    export default {
        name: "SwitchView",
        computed: {
            ...mapGetters("flow", ["flowError"]),
            flowErrorContent() {
                return this.flowError
                    ? "<pre style='max-width: 40vw; white-space: pre-wrap'>" + this.flowError + "</pre>"
                    :  ""
            }
        },
        methods: {
            switchView(view) {
                this.$emit("switch-view", view)
            }
        }
    }
</script>

<style scoped>

</style>