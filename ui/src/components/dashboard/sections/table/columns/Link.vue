<template>
    <RouterLink
        v-if="linkData"
        :to="{
            name: props.execution ? 'executions/update' : props.flow ? 'flows/update' : undefined,
            params: {
                namespace: linkData.NAMESPACE,
                ...(props.execution && {flowId: linkData.FLOW_ID, id: label,}),
                ...(props.flow && {id: linkData.FLOW_ID,}),
            },
        }"
    >
        <code class="link">
            {{ props.execution ? label.slice(0, 8) : label }}
        </code>
    </RouterLink>

    <code v-else class="link">{{ label }}</code>
</template>

<script setup lang="ts">
    import {PropType, computed} from "vue";

    const props = defineProps({
        execution: {type: Boolean, default: false},
        flow: {type: Boolean, default: false},
        row: {type: Object as PropType<Record<string, any>>, required: true},
        field: {type: String, required: true},
        columns: {type: Object as PropType<Record<string, any>>, required: true},
    });

    const label = computed(() => props.row[props.field]);

    const linkData = computed(() => {
        const result: Partial<Record<"FLOW_ID" | "NAMESPACE", any>> = {};

        for (const key in props.columns) {
            const config = props.columns[key];
            const fieldValue = props.row[key];

            if (config?.field === "FLOW_ID" || config?.field === "NAMESPACE") {
                result[config.field as "FLOW_ID" | "NAMESPACE"] = fieldValue;
            }
        }

        return result.FLOW_ID && result.NAMESPACE ? result : undefined;
    });
</script>

<style scoped lang="scss">
code.link {
    color: var(--ks-content-id);
}
</style>
