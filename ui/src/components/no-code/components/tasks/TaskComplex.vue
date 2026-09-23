<template>
    <div class="task-nested" :class="{'task-nested--bare': bare}">
        <TaskObject
            v-bind="$attrs"
            :properties="computedProperties"
            :schema="computedSchema"
            merge
        />
    </div>
</template>

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

    const resolvedAllOfSchemas = computed(() => {
        if (!props.schema?.allOf && !props.schema?.$ref) return []
        const schemas = props.schema.allOf ?? [props.schema]
        return schemas.map((item: {$ref?: string; properties?: Record<string, any>; required?: string[]}) =>
            resolve$ref(fullSchema.value, item),
        )
    })

    const computedProperties = computed(() => {
        if (!resolvedAllOfSchemas.value.length) {
            return props.schema?.properties || {}
        }
        return resolvedAllOfSchemas.value.reduce((acc: Record<string, any>, item: {properties?: Record<string, any>}) => ({
            ...acc,
            ...item?.properties,
        }), {})
    })

    const computedRequired = computed(() =>
        resolvedAllOfSchemas.value.reduce((acc: string[], item: {required?: string[]}) => [
            ...acc,
            ...(item?.required ?? []),
        ], [] as string[]),
    )

    const computedSchema = computed(() =>
        computedRequired.value.length ? {...props.schema, required: computedRequired.value} : props.schema,
    )
</script>

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
