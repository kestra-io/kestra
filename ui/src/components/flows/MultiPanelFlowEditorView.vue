<template>
    <div class="flow-editor-shell">
        <MultiPanelGenericEditorView
            ref="editorView"
            :class="{playgroundMode}"
            :editorElements="EDITOR_ELEMENTS"
            :defaultActiveTabs="tabs"
            :saveKey
            :preSerializePanels="preSerializePanels"
            :bottomVisible="playgroundMode"
            @set-tab-value="setTabValue"
        >
            <template #actions>
                <EditorButtonsWrapper
                    :haveChange
                    :showSaveAndExecute="isOnboardingCreate"
                />
            </template>
            <template #bottom-panel>
                <FlowPlayground v-if="playgroundMode" />
            </template>
            <template #footer>
                <KeyShortcuts />
            </template>
        </MultiPanelGenericEditorView>

        <Transition name="onboarding-hint-fade">
            <div
                v-if="isOnboardingCreate && showExecuteHint"
                class="onboarding-execute-hint-wrap"
            >
                <div class="onboarding-execute-hint">
                    <div class="onboarding-execute-hint__content">
                        <h3>{{ $t("welcome_copilot.execute_hint.title") }}</h3>
                        <p>{{ $t("welcome_copilot.execute_hint.description") }}</p>
                    </div>
                    <KsButton
                        link
                        class="onboarding-execute-hint__close"
                        :aria-label="$t('close')"
                        @click="showExecuteHint = false"
                    >
                        <Close />
                    </KsButton>
                </div>
            </div>
        </Transition>
    </div>
</template>

<script setup lang="ts">
    import {computed, markRaw, onMounted, onUnmounted, ref, watch} from "vue"
    import {useRoute, useRouter} from "vue-router"
    import Close from "vue-material-design-icons/Close.vue"
    import * as Utils from "../../utils/utils"
    import {usePlaygroundStore} from "../../stores/playground"
    import {useOnboardingV2Store} from "../../stores/onboardingV2"

    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils"
    import FlowPlayground from "./FlowPlayground.vue"
    import EditorButtonsWrapper from "../inputs/EditorButtonsWrapper.vue"
    import KeyShortcuts from "../inputs/KeyShortcuts.vue"
    import NoCode from "../no-code/NoCode.vue"
    import {useTriggerDraftStore} from "../../stores/triggerDraft"
    import {DEFAULT_ACTIVE_TABS, EDITOR_ELEMENTS} from "override/components/flows/panelDefinition"
    import {useFilesPanels, useInitialFilesTabs} from "./useFilesPanels"
    import {useTopologyPanels} from "./useTopologyPanels"
    import {useKeyShortcuts} from "../../utils/useKeyShortcuts"

    import {useNoCodePanelsFull} from "./useNoCodePanels"
    import {useFlowStore} from "../../stores/flow"
    import {usePluginsStore} from "../../stores/plugins"
    import {trackTabOpen} from "../../utils/tabTracking"
    import {Panel, Tab} from "../../utils/multiPanelTypes"
    import MultiPanelGenericEditorView from "../MultiPanelGenericEditorView.vue"

    function isTabFlowRelated(element: Tab){
        return ["code", "nocode", "topology"].includes(element.uid)
            // when the flow file is dirty all the nocode tabs get splashed
            || element.uid.startsWith("nocode-")
    }

    const RawNoCode = markRaw(NoCode)

    const onboardingV2Store = useOnboardingV2Store()
    const flowStore = useFlowStore()
    const {showKeyShortcuts} = useKeyShortcuts()

    const alwaysSaveKey = computed(() => `el-fl-${flowStore.flow?.namespace}-${flowStore.flow?.id}`)
    const saveKey = computed(() => flowStore.isCreating ? undefined : alwaysSaveKey.value)

    watch(() => flowStore.isCreating, (isCreating) => {
        if (!isCreating) {
            if (isGuidedCodeOnly.value && alwaysSaveKey.value) {
                localStorage.removeItem(alwaysSaveKey.value)
            } else {
                editorView.value?.saveState(alwaysSaveKey.value)
            }
        }
    })

    const route = useRoute()
    const router = useRouter()
    const editorView = ref<InstanceType<typeof MultiPanelGenericEditorView> | null>(null)
    const showExecuteHint = ref(true)
    const isOnboardingCreate = computed(() =>
        route.name === "flows/create" && route.query.onboardingPreset === "true",
    )

    onMounted(async () => {
        // Ensure the Flow Code panel is open and focused when arriving with ai=open
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
        // Show dialog instead of creating panel
        if(tabValue === "keyshortcuts"){
            showKeyShortcuts()
            return false
        }
    }

    useInitialFilesTabs(EDITOR_ELEMENTS)

    function cleanupNoCodeTabKey(key: string): string {
        // remove the number for "nocode-1234-" prefix from the key
        return /^nocode-\d{4}/.test(key) ? key.slice(0, 6) + key.slice(11) : key
    }

    function preSerializePanels(v:Panel[]){
        return v.map(p => ({
            tabs: p.tabs.map(t => t.uid),
            activeTab: cleanupNoCodeTabKey(p.activeTab?.uid),
            size: p.size,
        }))
    }

    const haveChange = computed(() => flowStore.haveChange || panels.value.some(panel =>
        panel.tabs.some(tab => tab.dirty),
    ))

    const {panels, actions} = useNoCodePanelsFull({
        RawNoCode,
        editorView,
        editorElements: EDITOR_ELEMENTS,
        source: computed(() => flowStore.flowYaml),
    })

    const isGuidedCodeOnly = computed(
        () => onboardingV2Store.isGuidedActive && onboardingV2Store.state.editorMode === "code_only",
    )
    watch(isGuidedCodeOnly, (guided, wasGuided) => {
        if (guided && playgroundStore.enabled) {
            playgroundStore.enabled = false
        }
        if (!guided && wasGuided && alwaysSaveKey.value) {
            localStorage.removeItem(alwaysSaveKey.value)
        }
    }, {immediate: true})
    const tabs = computed(() => (isGuidedCodeOnly.value ? ["code"] : DEFAULT_ACTIVE_TABS))

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

    // Track initial tabs opened while editing or creating flow.
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

    .playgroundMode :deep(.tabs-wrapper) {
        #{--kel-color-primary}: var(--ks-playground-bg-color);
        color: var(--ks-button-content-primary);
        background-position: 10% 0;
    }

    .flow-editor-shell {
        position: relative;
        height: 100%;
    }

    .onboarding-execute-hint-wrap {
        position: absolute;
        top: 5rem;
        right: 1rem;
        z-index: 20;
        display: flex;
        justify-content: flex-end;
        pointer-events: none;
    }

    .onboarding-execute-hint {
        --border-angle: 0turn;
        --hint-gradient: conic-gradient(
            from calc(var(--border-angle) + 50.37deg) at 50% 50%,
            #3991ff 0deg,
            #8c4bff 124.62deg,
            #a396ff 205.96deg,
            #3991ff 299.42deg,
            #e0e0ff 342.69deg,
            #3991ff 360deg
        );
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: 1rem;
        width: min(100%, 22.5rem);
        padding: calc(1.75rem - 1px) calc(1.75rem - 1px) calc(1.5rem - 1px);
        border: 1px solid transparent;
        border-radius: 0.75rem;
        background:
            linear-gradient(var(--ks-background-card), var(--ks-background-card)) padding-box,
            var(--hint-gradient) border-box;
        box-shadow: 0 0.5rem 1.5rem rgba(15, 23, 42, 0.06);
        pointer-events: auto;
        animation: onboardingHintBorderSpin 3s linear infinite;
    }

    .onboarding-execute-hint__content {
        h3 {
            margin: 0 0 0.75rem;
            color: var(--ks-content-primary);
            font-size: var(--ks-font-size-lg);
            font-weight: 700;
            line-height: 1.15;
        }

        p {
            margin: 0;
            color: var(--ks-content-secondary);
            font-size: var(--ks-font-size-sm);
            line-height: 1.45;
        }
    }

    .onboarding-execute-hint__close {
        color: var(--ks-content-tertiary);
        flex-shrink: 0;
    }

    @media (max-width: 768px) {
        .onboarding-execute-hint-wrap {
            top: 4rem;
            right: 1rem;
            right: 1rem;
        }

        .onboarding-execute-hint {
            width: 100%;
            padding: 1.25rem;
        }

        .onboarding-execute-hint__content {
            h3 {
                font-size: var(--ks-font-size-lg);
            }

            p {
                font-size: var(--ks-font-size-base);
            }
        }
    }

    .onboarding-hint-fade-enter-active,
    .onboarding-hint-fade-leave-active {
        transition: opacity 0.18s ease, transform 0.18s ease;
    }

    .onboarding-hint-fade-enter-from,
    .onboarding-hint-fade-leave-to {
        opacity: 0;
        transform: translateY(-6px);
    }

    @keyframes onboardingHintBorderSpin {
        to {
            --border-angle: 1turn;
        }
    }

    @property --border-angle {
        syntax: "<angle>";
        inherits: true;
        initial-value: 0turn;
    }
</style>

