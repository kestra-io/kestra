<template>
    <RouterLink v-if="to" :to>
        <code class="link">{{ props.node.flow }}</code>
    </RouterLink>

    <code v-else class="link">{{ props.node.flow }}</code>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    
    import {FLOW, EXECUTION, type Node} from "../utils/types";

    const props = defineProps<{ node: Node, subtype: typeof FLOW | typeof EXECUTION}>();

    const to = computed(() => {
        const base = {namespace: props.node.namespace};
        return {name: "flows/update", params: {...base, id: props.node.flow}};
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
