<template>
    <span>{{ props.elements }}</span>
</template>

<script setup lang="ts">
    import type cytoscape from "cytoscape";

    const props = defineProps<{ elements: cytoscape.ElementDefinition[] }>();
</script>
