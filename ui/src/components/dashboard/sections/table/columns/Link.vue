<template>
    <KsEntityLink
        v-if="linkData && props.flow"
        entity="flow"
        :value="label"
        :to="{name: 'flows/update', params: {namespace: linkData.NAMESPACE, id: linkData.FLOW_ID}}"
    />

    <RouterLink
        v-else-if="linkData && props.execution"
        :to="{
            name: 'executions/update',
            params: {
                namespace: linkData.NAMESPACE,
                flowId: linkData.FLOW_ID,
                id: label,
            },
        }"
    >
        <code class="link" :class="{colored: props.colored}">
            {{ label.slice(0, 8) }}
        </code>
    </RouterLink>

    <code v-else class="link" :class="{colored: props.colored}">{{ label }}</code>
</template>

<script setup lang="ts">
    import {PropType, computed} from "vue"

    const props = defineProps({
        execution: {type: Boolean, default: false},
        flow: {type: Boolean, default: false},
        colored: {type: Boolean, default: true},
        row: {type: Object as PropType<Record<string, any>>, required: true},
        field: {type: String, required: true},
        columns: {type: Object as PropType<Record<string, any>>, required: true},
    })

    const label = computed(() => props.row[props.field])

    const linkData = computed(() => {
        const result: Partial<Record<"FLOW_ID" | "NAMESPACE", any>> = {}

        for (const key in props.columns) {
            const config = props.columns[key]
            const fieldValue = props.row[key]

            if (config?.field === "FLOW_ID" || config?.field === "NAMESPACE") {
                result[config.field as "FLOW_ID" | "NAMESPACE"] = fieldValue
            }
        }

        return result.FLOW_ID && result.NAMESPACE ? result : undefined
    })
</script>

<style scoped>
.link {
    color: var(--ks-text-primary);
    font-size: var(--ks-font-size-sm);
}
.link.colored {
    color: var(--ks-text-link);
}
</style>
