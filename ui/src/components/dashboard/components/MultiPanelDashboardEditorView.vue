<template>
    <MultiPanelGenericEditorView
        ref="editorView"
        v-if="showEditor"
        :editorElements="DASHBOARD_EDITOR_ELEMENTS"
        :defaultActiveTabs="DEFAULT_ACTIVE_TABS"
        :saveKey="saveKey"
    >
        <template #actions>
            <DashboardEditorButtons @save="onSave" />
        </template>
    </MultiPanelGenericEditorView>
</template>

<script lang="ts" setup>
    import {computed, markRaw, useTemplateRef, watch} from "vue";
    import {DASHBOARD_EDITOR_ELEMENTS, DEFAULT_ACTIVE_TABS} from "../composables/useDashboardPanels";
    import {useDashboardStore} from "../../../stores/dashboard";
    import MultiPanelGenericEditorView from "../../MultiPanelGenericEditorView.vue";
    import DashboardNoCodeEditor from "./DashboardNoCodeEditor.vue";
    import DashboardEditorButtons from "./DashboardEditorButtons.vue";
    import {useNoCodePanelsFull} from "../../flows/useNoCodePanels";

    const showEditor = computed(() => dashboardStore.isCreating || dashboardStore.parsedSource?.id);

    const saveKeyAlways = computed(() => `ks-dashboard-${dashboardStore.parsedSource?.id}`)
    const saveKey = computed(() => 
        dashboardStore.isCreating ? undefined : saveKeyAlways.value
    );

    const dashboardStore = useDashboardStore();

    const emit = defineEmits<{
        (e: "save", source?: string): void;
    }>();

    function onSave(){
        emit("save", dashboardStore.sourceCode);
    }

    watch(() => dashboardStore.isCreating, (isCreating) => {
        if(!isCreating){
            // reset panels when switching from creating to editing an existing dashboard
            editorView.value?.saveState(saveKeyAlways.value);
        }
    });
    
    const editorView = useTemplateRef<InstanceType<typeof MultiPanelGenericEditorView>>("editorView");

    useNoCodePanelsFull({
        RawNoCode: markRaw(DashboardNoCodeEditor),
        editorView,
        editorElements: DASHBOARD_EDITOR_ELEMENTS,
        source: computed(() => dashboardStore.sourceCode),
    });
</script>