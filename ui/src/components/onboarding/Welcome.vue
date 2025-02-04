<template>
    <top-nav-bar v-if="topbar" :title="routeInfo.title">
        <template #additional-right>
            <ul>
                <li>
                    <el-button v-if="canCreate" tag="router-link" :to="{name: 'flows/create', query: {namespace: $route.query.namespace}}" :icon="Plus" type="primary">
                        {{ $t('create_flow') }}
                    </el-button>
                </li>
            </ul>
        </template>
    </top-nav-bar>
    <div class="main">
        <div class="section-1">
            <div class="section-1-main">
                <div class="section-content">
                    <img
                        :src="logo"
                        alt="Kestra"
                        class="section-1-img img-fluid"
                        width="180px"
                    >
                    <h2 class="section-1-title">
                        {{ $t("homeDashboard.wel_text") }}
                    </h2>
                    <p class="section-1-desc">
                        {{ $t("homeDashboard.start") }}
                    </p>
                    <router-link :to="{name: 'flows/create'}">
                        <el-button
                            :icon="Plus"
                            size="large"
                            type="primary"
                            class="px-3 p-4 section-1-link product-link"
                        >
                            {{ $t("welcome button create") }}
                        </el-button>
                    </router-link>
                    <el-button
                        :icon="Play"
                        tag="a"
                        href="https://www.youtube.com/watch?v=a2BZ7vOihjg"
                        target="_blank"
                        class="p-3 px-4 mt-0 mb-lg-5 watch"
                    >
                        Watch Video
                    </el-button>
                </div>
                <el-divider>
                    {{ $t("homeDashboard.guide") }}
                </el-divider>
                <onboarding-bottom />
            </div>
        </div>
    </div>
</template>


<script setup>
    import Plus from "vue-material-design-icons/Plus.vue";
    import Play from "vue-material-design-icons/Play.vue";
</script>

<script>
    import {mapGetters, mapState} from "vuex";
    import OnboardingBottom from "./OnboardingBottom.vue";
    import kestraWelcome from "../../assets/onboarding/kestra_welcome.svg";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import RouteContext from "../../mixins/routeContext";
    import RestoreUrl from "../../mixins/restoreUrl";
    import permission from "../../models/permission";
    import action from "../../models/action";


    export default {
        name: "CreateFlow",
        mixins: [RouteContext, RestoreUrl],
        components: {
            OnboardingBottom,
            TopNavBar
        },
        props: {
            topbar: {
                type: Boolean,
                default: true
            }
        },
        computed: {
            ...mapGetters("core", ["guidedProperties"]),
            ...mapState("auth", ["user"]),
            logo() {
                // get theme
                return (localStorage.getItem("theme") || "light") === "light" ? kestraWelcome : kestraWelcome;
            },
            routeInfo() {
                return {
                    title: this.$t("homeDashboard.welcome")
                };
            },
            canCreate() {
                return this.user && this.user.hasAnyActionOnAnyNamespace(permission.FLOW, action.CREATE);
            }
        }
    }
</script>

<style scoped lang="scss">
    .main {
        padding: 3rem 1rem 1rem;
        background: var(--ks-background-body);
        background: radial-gradient(ellipse at top, rgba(102,51,255,0.6) 0%, rgba(253, 253, 253, 0) 20%);
        background-size: 4000px;
        background-position: center;
        height: 100%;
        width: auto;
        display: flex;
        flex-direction: column;
        container-type: inline-size;

        @media (min-width: 768px) {
            padding: 3rem 2rem 1rem;
        }

        @media (min-width: 992px) {
            padding: 3rem 3rem 1rem;
        }

        @media (min-width: 1920px) {
            padding: 3rem 10rem 1rem;
        }
    }

    .img-fluid {
        max-width: 100%;
        height: auto;
    }

    .product-link, .watch {
        font-weight: 700;
        border-radius: 5px;
        text-decoration: none;
        font-size: var(--el-font-size-small);
        width: 200px;
        margin-bottom: 1rem;
    }

    .watch {
        font-weight: 500;
        background-color: var(--ks-button-background-secondary);
        font-size: var(--el-font-size-small);
    }

    .main .section-1 {
        display: flex;
        flex-grow: 1;
        justify-content: center;
        align-items: center;
        border-radius: var(--bs-border-radius);
    }
    .section-1-main {
        .section-content {
            width: 100%;
            display: flex;
            flex-direction: column;
            align-items: center;

            .section-1-title {
                line-height: var(--el-font-line-height-primary);
                text-align: center;
                font-size: var(--el-font-size-extra-large);
                font-weight: 600;
                color: var(--ks-content-primary);
            }

            .section-1-desc {
                line-height: var(--el-font-line-height-primary);
                font-weight: 500;
                font-size: 1rem;
                text-align: center;
                color: var(--ks-content-primary);
            }
        }
    }

    :deep(.el-divider__text) {
        color: var(--ks-content-secondary);
        white-space: nowrap;
        font-size: var(--el-font-size-extra-small);
    }

    @container (max-width: 20px) {
        .main .section-1 .section-1-main {
            width: 90%;
        }
    }

    @container (max-width: 50px) {
        .main .section-1 .section-1-main {
            padding-top: 30px;
        }

        .section-1 .section-1-main .container {
            width: 76%;
        }
    }

</style>