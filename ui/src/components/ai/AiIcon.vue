<template>
    <span class="icon" />
</template>

<style scoped lang="scss">
    .icon {
        height: var(--ks-font-size-lg);
        min-width: var(--ks-font-size-lg);
        background: center url("../../assets/icons/ai-agent.svg#file");
        background-repeat: no-repeat;

        html.light & {
            background: center url("../../assets/icons/ai-agent-light.svg#file");
        }
    }
</style>
