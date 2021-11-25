<template>
    <b-navbar class="bottom-line" fixed="bottom">
        <slot />
    </b-navbar>
</template>
<script>
    export default {}
</script>
<style lang="scss" >
    @import "../../styles/variable";

    .bottom-line {
        left: 50px;
        border-radius: 0;
        border-top: 1px solid var(--gray-300);
        z-index: 90;
        background-color: var(--white);

        .theme-dark & {
            background-color: var(--light);
        }

        button {
            margin-left: $spacer;

            span:first-child {
                margin-right: $spacer / 3;
            }
        }
    }
</style>
