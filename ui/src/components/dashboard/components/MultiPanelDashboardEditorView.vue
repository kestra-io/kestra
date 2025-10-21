<template>
    <MultiPanelGenericEditorView
        ref="editorView"
        :editorElements="DASHBOARD_EDITOR_ELEMENTS"
        :defaultActiveTabs="DEFAULT_ACTIVE_TABS"
        :saveKey="`ks-dashboard-${dashboardStore.dashboard?.id}`"
    >
        <template #actions>
            <DashboardEditorButtons @save="onSave" />
        </template>
    </MultiPanelGenericEditorView>
</template>

<script lang="ts" setup>
    import {computed, markRaw, useTemplateRef} from "vue";
    import {DASHBOARD_EDITOR_ELEMENTS, DEFAULT_ACTIVE_TABS} from "../composables/useDashboardPanels";
    import {useDashboardStore} from "../../../stores/dashboard";
    import MultiPanelGenericEditorView from "../../MultiPanelGenericEditorView.vue";
    import DashboardEditorButtons from "./DashboardEditorButtons.vue";

    const dashboardStore = useDashboardStore();

    const emit = defineEmits<{
        (e: "save", source?: string): void;
    }>();

    function onSave(){
        emit("save", dashboardStore.sourceCode);
    }

    import DashboardNoCodeEditor from "./DashboardNoCodeEditor.vue";
    const editorView = useTemplateRef<InstanceType<typeof MultiPanelGenericEditorView>>("editorView");

    import {useNoCodePanelsFull} from "../../flows/useNoCodePanels";
    useNoCodePanelsFull({
        RawNoCode: markRaw(DashboardNoCodeEditor),
        editorView,
        editorElements: DASHBOARD_EDITOR_ELEMENTS,
        source: computed(() => dashboardStore.sourceCode),
    });
</script>