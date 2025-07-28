<template>
    <span class="icon" />
</template>

<style scoped lang="scss">
    .icon {
        height: 20px;
        width: 20px;
        display: inline-flex;
        align-self: center;
        justify-self: center;
        background: center url("../../assets/icons/ai-agent.svg#file");

        html.light & {
            background: center url("../../assets/icons/ai-agent-light.svg#file");
        }
    }
</style>
