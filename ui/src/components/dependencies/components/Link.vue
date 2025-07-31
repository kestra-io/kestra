<template>
    <RouterLink v-if="to" :to>
        <code class="link">{{ label }}</code>
    </RouterLink>

    <code v-else class="link">{{ label }}</code>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    
    import {FLOW, EXECUTION, type Node} from "../../../../scripts/product/dependencies";

    const props = defineProps<{ node: Node, subtype: typeof FLOW | typeof EXECUTION}>();

    const to = computed(() => {
        const base = {namespace: props.node.namespace};

        if (props.subtype === EXECUTION) {
            return {name: "executions/update", params: {...base, flowId: props.node.flow, id: props.node.id}};
        } else if (props.subtype === FLOW) {
            return {name: "flows/update", params: {...base, id: props.node.flow}};
        }

        // If no valid subtype, return undefined to avoid navigation
        return undefined;
    });

    const label = computed(() => {
        return props.subtype === EXECUTION ? props.node.id.slice(0, 8) : props.node.flow;
    });
</script>

<style scoped lang="scss">
code.link {
    display: block;
    max-width: 100%;
    font-size: var(--font-size-sm);
    color: var(--ks-content-id);
}
</style>
