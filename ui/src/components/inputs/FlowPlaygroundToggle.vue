<template>
    <KsTooltip placement="bottom" :content="$t('playground.tooltip_persistence')">
        <KsSwitch v-model="playgroundStore.enabled" :disabled="unreachable && !playgroundStore.enabled" :activeText="$t('playground.toggle')" class="toggle" :class="{'is-active': playgroundStore.enabled}" />
    </KsTooltip>
</template>

<script setup lang="ts">
    import {usePlaygroundStore} from "../../stores/playground"
    import {useServerReachability} from "../../composables/useServerReachability"

    const playgroundStore = usePlaygroundStore()
    const {unreachable} = useServerReachability()
</script>

<style scoped>
.toggle{
    margin-right: 1rem;
    &.is-active{
        --kel-switch-border-color: var(--ks-toggle-active);
    }
}
</style>
