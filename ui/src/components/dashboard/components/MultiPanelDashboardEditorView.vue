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
    import {computed, markRaw, nextTick, useTemplateRef, watch} from "vue"
    import _throttle from "lodash/throttle"
    import {DASHBOARD_EDITOR_ELEMENTS, DEFAULT_ACTIVE_TABS} from "../composables/useDashboardPanels"
    import {useDashboardStore} from "../../../stores/dashboard"
    import MultiPanelGenericEditorView from "../../MultiPanelGenericEditorView.vue"
    import DashboardNoCodeEditor from "./DashboardNoCodeEditor.vue"
    import DashboardEditorButtons from "./DashboardEditorButtons.vue"
    import {useNoCodePanelsFull} from "../../flows/useNoCodePanels"
    import {flowYamlUtils as YAML_UTILS} from "@kestra-io/topology"
    import * as DashboardsAPI from "@kestra-io/kestra-sdk/dashboards"

    const showEditor = computed(() => dashboardStore.isCreating || dashboardStore.parsedSource?.id)

    const saveKeyAlways = computed(() => `ks-dashboard-${dashboardStore.parsedSource?.id}`)
    const saveKey = computed(() => 
        dashboardStore.isCreating ? undefined : saveKeyAlways.value,
    )

    const dashboardStore = useDashboardStore()

    const emit = defineEmits<{
        (e: "save", source?: string): void;
    }>()

    function onSave(){
        emit("save", dashboardStore.sourceCode)
    }

    watch(() => dashboardStore.isCreating, (isCreating) => {
        if(!isCreating){
            // reset panels when switching from creating to editing an existing dashboard
            editorView.value?.saveState(saveKeyAlways.value)
        }
    })
    
    const editorView = useTemplateRef<InstanceType<typeof MultiPanelGenericEditorView>>("editorView")

    useNoCodePanelsFull({
        RawNoCode: markRaw(DashboardNoCodeEditor),
        editorView,
        editorElements: DASHBOARD_EDITOR_ELEMENTS,
        source: computed(() => dashboardStore.sourceCode),
    })

    watch(() => dashboardStore.sourceCode, _throttle(async () => {
        const errorsResult = await DashboardsAPI.validateDashboard({body: dashboardStore.sourceCode})

        const dbId = dashboardStore.activeDashboard?.id
        if (errorsResult.constraints) {
            dashboardStore.errors = [errorsResult.constraints]
        } else {
            dashboardStore.errors = undefined
        }

        if (!dashboardStore.isCreating && dbId !== undefined && YAML_UTILS.parse(dashboardStore.sourceCode).id !== dbId) {
            // Safety net only: the code editor now refuses edits to the id line
            // (useReadOnlyYamlKeys), so this is unreachable from normal typing and
            // stays silent. It still guards the paths that replace the source
            // wholesale without going through the editor.
            await nextTick()
            if(dashboardStore.sourceCode && dbId){
                dashboardStore.sourceCode = YAML_UTILS.replaceBlockWithPath({
                    source: dashboardStore.sourceCode,
                    path: "id",
                    newContent: dbId,
                })
            }
        }
    }, 300, {trailing: true, leading: false}))
</script>
