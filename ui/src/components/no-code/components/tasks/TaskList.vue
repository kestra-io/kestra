<template>
    <div class="tasks-wrapper">
        <Collapse
            :title="root"
            :elements="items"
            :section
            :block-schema-path="[blockSchemaPath, 'properties', root, 'items'].join('/')"
            @remove="removeItem"
            @reorder="(yaml) => flowStore.flowYaml = yaml"
        />
    </div>
</template>

<script setup lang="ts">
    import {computed, inject, ref} from "vue";
    import Collapse from "../collapse/Collapse.vue";
    import {BLOCK_SCHEMA_PATH_INJECTION_KEY} from "../../injectionKeys";
    import {useFlowStore} from "../../../../stores/flow";

    const blockSchemaPath = inject(BLOCK_SCHEMA_PATH_INJECTION_KEY, ref(""))

    defineOptions({
        inheritAttrs: false
    });

    const flowStore = useFlowStore();

    interface Task {
        id:string,
        type:string
    }

    const emits = defineEmits(["update:modelValue"]);
    const props = withDefaults(defineProps<{
        modelValue?: Task[],
        root?: string;
    }>(), {
        modelValue: () => [],
        root: undefined
    });

    const items = computed(() =>
        !Array.isArray(props.modelValue) ? [props.modelValue] : props.modelValue,
    );

    function removeItem(yaml: string, index: number){
        flowStore.flowYaml = yaml;

        let localItems = [...items.value]
        localItems.splice(index, 1)

        emits("update:modelValue", localItems);
    };

    const section = computed(() => {
        return props.root ?? "tasks";
    });
</script>

<style scoped lang="scss">
@import "../../styles/code.scss";

.tasks-wrapper {
    width: 100%;
}

.disabled {
    opacity: 0.5;
    pointer-events: none;
    cursor: not-allowed;
}
</style>
