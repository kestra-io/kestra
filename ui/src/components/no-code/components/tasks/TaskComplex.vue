<template>
    <TaskObject
        :properties="computedProperties"
        :schema
        merge
    />
</template>

<script lang="ts" setup>
    import {computed, inject, ref} from "vue";
    import TaskObject from "./TaskObject.vue";
    import {resolve$ref} from "../../../../utils/utils";
    import {FULL_SCHEMA_INJECTION_KEY} from "../../injectionKeys";

    const props = withDefaults(defineProps<{
        schema: any,
        properties?: Record<string, any>,
    }>(), {
        properties: undefined,
    });

    const fullSchema = inject(FULL_SCHEMA_INJECTION_KEY, ref({}));

    const computedProperties = computed(() => {
        if(!props.schema?.allOf && !props.schema?.$ref) {
            return props.schema?.properties || {};
        }
        const schemas = props.schema.allOf ?? [props.schema];
        return schemas.reduce((
            acc: Record<string, any>,
            item: {
                $ref?: string;
                properties?: Record<string, any>
            }) => {

            const i = resolve$ref(fullSchema.value, item);
            return {
                ...acc,
                ...i?.properties
            };

        }, {});
    })
</script>
