<template>
    <el-card>
        <div ref="cyContainer">
            <Background />
        </div>
    </el-card>
</template>

<script setup lang="ts">
    import {ref} from "vue";

    import {Background} from "@vue-flow/background";

    const cyContainer = ref(null);

    import {useDependencies} from "./composables/useDependencies";
    useDependencies(cyContainer);
</script>

<style scoped lang="scss">
.el-card {
    height: calc(100vh - 183px);

    & :deep(.el-card__body) {
        height: calc(100vh - 183px);

        & div {
            height: -webkit-fill-available;
            width: 100%;
        }
    }
}
</style>
