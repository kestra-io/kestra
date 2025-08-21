<template>
    <div class="h-100 d-flex flex-column">
        <Editor
            id="editorWrapper"
            ref="editorRefElement"
            class="flex-1"
            :model-value="draftSource === undefined ? source : draftSource"
            :schema-type="isCurrentTabFlow ? 'flow': undefined"
            :lang="extension === undefined ? 'yaml' : undefined"
            :extension="extension"
            :navbar="false"
            :read-only="isReadOnly"
            :creating="isCreating"
            :path="props.path"
            :diff-overview-bar="false"
            @update:model-value="editorUpdate"
            @cursor="updatePluginDocumentation"
            @save="isCurrentTabFlow ? save(): saveFileContent()"
            @execute="execute"
            @mouse-move="(e) => highlightHoveredTask(e.target?.position?.lineNumber)"
            @mouse-leave="() => highlightHoveredTask(-1)"
            :original="draftSource === undefined ? undefined : source"
            :diff-side-by-side="false"
        >
            <template #absolute>
                <AITriggerButton
                    :show="isCurrentTabFlow"
                    :opened="aiAgentOpened"
                    @click="draftSource = undefined; aiAgentOpened = true"
                />
                <ContentSave v-if="!isCurrentTabFlow" @click="saveFileContent" />
            </template>
            <template v-if="playgroundStore.enabled" #widget-content>
                <PlaygroundRunTaskButton :task-id="highlightedLines?.taskId" />
            </template>
        </Editor>
        <Transition name="el-zoom-in-center">
            <AiAgent
                v-if="aiAgentOpened"
                class="position-absolute prompt"
                @close="aiAgentOpened = false"
                :flow="editorContent"
                @generated-yaml="(yaml: string) => {draftSource = yaml; aiAgentOpened = false}"
            />
        </Transition>
        <AcceptDecline
            v-if="draftSource !== undefined"
            @accept="acceptDraft"
            @reject="declineDraft"
        />
    </div>
</template>

<script lang="ts" setup>
    import {computed, onActivated, onMounted, ref, provide, onBeforeUnmount} from "vue";
    import {useRoute, useRouter} from "vue-router";

    import {EDITOR_CURSOR_INJECTION_KEY, EDITOR_WRAPPER_INJECTION_KEY} from "../code/injectionKeys";
    import {usePluginsStore} from "../../stores/plugins";
    import {EditorTabProps, useEditorStore} from "../../stores/editor";
    import {useFlowStore} from "../../stores/flow";
    import {useNamespacesStore} from "override/stores/namespaces";
    import useFlowEditorRunTaskButton from "../../composables/playground/useFlowEditorRunTaskButton";

    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";

    import Editor from "./Editor.vue";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
    import AiAgent from "../ai/AiAgent.vue";
    import AITriggerButton from "../ai/AITriggerButton.vue";
    import AcceptDecline from "./AcceptDecline.vue";
    import PlaygroundRunTaskButton from "./PlaygroundRunTaskButton.vue";

    const route = useRoute();
    const router = useRouter();

    const editorStore = useEditorStore();
    const flowStore = useFlowStore();

    const cursor = ref();

    const toggleAiShortcut = (event: KeyboardEvent) => {
        if (event.code === "KeyK" && (event.ctrlKey || event.metaKey) && event.altKey && event.shiftKey && isCurrentTabFlow.value) {
            event.preventDefault();
            event.stopPropagation();
            event.stopImmediatePropagation();
            draftSource.value = undefined;
            aiAgentOpened.value = !aiAgentOpened.value;
        }
    };
    const aiAgentOpened = ref(false);
    const draftSource = ref<string | undefined>(undefined);

    provide(EDITOR_CURSOR_INJECTION_KEY, cursor);

    const props = withDefaults(defineProps<EditorTabProps>(), {
        extension: undefined,
        dirty: false,
        flow: true,
    });

    provide(EDITOR_WRAPPER_INJECTION_KEY, props.flow);

    const source = computed<string>(() => {
        return (props.flow
            ? flowStore.flowYaml
            : editorStore.tabs.find((t: any) => t.path === props.path)?.content) ?? "";
    })

    async function loadFile() {
        if (props.dirty || props.flow) return;

        const fileNamespace = namespace.value ?? route.params?.namespace;

        if (!fileNamespace) return;

        const content = await namespacesStore.readFile({namespace: fileNamespace.toString(), path: props.path ?? ""})
        editorStore.setTabContent({path: props.path, content})
    }

    onMounted(() => {
        loadFile();
        window.addEventListener("keydown", handleGlobalSave);
        window.addEventListener("keydown", toggleAiShortcut);
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
    const isCurrentTabFlow = computed(() => props.flow)
    const isReadOnly = computed(() => flowStore.flow?.deleted || !flowStore.isAllowedEdit || flowStore.readOnlySystemLabel);

    const timeout = ref<any>(null);

    const editorContent = computed(() => {
        return draftSource.value ?? source.value;
    });

    const pluginsStore = usePluginsStore();
    const namespacesStore = useNamespacesStore();

    function editorUpdate(newValue: string){
        if (editorContent.value === newValue) {
            return;
        }
        if (isCurrentTabFlow.value) {
            if (draftSource.value !== undefined) {
                draftSource.value = newValue;
            } else {
                flowStore.flowYaml = newValue;
            }
        }
        editorStore.setTabContent({
            content: newValue,
            path: props.path
        });
        editorStore.setTabDirty({
            path: props.path,
            dirty: true
        });

        // throttle the trigger of the flow update
        clearTimeout(timeout.value);
        timeout.value = setTimeout(() => {
            flowStore.onEdit({
                source: newValue,
                currentIsFlow: isCurrentTabFlow.value,
                editorViewType: "YAML", // this is to be opposed to the no-code editor
                topologyVisible: true,
            });
        }, 1000);
    }


    function updatePluginDocumentation(event: any) {
        const elementWrapper = YAML_UTILS.localizeElementAtIndex(event.model.getValue(), event.model.getOffsetAt(event.position));
        let element = (elementWrapper?.value?.type !== undefined ? elementWrapper.value : elementWrapper?.parents?.findLast(p => p.type !== undefined)) as Parameters<typeof pluginsStore.updateDocumentation>[0];
        pluginsStore.updateDocumentation(element);
    };

    const save = async () => {
        clearTimeout(timeout.value);
        const editorRef = editorRefElement.value
        if(!editorRef?.$refs.monacoEditor) return
        const result = await flowStore.save({content:(editorRef.$refs.monacoEditor as any).value})

        editorStore.setTabDirty({
            path: props.path,
            dirty: false
        });

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
        await namespacesStore.createFile({
            namespace: namespace.value,
            path: props.path,
            content: editorContent.value || "",
        });
        editorStore.setTabDirty({
            path: props.path,
            dirty: false
        });
    }

    const handleGlobalSave = (event: KeyboardEvent) => {
        if ((event.ctrlKey || event.metaKey) && event.key === "s") {
            event.preventDefault();
            if (isCurrentTabFlow.value) {
                save();
            } else {
                saveFileContent();
            }
        }
    };

    const execute = () => {
        flowStore.executeFlow = true;
    };

    function acceptDraft() {
        const accepted = draftSource.value;
        draftSource.value = undefined;
        editorUpdate(accepted!);
    }

    function declineDraft() {
        draftSource.value = undefined;
        aiAgentOpened.value = true;
    }

    const {
        playgroundStore,
        highlightHoveredTask,
        highlightedLines,
    } = useFlowEditorRunTaskButton(isCurrentTabFlow, editorRefElement, source);
</script>

<style scoped lang="scss">
    .prompt {
        bottom: 10%;
        width: calc(100% - 5rem);
        left: 3rem;
        max-width: 700px;
        background-color: var(--ks-background-panel);
        box-shadow: 0px 4px 4px 0px var(--ks-card-shadow);
    }
</style>
