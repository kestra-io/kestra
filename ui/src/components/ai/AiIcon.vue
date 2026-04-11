<template>
    <span class="icon" />
</template>

<style scoped lang="scss">
    .icon {
        height: var(--kel-font-size-large);
        min-width: var(--kel-font-size-large);
        background: center url("../../assets/icons/ai-agent.svg#file");
        background-repeat: no-repeat;

        html.light & {
            background: center url("../../assets/icons/ai-agent-light.svg#file");
        }
    }
</style>
