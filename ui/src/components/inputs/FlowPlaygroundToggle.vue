<template>
    <KsTooltip placement="bottom" :content="$t('playground.tooltip_persistence')">
        <KsSwitch v-model="playgroundStore.enabled" :activeText="$t('playground.toggle')" class="toggle" :class="{'is-active': playgroundStore.enabled}" />
    </KsTooltip>
</template>

<script setup lang="ts">
    import {usePlaygroundStore} from "../../stores/playground"

    const playgroundStore = usePlaygroundStore()
</script>

<style scoped lang="scss">
.toggle{
    margin-right: 1rem;
    &.is-active{
        --kel-switch-border-color: var(--ks-border-active);
        ::v-deep(.kel-switch__label){
            color: var(--ks-text-primary);
        }
    }
}
</style>
