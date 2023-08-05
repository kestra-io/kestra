<template>
    <el-button-group size="small">
        <el-tooltip :content="$t('source')" transition="" :hide-after="0" :persistent="false">
            <el-button :type="buttonType(editorViewTypes.SOURCE)" @click="switchView(editorViewTypes.SOURCE)" :icon="FileDocumentEdit" />
        </el-tooltip>
        <el-tooltip :content="$t('source and doc')" transition="" :hide-after="0" :persistent="false">
            <el-button :type="buttonType(editorViewTypes.SOURCE_DOC)" @click="switchView(editorViewTypes.SOURCE_DOC)" :icon="BookOpenPageVariantOutline" />
        </el-tooltip>
        <el-tooltip :content="$t('source and topology')" transition="" :hide-after="0" :persistent="false">
            <el-button :type="buttonType(editorViewTypes.SOURCE_TOPOLOGY)" @click="switchView(editorViewTypes.SOURCE_TOPOLOGY)" :icon="FileChart" />
        </el-tooltip>
        <el-tooltip :content="$t('topology')" transition="" :hide-after="0" :persistent="false">
            <el-button :type="buttonType(editorViewTypes.TOPOLOGY)" @click="switchView(editorViewTypes.TOPOLOGY)" :icon="Graph" />
        </el-tooltip>
        <el-tooltip :content="$t('source and blueprints')" transition="" :hide-after="0" :persistent="false">
            <el-button :type="buttonType(editorViewTypes.SOURCE_BLUEPRINTS)" @click="switchView(editorViewTypes.SOURCE_BLUEPRINTS)" :icon="ImageSearch" />
        </el-tooltip>
    </el-button-group>
</template>

<script setup>
    import FileDocumentEdit from "vue-material-design-icons/FileDocumentEdit.vue";
    import Graph from "vue-material-design-icons/Graph.vue";
    import ImageSearch from "vue-material-design-icons/ImageSearch.vue";
    import FileChart from "vue-material-design-icons/FileChart.vue";
    import BookOpenPageVariantOutline from "vue-material-design-icons/BookOpenPageVariantOutline.vue";
    import {editorViewTypes} from "../../utils/constants";
</script>

<script>
    export default {
        props: {
            type: {
                type: String,
                required: true
            }
        },
        emits: ["switch-view"],
        methods: {
            switchView(view) {
                this.$emit("switch-view", view)
            },
            buttonType(view) {
                return view === this.type ? "primary" : "default";
            }
        }
    }
</script>

<style scoped lang="scss">
    :deep(.el-button) {
        &.el-button--default {
            background: var(--card-bg);
        }
    }
</style>
