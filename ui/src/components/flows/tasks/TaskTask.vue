<template>
    <div class="w-100">
        <Element
            :section="root"
            block-type="tasks"
            :parent-path-complete="parentPathComplete"
            :element="{
                id: model?.id ?? 'Set a task',
                type: model?.type,
            }"
            @remove-element="removeElement()"
        />
    </div>
</template>

<script setup lang="ts">
    import {computed, inject} from "vue";
    import {PARENT_PATH_INJECTION_KEY, REF_PATH_INJECTION_KEY, CREATING_TASK_INJECTION_KEY} from "../../code/injectionKeys";
    import Element from "../../code/components/collapse/Element.vue";

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


