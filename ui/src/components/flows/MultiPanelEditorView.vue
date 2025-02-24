<template>
    <Splitpanes>
        <pane v-for="i in 3" :key="i">
            <div>{{ i }}</div>
        </pane>
    </Splitpanes>
</template>

<script setup lang="ts">
    import "splitpanes/dist/splitpanes.css"
    import {Splitpanes, Pane} from "splitpanes"
</script>