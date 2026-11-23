<template>
    <template v-for="(segment, index) in segments" :key="index"><mark v-if="segment.matched">{{ segment.text }}</mark><template v-else>{{ segment.text }}</template></template>
</template>

<script setup lang="ts">
    import type {HighlightSegment} from "../../utils/crossResourceSearch"

    defineProps<{
        segments: HighlightSegment[]
    }>()
</script>
