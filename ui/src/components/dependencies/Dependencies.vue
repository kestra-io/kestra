<template>
    <div class="dependencies">
        <section id="graph" ref="container" :style="{width: left + '%'}" />

        <Handle :left @update:left="left = $event" />

        <section id="table" :style="{width: 100 - left + '%'}">
            <Table :elements="options.elements" />
        </section>
    </div>
</template>

<script setup lang="ts">
    import {ref} from "vue";

    import Handle from "../global/drag/Handle.vue";
    import Table from "./components/Table.vue";

    const left = ref(70);

    import {options, useDependencies} from "./composables/useDependencies";

    const container = ref(null);
    useDependencies(container);
</script>

<style scoped lang="scss">
.dependencies {
    display: flex;
    width: 100%;
    height: calc(100vh - 135px);

    & section#graph, section#table {
        overflow: hidden scroll;
        height: 100%;
    }

    & section#graph {
        background-color: transparent;
        background-image: radial-gradient(circle, var(--ks-dots-topology) 1px, transparent 1px);
        background-repeat: repeat;
        background-size: 24px 24px;
    }
}
</style>
