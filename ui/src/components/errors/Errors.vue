<template>
    <top-nav-bar :title="$t('errors.' + code + '.title')"/>
    <div class="errors">
        <div class="img" />
        <h2>{{ $t("errors." + code + ".title") }}</h2>

        <p>
            <span v-html="$t('errors.' + code + '.content')" />
        </p>
    </div>
</template>

<script>
    import TopNavBar from "../../components/layout/TopNavBar.vue";

    export default {
        components: {TopNavBar},
        props: {
            code: {
                type: Number,
                required: true
            }
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
