<template>
    <top-nav-bar :title="routeInfo.title" />
    <section class="container errors">
        <div class="img" />
        <h2>{{ $t("errors." + code + ".title") }}</h2>

        <p>
            <span v-html="$t('errors.' + code + '.content')" />
        </p>
    </section>
</template>

<script>
    import RouteContext from "../../mixins/routeContext";
    import TopNavBar from "../../components/layout/TopNavBar.vue";

    export default {
        mixins: [RouteContext],
        components: {TopNavBar},
        props: {
            code: {
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
    .errors {
        h2 {
            margin-bottom: calc(var(--spacer) * 2);
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
