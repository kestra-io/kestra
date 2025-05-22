<template>
    <editor
        ref="editorDomElement"
        :model-value="source"
        :schema-type="isCurrentTabFlow ? 'flow': undefined"
        :lang="extension === undefined ? 'yaml' : undefined"
        :extension="extension"
        :navbar="false"
        :read-only="isReadOnly"
        :creating="isCreating"
        :path="props.path"
        @update:model-value="editorUpdate"
        @cursor="updatePluginDocumentation"
        @save="save"
        @execute="execute"
    >
        <template #absolute>
            <KeyShortcuts v-if="isCurrentTabFlow" />
        </template>
    </editor>
</template>

<script lang="ts" setup>
    import {computed, onActivated, onMounted, ref, inject, provide, watch, nextTick} from "vue";
    import {useStore} from "vuex";
    import Editor from "./Editor.vue";
    import KeyShortcuts from "./KeyShortcuts.vue";

    import {YamlUtils as YAML_UTILS} from "@kestra-io/ui-libs";

    import {TOPOLOGY_CLICK_INJECTION_KEY, VISIBLE_PANELS_INJECTION_KEY, EDITOR_CURSOR_INJECTION_KEY, EDITOR_HIGHLIGHT_INJECTION_KEY} from "../code/injectionKeys";
    import {TopologyClickParams} from "../code/utils/types";
    import {Panel, Tab} from "../MultiPanelTabs.vue";

    const store = useStore();

    const topologyClick = inject(TOPOLOGY_CLICK_INJECTION_KEY, ref());
    const panels = inject(VISIBLE_PANELS_INJECTION_KEY, ref());

    const cursor = ref();
    provide(EDITOR_CURSOR_INJECTION_KEY, cursor);

    const highlight = ref();
    provide(EDITOR_HIGHLIGHT_INJECTION_KEY, highlight);

    watch(topologyClick, (event: TopologyClickParams | undefined) => {
        if (!event) return;

        const visible = panels.value?.map((p: Panel) => p.tabs.map((t: Tab) => t.value)).flat();
        if(visible?.includes("nocode")) return;

        const findLineNumber = (section: string, task: string, flow?: string): number | undefined => {
            const lines = (flow ?? source.value).split("\n");

            let inSection = false;

            for (let i = 0; i < lines.length; i++) {
                const line = lines[i].trim();

                // Check for section entry
                if (!inSection && line.startsWith(`${section}:`)) {
                    inSection = true;
                    continue;
                }

                // If in section, check for key-value match
                if (inSection && line.trim().startsWith("- id:") && line.includes(task)) {
                    return i + 1; // Line numbers are 1-based
                }
            }

            return undefined; // Not found
        }

        if(visible?.includes("code")){
            const {section, id} = event.params;

            if (event.action === "create") {
                const {position, target} = event.params;

                const ID = "NEW_TASK";

                const task = YAML_UTILS.stringify({id: ID, type: ""});
                const result = YAML_UTILS.insertTask(source.value, target, task, position);

                // Add the new task
                editorUpdate(result);

                nextTick(() => {
                    // Focus the newly created task
                    highlight.value = findLineNumber(section, ID, result)
                });
            }
            else if(event.action === "edit"){
                highlight.value = findLineNumber(section, id)
            }
        }
    }, {deep: true});

    export interface EditorTabProps{
        name: string,
        path: string,
        extension?: string,
        flow?: boolean,
        dirty?: boolean,
    }

    const props = withDefaults(defineProps<EditorTabProps>(), {
        extension: undefined,
        dirty: false,
        flow: true
    });

    const source = computed(() => {
        return props.flow
            ? store.getters["flow/flowYaml"]
            : store.state.editor.tabs.find((t:any) => t.path === props.path)?.content;
    })

    async function loadFile(){
        if(props.dirty || props.flow){
            return;
        }
        const content = await store.dispatch("namespace/readFile", {
            namespace: namespace.value,
            path: props.path
        })
        store.commit("editor/setTabContent", {
            path: props.path,
            content
        })
    }

    onMounted(() => {
        loadFile()
    });

    onActivated(() => {
        loadFile()
    });

    const editorDomElement = ref<any>(null);

    const namespace = computed(() => store.getters["flow/namespace"]);
    const flowStore = computed(() => store.getters["flow/flow"]);
    const isCreating = computed(() => store.state.flow.isCreating);
    const isCurrentTabFlow = computed(() => props.flow)
    const isReadOnly = computed(() => flowStore.value?.deleted || !store.getters["flow/isAllowedEdit"] || store.getters["flow/readOnlySystemLabel"])

    const timeout = ref<any>(null);

    function editorUpdate(newValue: string){
        if(store.state.editor.tabs.find((t:any) => t.path === props.path)?.content === newValue){
            return;
        }
        if(isCurrentTabFlow.value){
            store.commit("flow/setFlowYaml", newValue);
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


    function updatePluginDocumentation(event: string | undefined, task: any){
        store.dispatch("plugin/updateDocumentation", {event,task});
    };

    function save(){
        store.commit("editor/setCurrentTab", store.state.editor.tabs.find((t:any) => t.path === props.path));
        return store.dispatch("flow/save", {
            content: editorDomElement.value.$refs.monacoEditor.value,
        })
    }

    const execute = () => {
        store.commit("flow/executeFlow", true);
    };
</script>