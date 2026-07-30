<template>
    <div class="task-nested" :class="{'task-nested--bare': bare}">
        <TaskObject
            v-bind="$attrs"
            :properties="computedProperties"
            :schema
            merge
        />
    </div>
</template>

<style scoped lang="scss">
.task-nested {
    border-left: 2px solid var(--ks-border-subtle);
    padding-left: var(--ks-spacing-4);
}

.task-nested--bare {
    border-left: none;
    padding-left: 0;
}
</style>

<script lang="ts" setup>
    import {computed, inject, ref} from "vue"
    import TaskObject from "./TaskObject.vue"
    import {resolve$ref} from "../../../../utils/utils"
    import {FULL_SCHEMA_INJECTION_KEY} from "../../injectionKeys"

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        schema: any,
        properties?: Record<string, any>,
        bare?: boolean,
    }>(), {
        properties: undefined,
        bare: false,
    })

    const fullSchema = inject(FULL_SCHEMA_INJECTION_KEY, ref({}))

    const computedProperties = computed(() => {
        if(!props.schema?.allOf && !props.schema?.$ref) {
            return props.schema?.properties || {}
        }
        const schemas = props.schema.allOf ?? [props.schema]
        return schemas.reduce((
            acc: Record<string, any>,
            item: {
                $ref?: string;
                properties?: Record<string, any>
            }) => {

            const i = resolve$ref(fullSchema.value, item)
            return {
                ...acc,
                ...i?.properties,
            }

        }, {})
    })
</script>
