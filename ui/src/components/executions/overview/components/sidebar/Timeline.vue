<template>
    <el-timeline>
        <el-timeline-item
            v-for="(entry, hIdx) in props.histories"
            :key="hIdx"
            :timestamp="entry.date"
            :color="State.getStateColor(entry.state)"
        >
            {{ entry.state }}
        </el-timeline-item>
    </el-timeline>
</template>

<script setup lang="ts">
    import type {Histories} from "../../../../../stores/executions";

    import {State} from "@kestra-io/ui-libs";

    const props = defineProps<{ histories: Histories[] }>();
</script>
