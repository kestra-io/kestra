<template>
    <div class="logo">
        <router-link v-if="link" :to="{name: 'home'}">
            <span class="img" />
        </router-link>
        <span v-else class="img" />
    </div>
</template>

<script>
    export default {
        props: {
            link: {
                type: Boolean,
                default: false
            }
        }
    }
</script>

<style scoped lang="scss">
    .logo {
        width: 100%;

        span.img {
            width: 100%;
            height: 0;
            padding-top: 23.0842912%;
            background: url(../../../src/assets/logo.svg) 0 0 no-repeat;
            background-size: contain;
            display: block;
            overflow: unset;


            html.dark & {
                background: url(../../../src/assets/logo-white.svg) 0 0 no-repeat;
            }
        }
        a {
            position: absolute;
            width: 100%;
            height: 100%;
        }
    }
</style>