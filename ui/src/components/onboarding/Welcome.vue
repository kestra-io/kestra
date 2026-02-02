<template>
    <TopNavBar v-if="topbar" :title="routeInfo.title">
        <template #additional-right>
            <ul>
                <li>
                    <el-button v-if="canCreate" tag="router-link" :to="{name: 'flows/create', query: {namespace: $route.query.namespace}}" :icon="Plus">
                        {{ $t('create_flow') }}
                    </el-button>
                </li>
            </ul>
        </template>
    </TopNavBar>
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
                        {{ $t("welcome_page.wel_text") }}
                    </h2>
                    <p class="section-1-desc">
                        {{ $t("welcome_page.start") }}
                    </p>

                    <el-button
                        v-if="isOSS"
                        @click="startTour"
                        :icon="Compass"
                        size="large"
                        type="primary"
                        class="px-3 p-4 section-1-link product-link"
                    >
                        {{ $t("welcome button create") }}
                    </el-button>
                    <el-button
                        v-else
                        :icon="Compass"
                        tag="router-link"
                        :to="{name: 'flows/create'}"
                        size="large"
                        type="primary"
                        class="px-3 p-4 section-1-link product-link"
                    >
                        {{ $t("welcome button create") }}
                    </el-button>

                    <el-button
                        :icon="Play"
                        tag="a"
                        href="https://www.youtube.com/watch?v=waTpmiv4ZCs"
                        target="_blank"
                        class="p-3 px-4 mt-0 mb-lg-5 watch"
                    >
                        {{ $t("watch_video") }}
                    </el-button>
                </div>
                <el-divider>
                    {{ $t("welcome_page.guide") }}
                </el-divider>
                <OnboardingBottom />
            </div>
        </div>
    </div>
</template>


<script setup lang="ts">
    import {computed, getCurrentInstance} from "vue";
    import {useCoreStore} from "../../stores/core";
    import {useI18n} from "vue-i18n";
    import Plus from "vue-material-design-icons/Plus.vue";
    import Compass from "vue-material-design-icons/Compass.vue";
    import Play from "vue-material-design-icons/Play.vue";
    import OnboardingBottom from "override/components/OnboardingBottom.vue";
    import kestraWelcome from "../../assets/onboarding/kestra_welcome.svg";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import useRouteContext from "../../composables/useRouteContext";
    import useRestoreUrl from "../../composables/useRestoreUrl";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import {useAuthStore} from "override/stores/auth";
    import {useMiscStore} from "override/stores/misc";

    const {topbar = true} = defineProps<{topbar?: boolean}>();

    const coreStore = useCoreStore();
    const {t} = useI18n();
    const instance = getCurrentInstance();

    const logo = computed(() => {
        return (localStorage.getItem("theme") || "light") === "light" ? kestraWelcome : kestraWelcome;
    });

    const routeInfo = computed(() =>  ({title: t("welcome_page.welcome")}));

    const authStore = useAuthStore();

    const isOSS = computed(() => useMiscStore().configs?.edition === "OSS")

    const canCreate = computed(() => {
        return authStore.user.hasAnyActionOnAnyNamespace(permission.FLOW, action.CREATE);
    });

    useRouteContext(routeInfo);
    useRestoreUrl();

    const startTour = () => {
        localStorage.setItem("tourDoneOrSkip", "undefined");
        coreStore.guidedProperties = {
            ...coreStore.guidedProperties,
            tourStarted: true
        };
        (instance?.proxy as any)?.$tours["guidedTour"]?.start();
    };
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
        margin: 0;
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