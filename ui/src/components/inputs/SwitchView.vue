<template>
    <el-button-group>
        <el-tooltip :content="$t('source')" transition="" :hideAfter="0" :persistent="false" effect="light">
            <el-button :type="buttonType(editorViewTypes.SOURCE)" @click="switchView(editorViewTypes.SOURCE)" :icon="FileDocumentEditOutline" />
        </el-tooltip>
        <el-tooltip :content="!isFlow ? $t('flow_only') : $t('source and doc')" transition="" :hideAfter="0" :persistent="false" effect="light">
            <el-button :disabled="!isFlow" :type="buttonType(editorViewTypes.SOURCE_DOC)" @click="switchView(editorViewTypes.SOURCE_DOC)" :icon="BookOpenOutline" />
        </el-tooltip>
        <el-tooltip :content="!isFlow ? $t('flow_only') : $t('source and topology')" transition="" :hideAfter="0" :persistent="false" effect="light">
            <el-button :disabled="!isFlow" :type="buttonType(editorViewTypes.SOURCE_TOPOLOGY)" @click="switchView(editorViewTypes.SOURCE_TOPOLOGY)" :icon="FileTableOutline" />
        </el-tooltip>
        <el-tooltip :content="!isFlow ? $t('flow_only') : $t('source and blueprints')" transition="" :hideAfter="0" :persistent="false" effect="light">
            <el-button :disabled="!isFlow" :type="buttonType(editorViewTypes.SOURCE_BLUEPRINTS)" @click="switchView(editorViewTypes.SOURCE_BLUEPRINTS)" :icon="BallotOutline" />
        </el-tooltip>
    </el-button-group>
</template>

<script setup lang="ts">
    import FileDocumentEditOutline from "vue-material-design-icons/FileDocumentEditOutline.vue";
    import BookOpenOutline from "vue-material-design-icons/BookOpenOutline.vue";
    import FileTableOutline from "vue-material-design-icons/FileTableOutline.vue";
    import BallotOutline from "vue-material-design-icons/BallotOutline.vue";
    import {editorViewTypes} from "../../utils/constants";
    import {computed} from "vue";
    import {useEditorStore} from "../../stores/editor";

    const props = defineProps<{
        type: string;
    }>();

    const emit = defineEmits<{
        (e: "switch-view", view: string): void;
    }>();

    const editorStore = useEditorStore();

    const isFlow = computed(() => {
        return !editorStore.current || editorStore.current.name === "Flow";
    });

    function switchView(view: string) {
        editorStore.view = view;
        emit("switch-view", view);
    }

    function buttonType(view: string) {
        return view === props.type ? "primary" : "default";
    }
</script>


<style scoped lang="scss">
    :deep(.el-button) {
        border: 0;
        background: none;
        opacity: 0.5;
        padding-left: .5rem;
        padding-right: .5rem;

        &.el-button--primary {
            opacity: 1;
        }
    }

    button.el-button--primary {
        color: var(--ks-content-link);
    }
</style>
