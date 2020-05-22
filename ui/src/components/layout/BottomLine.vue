<template>
    <b-navbar class="bottom-line" fixed="bottom" toggleable="lg">
        <slot/>
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
        border-top: 1px solid $gray-300;
        z-index: 90;
        background-color: $white;

        button {
            margin-left: $spacer;
        }
    }
</style>
