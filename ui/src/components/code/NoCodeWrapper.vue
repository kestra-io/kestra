<template>
    <NoCode
        :flow="lastValidFlowYaml"
        :parent-path="parentPath"
        :ref-path="refPath"
        :block-type="blockType"
        :creating-task="creatingTask"
        :editing-task="editingTask"
        :field-name="fieldName"
        :position
        @update-metadata="(e) => onUpdateMetadata(e)"
        @update-task="(e) => editorUpdate(e)"
        @reorder="(yaml) => store.commit('flow/setFlowYaml', yaml)"
        @close-task="() => emit('closeTask')"
    />
</template>

<script setup lang="ts">
    import {computed, provide, ref} from "vue";
    import debounce from "lodash/debounce";
    import {useStore} from "vuex";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import NoCode from "./NoCode.vue";
    import {BlockType} from "./utils/types";
    import {CREATE_TASK_FUNCTION_INJECTION_KEY, EDIT_TASK_FUNCTION_INJECTION_KEY} from "./injectionKeys";

    export interface NoCodeProps {
        creatingTask?: boolean;
        editingTask?: boolean;
        blockType?: BlockType | "pluginDefaults";
        parentPath?: string;
        refPath?: number;
        position?: "before" | "after";
        fieldName?: string | undefined;
    }

    defineProps<NoCodeProps>();

    const emit = defineEmits<{
        (e: "createTask", blockType: string, parentPath: string, refPath: number | undefined, position: "after" | "before"): boolean | void;
        (e: "editTask", blockType: string, parentPath: string, refPath?: number): boolean | void;
        (e: "closeTask"): boolean | void;
    }>();

    provide(CREATE_TASK_FUNCTION_INJECTION_KEY, (blockType, parentPath, refPath) => {
        emit("createTask", blockType, parentPath, refPath, "after")
    });
    provide(EDIT_TASK_FUNCTION_INJECTION_KEY, (blockType, parentPath, refPath) => {
        emit("editTask", blockType, parentPath, refPath)
    });

    const store = useStore();
    const flowYaml = computed<string>(() => store.getters["flow/flowYaml"]);

    const lastValidFlowYaml = computed<string>(
        (oldValue) => {
            try {
                YAML_UTILS.parse(flowYaml.value);
                return flowYaml.value;
            } catch {
                return oldValue ?? "";
            }
        }
    );

    const validateFlow = debounce(() => {
        store.dispatch("flow/validateFlow", {flow: flowYaml.value});
    }, 500);

    const onUpdateMetadata = (metadata: any) => {
        store.commit("flow/setMetadata", {
            ...metadata.value,
            ...((metadata.concurrency?.limit ?? -1) === 0 ? {
                concurrency: null
            } : metadata)});
        store.dispatch("flow/onSaveMetadata");
        validateFlow()
        store.commit("editor/setTabDirty", {
            name: "Flow",
            dirty: true
        });
    };

    const timeout = ref();

    const editorUpdate = (source: string) => {
        store.commit("flow/setFlowYaml", source);
        store.commit("flow/setHaveChange", true);
        validateFlow();
        store.commit("editor/setTabDirty", {
            name: "Flow",
            dirty: true
        });

        // throttle the trigger of the flow update
        clearTimeout(timeout.value);
        timeout.value = setTimeout(() => {
            store.dispatch("flow/onEdit", {
                source,
                currentIsFlow: true,
                topologyVisible: true,
            });
        }, 1000);
    };
</script>