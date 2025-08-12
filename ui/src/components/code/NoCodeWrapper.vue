<template>
    <NoCode
        :flow="lastValidFlowYaml"
        :parent-path="parentPath"
        :ref-path="refPath"
        :creating-task="creatingTask"
        :editing-task="editingTask"
        :field-name="fieldName"
        :position
        :block-schema-path="blockSchemaPath"
        @update-task="(e) => editorUpdate(e)"
        @reorder="(yaml) => flowStore.flowYaml = yaml"
        @close-task="() => emit('closeTask')"
    />
</template>

<script setup lang="ts">
    import {computed, provide, ref, watch} from "vue";
    import debounce from "lodash/debounce";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import NoCode from "./NoCode.vue";
    import {CREATE_TASK_FUNCTION_INJECTION_KEY, EDIT_TASK_FUNCTION_INJECTION_KEY} from "./injectionKeys";
    import {useEditorStore} from "../../stores/editor";
    import {useFlowStore} from "../../stores/flow";

    export interface NoCodeProps {
        creatingTask?: boolean;
        editingTask?: boolean;
        parentPath?: string;
        refPath?: number;
        position?: "before" | "after";
        blockSchemaPath?: string;
        fieldName?: string | undefined;
    }

    defineProps<NoCodeProps>();

    const emit = defineEmits<{
        (e: "createTask", parentPath: string, blockSchemaPath: string, refPath: number | undefined,  position: "after" | "before"): boolean | void;
        (e: "editTask", parentPath: string, blockSchemaPath: string, refPath?: number): boolean | void;
        (e: "closeTask"): boolean | void;
    }>();

    provide(CREATE_TASK_FUNCTION_INJECTION_KEY, (parentPath, blockSchemaPath, refPath) => {
        emit("createTask", parentPath, blockSchemaPath, refPath, "after")
    });

    provide(EDIT_TASK_FUNCTION_INJECTION_KEY, ( parentPath, blockSchemaPath, refPath) => {
        emit("editTask", parentPath, blockSchemaPath, refPath)
    });

    const flowStore = useFlowStore();
    const flowYaml = computed<string>(() => flowStore.flowYaml ?? "");

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
        flowStore.validateFlow({flow: flowYaml.value});
    }, 500);

    const timeout = ref();
    const editorStore = useEditorStore();

    const editorUpdate = (source: string) => {
        flowStore.flowYaml = source;
        flowStore.haveChange = true;
        validateFlow();
        editorStore.setTabDirty({
            name: "Flow",
            dirty: true
        });

        // throttle the trigger of the flow update
        clearTimeout(timeout.value);
        timeout.value = setTimeout(() => {
            flowStore.onEdit({
                source,
                currentIsFlow: true,
                topologyVisible: true,
            });
        }, 1000);
    };

    watch(
        () => flowStore.flowYaml,
        (newVal, oldVal) => {
            if (newVal !== oldVal) {
                editorUpdate(newVal);
            }
        }
    );
</script>