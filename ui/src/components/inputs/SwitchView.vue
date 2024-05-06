<template>
    <el-button-group>
        <el-tooltip :content="$t('source')" transition="" :hide-after="0" :persistent="false" effect="light">
            <el-button :type="buttonType(editorViewTypes.SOURCE)" @click="switchView(editorViewTypes.SOURCE)" :icon="FileDocumentEditOutline" />
        </el-tooltip>
        <el-tooltip :content="$t('source and doc')" transition="" :hide-after="0" :persistent="false" effect="light">
            <el-button :type="buttonType(editorViewTypes.SOURCE_DOC)" @click="switchView(editorViewTypes.SOURCE_DOC)" :icon="BookOpenOutline" />
        </el-tooltip>
        <el-tooltip :content="$t('source and topology')" transition="" :hide-after="0" :persistent="false" effect="light">
            <el-button :type="buttonType(editorViewTypes.SOURCE_TOPOLOGY)" @click="switchView(editorViewTypes.SOURCE_TOPOLOGY)" :icon="FileTableOutline" />
        </el-tooltip>
        <el-tooltip :content="$t('topology')" transition="" :hide-after="0" :persistent="false" effect="light">
            <el-button :type="buttonType(editorViewTypes.TOPOLOGY)" @click="switchView(editorViewTypes.TOPOLOGY)" :icon="FileTreeOutline" />
        </el-tooltip>
        <el-tooltip :content="$t('source and blueprints')" transition="" :hide-after="0" :persistent="false" effect="light">
            <el-button :type="buttonType(editorViewTypes.SOURCE_BLUEPRINTS)" @click="switchView(editorViewTypes.SOURCE_BLUEPRINTS)" :icon="BallotOutline" />
        </el-tooltip>
    </el-button-group>
</template>

<script setup>
    import FileDocumentEditOutline from "vue-material-design-icons/FileDocumentEditOutline.vue";
    import BookOpenOutline from "vue-material-design-icons/BookOpenOutline.vue";
    import FileTableOutline from "vue-material-design-icons/FileTableOutline.vue";
    import FileTreeOutline from "vue-material-design-icons/FileTreeOutline.vue";
    import BallotOutline from "vue-material-design-icons/BallotOutline.vue";
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
        border: 0;
        background: none;
        opacity: 0.5;
        padding-left: calc(var(--spacer) / 2);
        padding-right: calc(var(--spacer) / 2);

        &.el-button--primary {
            opacity: 1;
        }
    }

    button.el-button--primary {
        color: var(--bs-primary);
    }
</style>
