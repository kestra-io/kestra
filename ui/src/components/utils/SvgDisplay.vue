<template>
    <div class="wrapper">
        <div class="icon" :style="styles" />
    </div>
</template>

<script>
    export default {
        name: "SvgDisplay",
        props: {
            encodedSvg: {
                type: String,
                default: undefined
            }
        },
        computed: {
            styles() {
                return {
                    backgroundImage: `url(data:image/svg+xml;base64,${this.encodedSvg})`
                }
            },
        }
    }
</script>

<style lang="scss" scoped>
    .wrapper {
        display: inline-block;
        width: 100%;
        height: 100%;
        position: relative;

        :deep(span) {
            position: absolute;
            padding: 1px;
            left: 0;
            display: block;
            width: 100%;
            height: 100%;
        }

        :deep(.icon) {
            width: 100%;
            height: 100%;
            display: block;
            background-size: contain;
            background-repeat: no-repeat;
            background-position: center center;
        }
    }
</style>