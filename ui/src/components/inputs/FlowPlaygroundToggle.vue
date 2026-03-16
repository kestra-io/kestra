<template>
    <ks-tooltip placement="bottom" :content="$t('playground.tooltip_persistence')">
        <ks-switch v-model="playgroundStore.enabled" :activeText="$t('playground.toggle')" class="toggle" :class="{'is-active': playgroundStore.enabled}" />
    </ks-tooltip>
</template>

<script setup lang="ts">
    import {usePlaygroundStore} from "../../stores/playground";

    const playgroundStore = usePlaygroundStore();
</script>

<style scoped lang="scss">
.toggle{
    margin-right: 1rem;
    &.is-active{
        --el-switch-border-color: #FFFFFF;
        ::v-deep(.el-switch__label){
            color: white;
        }
    }
}
</style>
