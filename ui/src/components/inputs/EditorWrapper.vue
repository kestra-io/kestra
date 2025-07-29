<template>
    <editor
        id="editorWrapper"
        ref="editorRefElement"
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
                :enabled="aiEnabled"
                :opened="aiAgentOpened"
                @click="draftSource = undefined; aiAgentOpened = true"
            />
            <ContentSave v-if="!isCurrentTabFlow" @click="saveFileContent" />
        </template>
        <template v-if="playgroundStore.enabled" #widget-content>
            <el-button
                class="el-button--playground"
                @click="playgroundStore.runUntilTask(highlightedLines?.taskId)"
            >
                {{ t('playground.run_task') }}
            </el-button>
        </template>
    </editor>
    <transition name="el-zoom-in-center">
        <AiAgent
            v-if="aiAgentOpened"
            class="position-absolute prompt"
            @close="aiAgentOpened = false"
            :flow="flowContent"
            @generated-yaml="(yaml: string) => {draftSource = yaml; aiAgentOpened = false}"
        />
    </transition>
    <AcceptDecline
        v-if="draftSource !== undefined"
        class="position-absolute actions"
        @accept="acceptDraft"
        @reject="declineDraft"
    />
</template>

<script lang="ts" setup>
    import {computed, onActivated, onMounted, ref, provide, onBeforeUnmount} from "vue";
    import {useStore} from "vuex";
    import {useI18n} from "vue-i18n";
    import Editor from "./Editor.vue";

    import ContentSave from "vue-material-design-icons/ContentSave.vue";

    import {useRoute, useRouter} from "vue-router";

    const {t} = useI18n();

    const route = useRoute()
    const router = useRouter()

    import {EDITOR_CURSOR_INJECTION_KEY, EDITOR_WRAPPER_INJECTION_KEY} from "../code/injectionKeys";
    import {usePluginsStore} from "../../stores/plugins";
    import {useMiscStore} from "../../stores/misc";

    import AiAgent from "../ai/AiAgent.vue";
    import AITriggerButton from "../ai/AITriggerButton.vue";
    import AcceptDecline from "./AcceptDecline.vue";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import useFlowEditorRunTaskButton from "../../composables/playground/useFlowEditorRunTaskButton";

    const store = useStore();
    const miscStore = useMiscStore();

    const aiEnabled = computed(() => miscStore.configs?.isAiEnabled);
    const cursor = ref();

    const toggleAiShortcut = (event: KeyboardEvent) => {
        if (event.code === "KeyK" && (event.ctrlKey || event.metaKey) && event.altKey && event.shiftKey && isCurrentTabFlow.value && aiEnabled.value) {
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


    export interface EditorTabProps {
        name: string,
        path: string,
        extension?: string,
        flow?: boolean,
        dirty?: boolean,
    }

    const props = withDefaults(defineProps<EditorTabProps>(), {
        extension: undefined,
        dirty: false,
        flow: true,
    });

    provide(EDITOR_WRAPPER_INJECTION_KEY, props.flow);

    const source = computed<string>(() => {
        return props.flow
            ? store.state.flow.flowYaml
            : store.state.editor.tabs.find((t: any) => t.path === props.path)?.content;
    })

    async function loadFile() {
        if (props.dirty || props.flow) return;

        const fileNamespace = namespace.value ?? route.params?.namespace;

        if (!fileNamespace) return;

        const content = await store.dispatch("namespace/readFile", {namespace: fileNamespace, path: props.path})
        store.commit("editor/setTabContent", {path: props.path, content})
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
    });

    const editorRefElement = ref<InstanceType<typeof Editor>>();

    const namespace = computed(() => store.state.flow.namespace);
    const flowStore = computed(() => store.state.flow.flow);
    const isCreating = computed(() => store.state.flow.isCreating);
    const isCurrentTabFlow = computed(() => props.flow)
    const isReadOnly = computed(() => flowStore.value?.deleted || !store.getters["flow/isAllowedEdit"] || store.getters["flow/readOnlySystemLabel"]);

    const timeout = ref<any>(null);

    const flowContent = computed(() => {
        return draftSource.value ?? source.value;
    });

    const pluginsStore = usePluginsStore();

    function editorUpdate(newValue: string){
        if (flowContent.value === newValue) {
            return;
        }
        if (isCurrentTabFlow.value) {
            if (draftSource.value !== undefined) {
                draftSource.value = newValue;
            } else {
                store.commit("flow/setFlowYaml", newValue);
            }
        }
        store.commit("editor/setTabContent", {
            content: newValue,
            path: props.path
        });
        store.commit("editor/setTabDirty", {
            path: props.path,
            dirty: true
        });

        // throttle the trigger of the flow update
        clearTimeout(timeout.value);
        timeout.value = setTimeout(() => {
            store.dispatch("flow/onEdit", {
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

    const flowParsed = computed(() => store.getters["flow/flowParsed"]);
    const save = async () => {
        clearTimeout(timeout.value);
        const editorRef = editorRefElement.value
        if(!editorRef?.$refs.monacoEditor) return
        const result = await store.dispatch("flow/save", {content:(editorRef.$refs.monacoEditor as any).value})

        store.commit("editor/setTabDirty", {
            path: props.path,
            dirty: false
        });

        if (result === "redirect_to_update") {
            await router.push({
                name: "flows/update",
                params: {
                    id: flowParsed.value.id,
                    namespace: flowParsed.value.namespace,
                    tab: "edit",
                    tenant: route.params?.tenant,
                },
            });
        }
    };

    const saveFileContent = async () => {
        clearTimeout(timeout.value);
        await store.dispatch("namespace/createFile", {
            namespace: namespace.value,
            path: props.path,
            content: editorRefElement.value?.modelValue,
        });
        store.commit("editor/setTabDirty", {
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
        store.commit("flow/executeFlow", true);
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

.actions {
    bottom: 10%;
}
</style>