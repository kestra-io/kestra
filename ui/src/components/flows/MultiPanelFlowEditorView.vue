<template>
    <div class="flow-editor-shell">
        <MultiPanelGenericEditorView
            ref="editorView"
            :editorElements="EDITOR_ELEMENTS"
            :defaultActiveTabs="tabs"
            :saveKey
            :preSerializePanels="preSerializePanels"
            :bottomVisible="playgroundMode"
            @set-tab-value="setTabValue"
        >
            <template #actions>
                <FlowEditorStats />
            </template>
            <template #bottom-panel>
                <FlowPlayground v-if="playgroundMode" />
            </template>
            <template #footer>
                <KeyShortcuts />
            </template>
        </MultiPanelGenericEditorView>

    </div>
</template>

<script setup lang="ts">
    import {computed, defineAsyncComponent, markRaw, onMounted, onUnmounted, ref, watch} from "vue"
    import {useRoute, useRouter} from "vue-router"
    import * as Utils from "../../utils/utils"
    import {usePlaygroundStore} from "../../stores/playground"

    import {flowYamlUtils as YAML_UTILS} from "@kestra-io/topology"
    import FlowPlayground from "./FlowPlayground.vue"
    import FlowEditorStats from "override/components/flows/FlowEditorStats.vue"
    import KeyShortcuts from "../inputs/KeyShortcuts.vue"
    import {useTriggerDraftStore} from "../../stores/triggerDraft"
    import {DEFAULT_ACTIVE_TABS, EDITOR_ELEMENTS} from "override/components/flows/panelDefinition"
    import {useFilesPanels, useInitialFilesTabs} from "./useFilesPanels"
    import {useTopologyPanels} from "./useTopologyPanels"
    import {useKeyShortcuts} from "../../utils/useKeyShortcuts"
    import {storageKeys} from "../../utils/constants"

    import {useNoCodePanelsFull} from "./useNoCodePanels"
    import {useFlowStore} from "../../stores/flow"
    import {usePluginsStore} from "../../stores/plugins"
    import {trackTabOpen} from "../../utils/tabTracking"
    import {Panel, Tab} from "../../utils/multiPanelTypes"
    import MultiPanelGenericEditorView from "../MultiPanelGenericEditorView.vue"

    function isTabFlowRelated(element: Tab){
        return ["code", "nocode", "topology"].includes(element.uid)
            || element.uid.startsWith("nocode-")
    }

    // Only one of the two engines can ever render, but importing both statically put both in
    // the editor's boot graph - and the whole no-code tree with them, even when the user never
    // leaves the code tab. The chosen one now loads when a no-code tab first opens; the tabs
    // already render it inside a Suspense boundary (see getTabFromNoCodeTab).
    const RawNoCode = markRaw(
        localStorage.getItem(storageKeys.NOCODE_ENGINE) === "legacy"
            ? defineAsyncComponent(() => import("../no-code/NoCode.vue"))
            : defineAsyncComponent(() => import("../no-code/blocks/BlockEditor.vue")),
    )

    const flowStore = useFlowStore()
    const {showKeyShortcuts} = useKeyShortcuts()

    const alwaysSaveKey = computed(() => `el-fl-${flowStore.flow?.namespace}-${flowStore.flow?.id}`)
    const saveKey = computed(() => flowStore.isCreating ? undefined : alwaysSaveKey.value)

    watch(() => flowStore.isCreating, (isCreating) => {
        if (!isCreating) {
            editorView.value?.saveState(alwaysSaveKey.value)
        }
    })

    const route = useRoute()
    const router = useRouter()
    const editorView = ref<InstanceType<typeof MultiPanelGenericEditorView> | null>(null)

    onMounted(async () => {
        if(route.query.ai === "open"){
            if(!editorView.value?.openTabs.includes("code")) editorView.value?.setTabValue("code")
            else editorView.value?.focusTab("code")
        }

        if(route.query.createTrigger === "true"){
            if(!editorView.value?.openTabs.includes("nocode")) {
                editorView.value?.setTabValue("nocode")
            } else {
                editorView.value?.focusTab("nocode")
            }

            const draft = triggerDraftStore.consumeDraft(
                flowStore.flow?.namespace ?? "",
                flowStore.flow?.id ?? "",
            )

            const {createTrigger: _, ...query} = route.query
            await router.replace({...route, query})

            if (draft?.triggerYaml) {
                flowStore.flowYaml = spliceTriggerIntoFlow(
                    flowStore.flowYaml ?? "",
                    draft.triggerYaml,
                )
            } else {
                const panelIndex = Math.max(
                    0,
                    panels.value.findIndex(p => p.tabs.some(t => t.uid.startsWith("nocode"))),
                )
                const blockSchemaPath = [
                    pluginsStore.flowSchema?.$ref,
                    "properties",
                    "triggers",
                    "items",
                ].join("/")
                actions.openAddTaskTab({panelIndex, tabIndex: 0}, "triggers", blockSchemaPath)
            }
        }
    })

    const triggerDraftStore = useTriggerDraftStore()

    function spliceTriggerIntoFlow(source: string, triggerBlock: string): string {
        const hasTriggersKey = /^triggers\s*:/m.test(source)

        if (hasTriggersKey) {
            return YAML_UTILS.insertBlockWithPath({
                source,
                newBlock: triggerBlock,
                parentPath: "triggers",
                position: "after",
            })
        }

        const normalizedSource = source.endsWith("\n") ? source : source + "\n"
        const indentedBlock = triggerBlock
            .split("\n")
            .map((line, idx) => (line.length === 0 ? "" : (idx === 0 ? `  - ${line}` : `    ${line}`)))
            .join("\n")
            .replace(/\s+$/, "")

        return `${normalizedSource}\ntriggers:\n${indentedBlock}\n`
    }

    const pluginsStore = usePluginsStore()
    const playgroundStore = usePlaygroundStore()

    const playgroundMode = computed(() => playgroundStore.enabled)

    onUnmounted(() => {
        playgroundStore.enabled = false
        playgroundStore.clearExecutions()
    })

    function setTabValue(tabValue: string) {
        if(tabValue === "keyshortcuts"){
            showKeyShortcuts()
            return false
        }
    }

    useInitialFilesTabs(EDITOR_ELEMENTS)

    function cleanupNoCodeTabKey(key: string): string {
        return /^nocode-\d{4}/.test(key) ? key.slice(0, 6) + key.slice(11) : key
    }

    function preSerializePanels(v:Panel[]){
        return v.map(p => ({
            tabs: p.tabs.map(t => t.uid),
            activeTab: cleanupNoCodeTabKey(p.activeTab?.uid),
            size: p.size,
        }))
    }

    const {panels, actions} = useNoCodePanelsFull({
        RawNoCode,
        editorView,
        editorElements: EDITOR_ELEMENTS,
        source: computed(() => flowStore.flowYaml),
    })

    const tabs = computed(() => DEFAULT_ACTIVE_TABS)

    flowStore.creationId = flowStore.creationId ?? Utils.uid()

    useFilesPanels(panels, computed(() => flowStore.flowParsed?.namespace))

    useTopologyPanels(panels, actions.openAddTaskTab, actions.openEditTaskTab)

    watch(() => flowStore.haveChange, (dirty) => {
        for(const panel of panels.value){
            if(panel.activeTab && isTabFlowRelated(panel.activeTab)){
                panel.activeTab.dirty = dirty
            }
            for(const tab of panel.tabs){
                if(isTabFlowRelated(tab)){
                    tab.dirty = dirty
                }
            }
        }
    })

    let hasTrackedInitialTabs = false
    watch(panels, (newPanels) => {
        if (!hasTrackedInitialTabs && newPanels && newPanels.length > 0) {
            hasTrackedInitialTabs = true
            const allTabs = newPanels.flatMap(panel => panel.tabs)
            allTabs.forEach(tab => trackTabOpen(tab))
        }
    }, {immediate: true})
</script>

<style lang="scss" scoped>
    .flow-editor-shell {
        position: relative;
        height: 100%;
    }
</style>
