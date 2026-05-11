<template>
    <AiCopilotWrapper
        ref="copilotWrapper"
        class="h-100 d-flex flex-column"
        :flow="editorContent"
        :generationType="aiGenerationTypes.FLOW"
        :namespace="namespace"
        @generated-yaml="(yaml: string) => { draftSource = yaml }"
    >
        <template #default="{aiCopilotOpened, openAiCopilot}">
            <img
                v-if="['jpg', 'jpeg', 'png', 'gif', 'webp', 'webm', 'avif'].includes(extension)"
                :src="`${apiUrl()}/namespaces/${namespace}/files?path=/${path}`"
                class="image-preview"
            >
            <Editor
                v-else
                id="editorWrapper"
                ref="editorRefElement"
                class="flex-1"
                :modelValue="hasDraft ? draftSource : source"
                :schemaType="flow ? 'flow': undefined"
                :lang="lang"
                :extension="extension"
                :navbar="false"
                :readOnly="flow && flowStore.isReadOnly"
                :creating="isCreating"
                :path="path"
                :diffOverviewBar="false"
                :scrollKey="editorScrollKey"
                @update:model-value="editorUpdate"
                @cursor="updatePluginDocumentation"
                @save="flow ? saveFlowYaml(): saveFileContent()"
                @execute="execute"
                @mouse-move="(e) => highlightHoveredTask(e.target?.position?.lineNumber)"
                @mouse-leave="() => highlightHoveredTask(-1)"
                :original="hasDraft ? source : undefined"
                :diffSideBySide="false"
            >
                <template #absolute>
                    <AITriggerButton
                        v-if="aiCopilotAllowed"
                        :show="flow"
                        :opened="aiCopilotOpened"
                        @click="() => { draftSource = undefined; openAiCopilot(); }"
                    />
                    <ContentSave v-if="!flow" @click="saveFileContent" />
                </template>
                <template v-if="playgroundStore.enabled" #widget-content>
                    <PlaygroundRunTaskButton :taskId="highlightedLines?.taskId" />
                </template>
                <template #buttons>
                    <AcceptDecline :visible="hasDraft" @accept="acceptDraft" @reject="declineDraft" />
                </template>
            </Editor>
        </template>
    </AiCopilotWrapper>
</template>

<script lang="ts">
    export const FILES_SET_DIRTY_INJECTION_KEY = Symbol("files-set-dirty-injection-key") as InjectionKey<(payload: { path: string; dirty: boolean }) => void>;
    export const FILES_UPDATE_CONTENT_INJECTION_KEY = Symbol("files-update-content-injection-key") as InjectionKey<(payload: { path: string; content: string }) => void>;

    export interface EditorTabProps {
        name: string;
        extension: string;
        path: string;
        flow: boolean;
        dirty: boolean;
    }
</script>

<script setup lang="ts">
    import {computed, onActivated, onMounted, ref, provide, onBeforeUnmount, watch, InjectionKey, inject} from "vue";
    import {useRoute, useRouter} from "vue-router";
    import {apiUrl} from "override/utils/route";
    import type * as monaco from "monaco-editor/esm/vs/editor/editor.api";

    import {EDITOR_CURSOR_INJECTION_KEY, EDITOR_WRAPPER_INJECTION_KEY} from "../no-code/injectionKeys";
    import {usePluginsStore} from "../../stores/plugins";
    import {isSuccessfulFlowSaveOutcome, useFlowStore} from "../../stores/flow";
    import {useApiStore} from "../../stores/api";
    import {useAuthStore} from "override/stores/auth"
    import {useNamespacesStore} from "override/stores/namespaces";
    import {useMiscStore} from "override/stores/misc";
    import {useOnboardingV2Store} from "../../stores/onboardingV2";
    import useFlowEditorRunTaskButton from "../../composables/playground/useFlowEditorRunTaskButton";
    import {aiGenerationTypes} from "../../utils/constants";

    import {flowYamlUtils as YAML_UTILS} from "@kestra-io/design-system";

    import Editor from "./Editor.vue";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
    import AiCopilotWrapper from "../ai/AiCopilotWrapper.vue";
    import AITriggerButton from "../ai/AITriggerButton.vue";
    import PlaygroundRunTaskButton from "./PlaygroundRunTaskButton.vue";
    import {FILES_CLOSE_TAB_INJECTION_KEY} from "./FileExplorer.vue";
    import resource from "../../models/resource"
    import action from "../../models/action"
    import AcceptDecline from "./AcceptDecline.vue";

    const route = useRoute();
    const router = useRouter();

    const flowStore = useFlowStore();
    const authStore = useAuthStore();

    const cursor = ref();

    const copilotWrapper = ref<InstanceType<typeof AiCopilotWrapper>>();

    const toggleAiShortcut = (event: KeyboardEvent) => {
        if (onboardingStore.isGuidedActive) {
            return;
        }
        if (event.code === "KeyK" && (event.ctrlKey || event.metaKey) && event.altKey && event.shiftKey && props.flow) {
            event.preventDefault();
            event.stopPropagation();
            event.stopImmediatePropagation();
            draftSource.value = undefined;
            if (copilotWrapper.value?.aiCopilotOpened) {
                copilotWrapper.value.closeAiCopilot();
            } else {
                copilotWrapper.value?.openAiCopilot();
            }
        }
    };
    const draftSource = ref<string | undefined>(undefined);

    provide(EDITOR_CURSOR_INJECTION_KEY, cursor);

    const props = defineProps<EditorTabProps>();

    provide(EDITOR_WRAPPER_INJECTION_KEY, props.flow);

    const sourceNS = ref("")
    const savedSourceNS = ref("")

    const source = computed(() => props.flow ? flowStore.flowYaml : sourceNS.value);
    const savedSource = computed(() => props.flow ? flowStore.flowYamlOrigin : savedSourceNS.value);

    // Overrides the wrapper's broader hasAnyActionOnAnyNamespace check with a
    // namespace-scoped permission check and onboarding guard.
    const aiCopilotAllowed = computed(() => {
        return !onboardingStore.isGuidedActive && authStore.user?.isAllowed(resource.COPILOT, action.USE, namespace.value);
    });

    async function loadFile() {
        if (props.dirty || props.flow) return;

        const fileNamespace = namespace.value ?? route.params?.namespace;
        if (!fileNamespace) return;
        const result = await namespacesStore.readFile({
            namespace: fileNamespace.toString(),
            path: props.path ?? ""
        });

        if(result.notFound) {
            console.error(result.error);
            closeCurrentTab();
            return
        }

        if(result.error){
            console.error(result.error);
            return
        }

        if (result.content) {
            sourceNS.value = result.content;
            savedSourceNS.value = result.content;
        }
    }

    const closeTab = inject(FILES_CLOSE_TAB_INJECTION_KEY, () => {});

    function closeCurrentTab() {
        closeTab(props);
    }

    const isDirty = computed(() => source.value !== savedSource.value);

    watch(() => props.dirty, (newVal) => {
        if (!newVal && !props.flow) {
            savedSourceNS.value = sourceNS.value;
        }
    });

    const setDirty = inject(FILES_SET_DIRTY_INJECTION_KEY);
    watch(isDirty, (newVal) => {
        if(props.path){
            setDirty?.({path: props.path, dirty: newVal});
        }
    });

    onMounted(() => {
        if(props.flow){
            pluginsStore.lazyLoadSchemaType({type: "flow"});
        }
        loadFile();
        window.addEventListener("keydown", handleGlobalSave);
        window.addEventListener("keydown", toggleAiShortcut);
        if(route.query.ai === "open" && !onboardingStore.isGuidedActive) {
            draftSource.value = undefined;
            copilotWrapper.value?.openAiCopilot();
        }
    });

    const LANGS_WITH_WORKERS_MAP = {
        yaml: "yaml",
        yml: "yaml",
        json: "json",
        js: "javascript",
        ts: "typescript",
        jsx: "javascript",
        tsx: "typescript",
    };

    const lang = computed(() => {
        if (props.extension in LANGS_WITH_WORKERS_MAP) {
            return LANGS_WITH_WORKERS_MAP[props.extension as keyof typeof LANGS_WITH_WORKERS_MAP];
        }
        return undefined;
    });

    watch(() => flowStore.openAiCopilot, (newVal) => {
        if (onboardingStore.isGuidedActive) {
            return;
        }
        if (newVal) {
            draftSource.value = undefined;
            copilotWrapper.value?.openAiCopilot();
            flowStore.setOpenAiCopilot(false);
        }
    });

    onActivated(() => {
        loadFile();
    });

    onBeforeUnmount(() => {
        window.removeEventListener("keydown", handleGlobalSave);
        window.removeEventListener("keydown", toggleAiShortcut);
        pluginsStore.editorPlugin = undefined;
    });

    const editorRefElement = ref<InstanceType<typeof Editor>>();

    const namespace = computed(() => flowStore.flow?.namespace);
    const isCreating = computed(() => flowStore.isCreating);

    const timeout = ref<any>(null);

    const editorContent = computed(() => {
        return draftSource.value ?? source.value;
    });

    const pluginsStore = usePluginsStore();
    const namespacesStore = useNamespacesStore();
    const miscStore = useMiscStore();
    const apiStore = useApiStore();
    const onboardingStore = useOnboardingV2Store();
    const hash = computed<number>(() => miscStore.configs?.pluginsHash ?? 0);

    const editorScrollKey = computed(() => {
        if (props.flow) {
            const ns = flowStore.flow?.namespace ?? "";
            const id = flowStore.flow?.id ?? "";
            return `flow:${ns}/${id}:code`;
        }
        const ns = namespace.value;
        if (ns && props.path) {
            return `file:${ns}:${props.path}`;
        }
        return undefined;
    });


    const updateContent = inject(FILES_UPDATE_CONTENT_INJECTION_KEY);

    function editorUpdate(newValue: string){
        if (editorContent.value === newValue) {
            return;
        }
        if (props.flow) {
            if (hasDraft.value) {
                draftSource.value = newValue;
            } else {
                flowStore.flowYaml = newValue;
            }
        }
        sourceNS.value = newValue;
        if(props.path){
            updateContent?.({path: props.path, content: newValue});
        }

        // only validate and update graph for flow files
        if(!props.flow) return

        // throttle the trigger of the flow update
        clearTimeout(timeout.value);
        timeout.value = setTimeout(() => {
            flowStore.onEdit({
                source: newValue,
                editorViewType: "YAML", // this is to be opposed to the no-code editor
                topologyVisible: true,
            });
        }, 1000);
    }

    onBeforeUnmount(() => {
        clearTimeout(timeout.value);
    });

    function updatePluginDocumentation(event: {position: monaco.Position, model: monaco.editor.ITextModel}) {
        const cls = YAML_UTILS.getTypeAtPosition(source.value, event.position, pluginsStore.allTypes);
        const version = YAML_UTILS.getVersionAtPosition(source.value, event.position);
        pluginsStore.updateDocumentation({cls, version, hash: hash.value});
    }

    const saveFlowYaml = async () => {
        clearTimeout(timeout.value);
        const editorRef = editorRefElement.value
        if(!editorRef?.$refs.monacoEditor) return

        const isCreating = flowStore.isCreating;

        // Use saveAll() for consistency with the Save button behavior
        const result = isCreating
            ? await flowStore.save()
            : await flowStore.saveAll();

        if (result === "redirect_to_update") {
            await router.push({
                name: "flows/update",
                params: {
                    id: flowStore.flow?.id,
                    namespace: flowStore.flow?.namespace,
                    tab: "edit",
                    tenant: route.params?.tenant,
                },
            });
        }

        if (isSuccessfulFlowSaveOutcome(result)) {
            onboardingStore.recordSave();
        }
    };

    const saveFileContent = async () => {
        clearTimeout(timeout.value);
        if(!namespace.value || !props.path || props.flow) return
        await namespacesStore.saveOrCreateFile({
            namespace: namespace.value,
            path: props.path,
            content: editorContent.value || "",
        });
        savedSourceNS.value = source.value;
    }

    const handleGlobalSave = (event: KeyboardEvent) => {
        if ((event.ctrlKey || event.metaKey) && event.key === "s") {
            event.preventDefault();
            if (props.flow) {
                saveFlowYaml();
            } else {
                saveFileContent();
            }
        }
    };

    const execute = () => {
        flowStore.executeFlow = true;
    };

    function trackAiCopilotAction(action: string) {
        apiStore.posthogEvents({
            type: "AI_COPILOT",
            action,
            ai_copilot_configured: miscStore.configs?.isAiEnabled === true,
        });
    }

    function acceptDraft() {
        trackAiCopilotAction("changes_apply");
        const accepted = draftSource.value;
        draftSource.value = undefined;
        editorUpdate(accepted!);
        copilotWrapper.value?.resetConversation();
    }

    function declineDraft() {
        trackAiCopilotAction("changes_reject");
        draftSource.value = undefined;
        copilotWrapper.value?.openAiCopilot();
    }

    const hasDraft = computed(() => draftSource.value !== undefined);

    const {
        playgroundStore,
        highlightHoveredTask,
        highlightedLines,
    } = useFlowEditorRunTaskButton(computed(() => props.flow), editorRefElement, source);
</script>

<style scoped lang="scss">
    .image-preview {
        margin: 2rem;
    }
</style>
