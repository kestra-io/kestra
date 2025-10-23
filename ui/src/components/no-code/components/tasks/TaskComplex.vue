<template>
    <TaskObject
        :properties="computedProperties"
        :schema
        :definitions
        merge
    />
</template>

<script lang="ts" setup>
    import {computed} from "vue";
    import TaskObject from "./TaskObject.vue";

    const props = withDefaults(defineProps<{
        schema: any,
        definitions?: Record<string, any>,
        properties?: Record<string, any>,
    }>(), {
        definitions: () => ({}),
        properties: undefined,
    });

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

            if (item.$ref) {
                const type = item.$ref.split("/").pop()!;
                return {
                    ...acc,
                    ...props.definitions[type]?.properties
                };
            }
            return {
                ...acc,
                ...item.properties
            };

        }, {});
    })
</script>
