<template>
    <top-nav-bar :title="routeInfo.title" v-if="!isFullScreen()" />
    <EmptyTemplate>
        <img :src="sourceImg" :alt="$t('errors.' + code + '.title')" class="img">
        <h2>{{ $t("errors." + code + ".title") }}</h2>

        <p>
            <span v-html="$t('errors.' + code + '.content')" />
        </p>

        <el-button v-if="!isFullScreen()" tag="router-link" :to="{name: 'home'}" type="primary" size="large">
            {{ $t("back_to_dashboard") }}
        </el-button>
    </EmptyTemplate>
</template>

<script>
    import RouteContext from "../../mixins/routeContext";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import EmptyTemplate from "../../components/layout/EmptyTemplate.vue";
    import sourceImg from "../../assets/errors/kestra-error.png";

    export default {
        mixins: [RouteContext],
        components: {TopNavBar, EmptyTemplate},
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
            sourceImg() {
                return sourceImg;
            }
        },
        methods: {
            isFullScreen() {
                return document.getElementsByTagName("html")[0].classList.contains("full-screen");
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

    .img {
        margin-top: 7rem;
        max-height: 156px;
    }

    h2 {
        line-height: 30px;
        font-size: 20px;
        font-weight: 600;
    }

    p {
        line-height: 22px;
        font-size: 14px;
    }

</style>
