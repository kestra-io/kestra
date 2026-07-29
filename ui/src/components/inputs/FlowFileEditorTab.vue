<template>
    <div class="h-100 d-flex flex-column">
        <img
            v-if="['jpg', 'jpeg', 'png', 'gif', 'webp', 'webm', 'avif'].includes(extension)"
            :src="`${apiUrl()}/namespaces/${namespace}/files?path=/${path}`"
            class="image-preview"
        >
        <KsEditor
            v-else
            v-bind="editorBindings"
            id="flowFileEditorTab"
            ref="editorRefElement"
            class="flex-1"
            :modelValue="source"
            :schemaType="flow ? 'flow': undefined"
            :lang="lang"
            :navbar="false"
            :readOnly="flow && flowStore.isReadOnly"
            :path="path"
            :options="{
                creating: isCreating,
                diffOverviewBar: false,
                scrollKey: editorScrollKey,
                diffSideBySide: false,
                editor: {padding: {top: 16}},
            }"
            @update:model-value="editorUpdate"
            @cursor="updatePluginDocumentation"
            @save="flow ? saveFlowYaml(): saveFileContent()"
            @execute="execute"
            @mouse-move="(e) => highlightHoveredTask(e.target?.position?.lineNumber)"
            @mouse-leave="() => highlightHoveredTask(-1)"
        >
            <template #absolute>
                <ContentSave v-if="!flow" :class="{'save-disabled': !isDirty}" @click="isDirty && saveFileContent()" />
            </template>
            <template v-if="playgroundStore.enabled" #widget-content>
                <PlaygroundRunTaskButton :taskId="highlightedLines?.taskId" />
            </template>
        </KsEditor>
    </div>
</template>

<script lang="ts">
    export const FILES_SET_DIRTY_INJECTION_KEY = Symbol("files-set-dirty-injection-key") as InjectionKey<(payload: { path: string; dirty: boolean }) => void>
    export const FILES_UPDATE_CONTENT_INJECTION_KEY = Symbol("files-update-content-injection-key") as InjectionKey<(payload: { path: string; content: string }) => void>
    // Shared channel that lets actions outside the editor (e.g. restoring a revision)
    // push fresh content into an already-open file tab so it refreshes in place.
    // Keyed by file path; the entry object reference changes on each push so the
    // editor tab can react even when restoring the same content twice.
    export const FILES_REFRESH_CONTENT_INJECTION_KEY = Symbol("files-refresh-content-injection-key") as InjectionKey<Ref<Record<string, { content: string }>>>

    export interface EditorTabProps {
        name: string;
        extension: string;
        path: string;
        flow: boolean;
        dirty: boolean;
    }
</script>

<script setup lang="ts">
    import {computed, onActivated, onMounted, ref, provide, onBeforeUnmount, watch, InjectionKey, inject, type Ref} from "vue"
    import {useRoute, useRouter} from "vue-router"
    import {apiUrl} from "override/utils/route"
    import type * as monaco from "monaco-editor/esm/vs/editor/editor.api"

    import {EDITOR_CURSOR_INJECTION_KEY, EDITOR_WRAPPER_INJECTION_KEY} from "../no-code/injectionKeys"
    import {usePluginsStore} from "../../stores/plugins"
    import {isSuccessfulFlowSaveOutcome, useFlowStore} from "../../stores/flow"
    import {useDocStore} from "../../stores/doc"
    import {useNamespacesStore} from "override/stores/namespaces"
    import {useMiscStore} from "override/stores/misc"
    import {useOnboardingV2Store} from "../../stores/onboardingV2"
    import useFlowEditorRunTaskButton from "../../composables/playground/useFlowEditorRunTaskButton"

    import {flowYamlUtils as YAML_UTILS} from "@kestra-io/topology"
    import {KsEditor} from "@kestra-io/design-system"
    import {useEditorBindings} from "../../composables/useEditorBindings"

    import ContentSave from "vue-material-design-icons/ContentSave.vue"
    import PlaygroundRunTaskButton from "./PlaygroundRunTaskButton.vue"
    import {FILES_CLOSE_TAB_INJECTION_KEY} from "./FileExplorer.vue"

    const route = useRoute()
    const router = useRouter()

    const flowStore = useFlowStore()
    const editorBindings = useEditorBindings()

    const cursor = ref()

    // Ctrl/⌘+Alt+Shift+K opens the AI Copilot (the v2 context-dock tab). Suppressed during the
    // guided onboarding tour.
    const toggleAiShortcut = (event: KeyboardEvent) => {
        if (onboardingStore.isGuidedActive) {
            return
        }
        if (event.code === "KeyK" && (event.ctrlKey || event.metaKey) && event.altKey && event.shiftKey && props.flow) {
            event.preventDefault()
            event.stopPropagation()
            event.stopImmediatePropagation()
            miscStore.openCopilot()
        }
    }

    provide(EDITOR_CURSOR_INJECTION_KEY, cursor)

    const props = defineProps<EditorTabProps>()

    provide(EDITOR_WRAPPER_INJECTION_KEY, props.flow)

    const sourceNS = ref("")
    const savedSourceNS = ref("")

    const source = computed(() => props.flow ? flowStore.flowYaml : sourceNS.value)
    const savedSource = computed(() => props.flow ? flowStore.flowYamlOrigin : savedSourceNS.value)

    async function loadFile() {
        if (props.dirty || props.flow) return

        const fileNamespace = namespace.value ?? route.params?.namespace
        if (!fileNamespace) return
        const result = await namespacesStore.readFile({
            namespace: fileNamespace.toString(),
            path: props.path ?? "",
        })

        if(result.notFound) {
            console.error(result.error)
            closeCurrentTab()
            return
        }

        if(result.error){
            console.error(result.error)
            return
        }

        if (result.content) {
            sourceNS.value = result.content
            savedSourceNS.value = result.content
        }
    }

    const closeTab = inject(FILES_CLOSE_TAB_INJECTION_KEY, () => {})

    function closeCurrentTab() {
        closeTab(props)
    }

    const isDirty = computed(() => source.value !== savedSource.value)

    watch(() => props.dirty, (newVal) => {
        if (!newVal && !props.flow) {
            savedSourceNS.value = sourceNS.value
        }
    })

    const setDirty = inject(FILES_SET_DIRTY_INJECTION_KEY)
    watch(isDirty, (newVal) => {
        if(props.path){
            setDirty?.({path: props.path, dirty: newVal})
        }
    })

    onMounted(() => {
        useDocStore().docId = "flowEditor"
        if(props.flow){
            pluginsStore.lazyLoadSchemaType({type: "flow"})
        }
        loadFile()
        window.addEventListener("keydown", handleGlobalSave)
        window.addEventListener("keydown", toggleAiShortcut)
    })

    const LANGS_WITH_WORKERS_MAP = {
        yaml: "yaml",
        yml: "yaml",
        json: "json",
        js: "javascript",
        ts: "typescript",
        jsx: "javascript",
        tsx: "typescript",
    }

    const lang = computed(() => {
        if (props.extension in LANGS_WITH_WORKERS_MAP) {
            return LANGS_WITH_WORKERS_MAP[props.extension as keyof typeof LANGS_WITH_WORKERS_MAP]
        }
        return undefined
    })

    onActivated(() => {
        loadFile()
    })

    onBeforeUnmount(() => {
        window.removeEventListener("keydown", handleGlobalSave)
        window.removeEventListener("keydown", toggleAiShortcut)
        pluginsStore.editorPlugin = undefined
    })

    const editorRefElement = ref<InstanceType<typeof KsEditor>>()

    const namespace = computed(() => flowStore.flow?.namespace)
    const isCreating = computed(() => flowStore.isCreating)

    const timeout = ref<any>(null)

    const editorContent = computed(() => source.value)

    const pluginsStore = usePluginsStore()
    const namespacesStore = useNamespacesStore()
    const miscStore = useMiscStore()
    const onboardingStore = useOnboardingV2Store()
    const hash = computed<number>(() => miscStore.configs?.pluginsHash ?? 0)

    const editorScrollKey = computed(() => {
        if (props.flow) {
            const ns = flowStore.flow?.namespace ?? ""
            const id = flowStore.flow?.id ?? ""
            return `flow:${ns}/${id}:code`
        }
        const ns = namespace.value
        if (ns && props.path) {
            return `file:${ns}:${props.path}`
        }
        return undefined
    })


    const updateContent = inject(FILES_UPDATE_CONTENT_INJECTION_KEY)

    // React to content pushed from outside the editor (e.g. restoring a revision):
    // refresh the already-open tab in place instead of relying on a close/reopen,
    // which is a no-op for an open file because the tab keeps the same cached uid.
    const externalContentUpdates = inject(FILES_REFRESH_CONTENT_INJECTION_KEY, undefined)
    watch(() => (props.path ? externalContentUpdates?.value[props.path] : undefined), (update) => {
        if (!update || props.flow) return
        sourceNS.value = update.content
        // restored content becomes the new clean baseline so the tab is not flagged dirty
        savedSourceNS.value = update.content
        if (props.path) {
            updateContent?.({path: props.path, content: update.content})
        }
    })

    function editorUpdate(newValue: string){
        if (editorContent.value === newValue) {
            return
        }
        if (props.flow) {
            flowStore.flowYaml = newValue
        }
        sourceNS.value = newValue
        if(props.path){
            updateContent?.({path: props.path, content: newValue})
        }

        // only validate and update graph for flow files
        if(!props.flow) return

        // throttle the trigger of the flow update
        clearTimeout(timeout.value)
        timeout.value = setTimeout(() => {
            flowStore.onEdit({
                source: newValue,
                editorViewType: "YAML", // this is to be opposed to the no-code editor
                topologyVisible: true,
            })
        }, 1000)
    }

    onBeforeUnmount(() => {
        clearTimeout(timeout.value)
    })

    function updatePluginDocumentation(event: {position: monaco.Position, model: monaco.editor.ITextModel}) {
        const cls = YAML_UTILS.getTypeAtPosition(source.value, event.position, pluginsStore.allTypes)
        const version = YAML_UTILS.getVersionAtPosition(source.value, event.position)
        pluginsStore.updateDocumentation({cls, version, hash: hash.value})
    }

    const saveFlowYaml = async () => {
        clearTimeout(timeout.value)
        if(!editorRefElement.value?.getEditor()) return

        const result = await flowStore.saveAll()

        if (result === "redirect_to_update") {
            await router.push({
                name: "flows/update",
                params: {
                    id: flowStore.flow?.id,
                    namespace: flowStore.flow?.namespace,
                    tab: "edit",
                    tenant: route.params?.tenant,
                },
            })
        }

        if (isSuccessfulFlowSaveOutcome(result)) {
            onboardingStore.recordSave()
        }
    }

    const saveFileContent = async () => {
        clearTimeout(timeout.value)
        if(!namespace.value || !props.path || props.flow) return
        await namespacesStore.saveOrCreateFile({
            namespace: namespace.value,
            path: props.path,
            content: editorContent.value || "",
        })
        savedSourceNS.value = source.value
    }

    const handleGlobalSave = (event: KeyboardEvent) => {
        if ((event.ctrlKey || event.metaKey) && event.key === "s") {
            event.preventDefault()
            if (props.flow) {
                saveFlowYaml()
            } else if (isDirty.value) {
                saveFileContent()
            }
        }
    }

    const execute = () => {
        flowStore.executeFlow = true
    }

    const {
        playgroundStore,
        highlightHoveredTask,
        highlightedLines,
    } = useFlowEditorRunTaskButton(computed(() => props.flow), editorRefElement, source)
</script>

<style scoped lang="scss">
    .image-preview {
        margin: 2rem;
    }

    .save-disabled {
        opacity: 0.4;
        cursor: not-allowed;
        pointer-events: none;
    }
</style>
