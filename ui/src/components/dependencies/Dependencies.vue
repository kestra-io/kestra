<template>
    <el-splitter class="dependencies">
        <el-splitter-panel id="graph" v-bind="PANEL">
            <div ref="container" />
        </el-splitter-panel>

        <el-splitter-panel id="table">
            <Table :nodes @select="selectNode" />
        </el-splitter-panel>
    </el-splitter>
</template>

<script setup lang="ts">
    import {ref, computed} from "vue";

    const PANEL = {size: "70%", min: "30%", max: "80%"};

    import Table from "./components/Table.vue";

    import {options, useDependencies} from "./composables/useDependencies";

    import {NODE, type Node} from "../../../scripts/product/dependencies";

    const container = ref(null);
    const {selectNode} = useDependencies(container);

    const nodes = computed((): { data: Node }[] => {
        const elements = options.elements;

        if (!elements || !Array.isArray(elements)) return [];

        return elements.filter((element): element is { data: Node } => element.data.type === NODE);
    });
</script>

<style scoped lang="scss">
.dependencies {
    display: flex;
    width: 100%;
    height: calc(100vh - 135px);

    & div#graph > div {
        height: 100%;
        overflow: hidden scroll;
        background-color: transparent;
        background-image: radial-gradient(
            circle,
            var(--ks-dots-topology) 1px,
            transparent 1px
        );
        background-repeat: repeat;
        background-size: 24px 24px;
    }

    & div#table {
        display: flex;
        flex-direction: column;
        height: 100%;
    }
}
</style>
