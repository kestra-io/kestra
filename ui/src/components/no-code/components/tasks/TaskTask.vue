<template>
    <div class="w-100">
        <Element
            :section="root"
            :parentPathComplete="parentPathComplete"
            :blockSchemaPath="[blockSchemaPath, 'properties', root.split('.').pop()].join('/')"
            :element="{
                id: model?.id ?? 'Set a task',
                type: model?.type,
            }"
            typeFieldSchema="type"
            @remove-element="removeElement()"
        />
    </div>
</template>

<script setup lang="ts">
    import {computed, inject, ref} from "vue";
    import {
        PARENT_PATH_INJECTION_KEY,
        REF_PATH_INJECTION_KEY,
        CREATING_TASK_INJECTION_KEY,
        BLOCK_SCHEMA_PATH_INJECTION_KEY
    } from "../../injectionKeys";
    import Element from "./taskList/Element.vue";

    const model = defineModel({
        type: Object,
        default: () => ({})
    });

    const props = defineProps({
        root: {
            type: String,
            required: true
        },
    });

    const parentPath = inject(PARENT_PATH_INJECTION_KEY, "");
    const refPath = inject(REF_PATH_INJECTION_KEY, undefined);
    const creatingTask = inject(CREATING_TASK_INJECTION_KEY, false);
    const blockSchemaPath = inject(BLOCK_SCHEMA_PATH_INJECTION_KEY, ref())

    const parentPathComplete = computed(() => {
        return `${[
            [
                parentPath,
                creatingTask && refPath !== undefined
                    ? `[${refPath + 1}]`
                    : refPath !== undefined
                        ? `[${refPath}]`
                        : undefined,
            ].filter(Boolean).join(""),
            props.root,
        ].filter(p => p.length).join(".")}`;
    });

    function removeElement() {
        model.value = undefined;
    }
</script>


