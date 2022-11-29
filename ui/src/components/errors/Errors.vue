<template>
    <div class="errors">
        <div class="img" />
        <h2>{{ $t("errors." + this.code + ".title") }}</h2>

        <p>
            <span v-html="$t('errors.' + this.code + '.content')" />
        </p>
    </div>
</template>

<script>
    import RouteContext from "../../mixins/routeContext";

    export default {
        mixins: [RouteContext],
        props: {
            code : {
                type: Number,
                required: true
            }
        },
        computed: {
            routeInfo() {
                return {
                    title: this.$t("errors." + this.code + ".title"),
                };
            },
        },
        watch: {
            $route() {
                this.$store.commit("core/setError", undefined);
            }
        },

    };
</script>


<style lang="scss" scoped>
    @use 'element-plus/theme-chalk/src/mixins/function' as *;

    .errors {
        h2 {
            margin-bottom: calc(getCssVar('spacer') * 2);
        }

        width: 100%;
        text-align: center;
        .img {
            display: inline-block;
            background: url("../../assets/errors/sorry.svg") no-repeat;
            background-size: contain;
            height: 300px;
            width: 300px;
        }
    }

</style>
