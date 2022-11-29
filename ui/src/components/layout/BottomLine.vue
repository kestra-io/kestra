<template>
    <nav class="bottom-line">
        <slot />
    </nav>
</template>
<script>
    export default {}
</script>
<style lang="scss">
    @use 'element-plus/theme-chalk/src/mixins/function' as *;

    .bottom-line {
        position: fixed;
        bottom: 0;
        right: 0;
        left: 0px;
        border-radius: 0;
        border-top: 1px solid getCssVar('border-color');
        z-index: 90;
        background-color: getCssVar('color', 'white');
        padding: 0.5rem 1rem;
        text-align: right;

        html.dark & {
            background-color: getCssVar('gray-200');
        }

        button {
            margin-left: getCssVar('spacer');

            span:first-child {
                margin-right: calc(getCssVar('spacer') / 3);
            }
        }

        ul {
            display: flex;
            list-style: none;
            margin: 0;
            flex-wrap: nowrap;
            padding: 0;
            justify-content: flex-end;
        }
    }
</style>
