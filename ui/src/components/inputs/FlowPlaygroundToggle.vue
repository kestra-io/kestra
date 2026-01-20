<template>
    <el-tooltip placement="bottom" :content="$t('playground.tooltip_persistence')">
        <el-switch v-model="playgroundStore.enabled" :activeText="$t('playground.toggle')" class="toggle" :class="{'is-active': playgroundStore.enabled}" />
    </el-tooltip>
</template>

<script setup lang="ts">
    import {usePlaygroundStore} from "../../stores/playground";

    const playgroundStore = usePlaygroundStore();
</script>

<style scoped lang="scss">
.toggle{
    margin-right: 1rem;
    &.is-active ::v-deep(.el-switch__label){
        color: white;
    }
}
</style>
