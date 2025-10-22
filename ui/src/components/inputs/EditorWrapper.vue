<template>
    <div class="h-100 d-flex flex-column">
        <Editor
            id="editorWrapper"
            ref="editorRefElement"
            class="flex-1"
            :modelValue="hasDraft ? draftSource : source"
            :schemaType="flow ? 'flow': undefined"
            :lang="extension === undefined ? 'yaml' : undefined"
            :extension="extension"
            :navbar="false"
            :readOnly="flow && flowStore.isReadOnly"
            :creating="isCreating"
            :path="props.path"
            :diffOverviewBar="false"
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
                    :show="flow"
                    :opened="aiCopilotOpened"
                    @click="draftSource = undefined; aiCopilotOpened = true"
                />
                <ContentSave v-if="!flow" @click="saveFileContent" />
            </template>
            <template v-if="playgroundStore.enabled" #widget-content>
                <PlaygroundRunTaskButton :taskId="highlightedLines?.taskId" />
            </template>
        </Editor>
        <Transition name="el-zoom-in-center">
            <AiCopilot
                v-if="aiCopilotOpened"
                class="position-absolute prompt"
                @close="closeAiCopilot"
                :flow="editorContent"
                :conversationId="conversationId"
                @generated-yaml="(yaml: string) => {draftSource = yaml; aiCopilotOpened = false}"
            />
        </Transition>
        <AcceptDecline
            v-if="hasDraft"
            @accept="acceptDraft"
            @reject="declineDraft"
        />
    </div>
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

    import {EDITOR_CURSOR_INJECTION_KEY, EDITOR_WRAPPER_INJECTION_KEY} from "../no-code/injectionKeys";
    import {usePluginsStore} from "../../stores/plugins";
    import {useFlowStore} from "../../stores/flow";
    import {useNamespacesStore} from "override/stores/namespaces";
    import {useMiscStore} from "override/stores/misc";
    import useFlowEditorRunTaskButton from "../../composables/playground/useFlowEditorRunTaskButton";

    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";

    import Editor from "./Editor.vue";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
    import AiCopilot from "../ai/AiCopilot.vue";
    import AITriggerButton from "../ai/AITriggerButton.vue";
    import AcceptDecline from "./AcceptDecline.vue";
    import PlaygroundRunTaskButton from "./PlaygroundRunTaskButton.vue";
    import Utils from "../../utils/utils";

    const route = useRoute();
    const router = useRouter();

    const flowStore = useFlowStore();

    const cursor = ref();

    const toggleAiShortcut = (event: KeyboardEvent) => {
        if (event.code === "KeyK" && (event.ctrlKey || event.metaKey) && event.altKey && event.shiftKey && props.flow) {
            event.preventDefault();
            event.stopPropagation();
            event.stopImmediatePropagation();
            draftSource.value = undefined;
            aiCopilotOpened.value = !aiCopilotOpened.value;
        }
    };
    const aiCopilotOpened = ref(false);
    const draftSource = ref<string | undefined>(undefined);

    provide(EDITOR_CURSOR_INJECTION_KEY, cursor);

    const props = defineProps<EditorTabProps>();

    provide(EDITOR_WRAPPER_INJECTION_KEY, props.flow);

    const sourceNS = ref("")
    const savedSourceNS = ref("")

    const source = computed(() => props.flow ? flowStore.flowYaml : sourceNS.value);
    const savedSource = computed(() => props.flow ? flowStore.flowYamlOrigin : savedSourceNS.value);

    async function loadFile() {
        if (props.dirty || props.flow) return;

        const fileNamespace = namespace.value ?? route.params?.namespace;
        if (!fileNamespace) return;
        sourceNS.value = await namespacesStore.readFile({namespace: fileNamespace.toString(), path: props.path ?? ""})

        savedSourceNS.value = source.value;
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
        loadPluginsHash();
        loadFile();
        window.addEventListener("keydown", handleGlobalSave);
        window.addEventListener("keydown", toggleAiShortcut);
        if(route.query.ai === "open") {
            draftSource.value = undefined;
            aiCopilotOpened.value = true;
        }
    });

    watch(() => flowStore.openAiCopilot, (newVal) => {
        if (newVal) {
            draftSource.value = undefined;
            aiCopilotOpened.value = true;
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
    const hash = ref<any>(null);

    const editorContent = computed(() => {
        return draftSource.value ?? source.value;
    });

    const pluginsStore = usePluginsStore();
    const namespacesStore = useNamespacesStore();
    const miscStore = useMiscStore();

    function loadPluginsHash() {
        miscStore.loadConfigs().then(config => {
            hash.value = config.pluginsHash;
        });
    }

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


    function updatePluginDocumentation(event: any) {
        const source = event.model.getValue();
        const cursorOffset = event.model.getOffsetAt(event.position);

        const isPlugin = (type: string) => pluginsStore.allTypes.includes(type);
        const isInRange = (range: [number, number, number]) =>
            cursorOffset >= range[0] && cursorOffset <= range[2];
        const getRangeSize = (range: [number, number, number]) => range[2] - range[0];

        const getElementFromRange = (typeElement: any) => {
            const wrapper = YAML_UTILS.localizeElementAtIndex(source, typeElement.range[0]);
            return wrapper?.value?.type && isPlugin(wrapper.value.type)
                ? wrapper.value
                : {type: typeElement.type};
        };

        const selectedElement = YAML_UTILS.extractFieldFromMaps(source, "type", () => true, isPlugin)
            .filter(el => el.range && isInRange(el.range))
            .reduce((closest, current) =>
                        !closest || getRangeSize(current.range) < getRangeSize(closest.range)
                            ? current
                            : closest
                    , null as any);

        let result = selectedElement ? getElementFromRange(selectedElement) : undefined;
        result = {...result, hash: hash.value, forceRefresh: true};
        pluginsStore.updateDocumentation(result as Parameters<typeof pluginsStore.updateDocumentation>[0]);
    };

    const saveFlowYaml = async () => {
        clearTimeout(timeout.value);
        const editorRef = editorRefElement.value
        if(!editorRef?.$refs.monacoEditor) return

        // Use saveAll() for consistency with the Save button behavior
        const result = flowStore.isCreating
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
    };

    const saveFileContent = async () => {
        clearTimeout(timeout.value);
        if(!namespace.value || !props.path || props.flow) return
        await namespacesStore.createFile({
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

    const conversationId = ref<string>(Utils.uid());

    function acceptDraft() {
        const accepted = draftSource.value;
        draftSource.value = undefined;
        conversationId.value = Utils.uid();
        editorUpdate(accepted!);
    }

    function declineDraft() {
        draftSource.value = undefined;
        aiCopilotOpened.value = true;
    }

    function closeAiCopilot() {
        aiCopilotOpened.value = false;
        const currentQuery = {...route.query, ai: undefined};
        router.replace({
            name: route.name,
            params: route.params,
            query: currentQuery
        });
    }

    const hasDraft = computed(() => draftSource.value !== undefined);

    const {
        playgroundStore,
        highlightHoveredTask,
        highlightedLines,
    } = useFlowEditorRunTaskButton(computed(() => props.flow), editorRefElement, source);
</script>

<style scoped lang="scss">
    .prompt {
        bottom: 10%;
        width: calc(100% - 5rem);
        left: 3rem;
        max-width: 700px;
        background-color: var(--ks-background-panel);
        box-shadow: 0 2px 4px 0 var(--ks-card-shadow);
    }
</style>
