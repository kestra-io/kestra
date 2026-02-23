<template>
    <TopNavBar v-if="topbar" :title="routeInfo.title" />
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
                        {{ $t("onboarding.welcome.headline") }}
                    </h2>
                    <div v-if="canCreate" class="welcome-actions">
                        <el-card class="action-card primary" @click="startGuided">
                            <div class="action-card-content">
                                <el-icon size="26">
                                    <component :is="Compass" />
                                </el-icon>
                                <div>
                                    <h5>
                                        {{ $t("onboarding.welcome.guided_title") }}
                                        <span class="action-label">{{ $t("onboarding.welcome.badge") }}</span>
                                    </h5>
                                    <p>{{ $t("onboarding.welcome.guided_description") }}</p>
                                    <span class="meta-time">{{ $t("onboarding.welcome.guided_duration") }}</span>
                                </div>
                            </div>
                        </el-card>
                        <el-card class="action-card" @click="startSelfServe">
                            <div class="action-card-content">
                                <el-icon size="26">
                                    <component :is="Plus" />
                                </el-icon>
                                <div>
                                    <h5>{{ $t("onboarding.welcome.self_serve_title") }}</h5>
                                    <p>{{ $t("onboarding.welcome.self_serve_description") }}</p>
                                    <span class="meta-time">{{ $t("onboarding.welcome.self_serve_note") }}</span>
                                </div>
                            </div>
                        </el-card>
                    </div>
                </div>
                <el-divider>
                    {{ $t("onboarding.welcome.additional_help") }}
                </el-divider>
                <div class="resources">
                    <el-link href="https://kestra.io/docs" target="_blank">
                        {{ $t("onboarding.welcome.docs") }}
                    </el-link>
                    <el-link href="https://kestra.io/docs/tutorial" target="_blank">
                        {{ $t("onboarding.welcome.tutorial") }}
                    </el-link>
                    <el-link href="https://kestra.io/blueprints" target="_blank">
                        {{ $t("onboarding.welcome.blueprints") }}
                    </el-link>
                    <el-link href="https://kestra.io/slack" target="_blank">
                        {{ $t("onboarding.welcome.slack") }}
                    </el-link>
                </div>
            </div>
        </div>
    </div>
</template>


<script setup lang="ts">
    import {computed} from "vue";
    import {useI18n} from "vue-i18n";
    import {useRouter} from "vue-router";
    import Plus from "vue-material-design-icons/Plus.vue";
    import Compass from "vue-material-design-icons/Compass.vue";
    import kestraWelcome from "../../assets/onboarding/kestra_welcome.svg";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import useRouteContext from "../../composables/useRouteContext";
    import useRestoreUrl from "../../composables/useRestoreUrl";
    import {useOnboardingAnalytics} from "../../composables/useOnboardingAnalytics";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import {useAuthStore} from "override/stores/auth";
    import {useOnboardingV2Store} from "../../stores/onboardingV2";

    const {topbar = true} = defineProps<{topbar?: boolean}>();

    const {t} = useI18n();
    const router = useRouter();
    const onboardingStore = useOnboardingV2Store();
    const {trackOnboarding} = useOnboardingAnalytics();

    const logo = computed(() => {
        return (localStorage.getItem("theme") || "light") === "light" ? kestraWelcome : kestraWelcome;
    });

    const routeInfo = computed(() =>  ({title: t("welcome_page.welcome")}));

    const authStore = useAuthStore();

    const canCreate = computed(() => {
        return authStore.user?.hasAnyActionOnAnyNamespace(permission.FLOW, action.CREATE);
    });

    useRouteContext(routeInfo);
    useRestoreUrl();

    const startGuided = () => {
        trackOnboarding({
            action: "start_guided_clicked",
            mode: "guided",
        });
        router.push({name: "flows/create", query: {onboarding: "guided"}});
    };

    const startSelfServe = () => {
        trackOnboarding({
            action: "start_self_serve_clicked",
            mode: "self_serve",
        });
        onboardingStore.startSelfServe();
        router.push({name: "flows/create"});
    };
</script>

<style scoped lang="scss">
    .main {
        padding: 6rem 1rem 1rem;
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
            padding: 6rem 2rem 1rem;
        }

        @media (min-width: 992px) {
            padding: 6rem 3rem 1rem;
        }

        @media (min-width: 1920px) {
            padding: 6rem 10rem 1rem;
        }
    }

    .img-fluid {
        max-width: 100%;
        height: auto;
    }

    .welcome-actions {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 1.25rem;
        width: 100%;
        max-width: 780px;
        margin-bottom: 1.5rem;
    }

    .action-card {
        cursor: pointer;
        border: 1px solid var(--ks-border-primary);
        background: var(--ks-background-card);
    }

    .action-card.primary {
        border-color: var(--el-color-primary);
        box-shadow: 0 0 0 1px color-mix(in srgb, var(--el-color-primary) 40%, transparent);
    }

    .action-card-content {
        display: flex;
        align-items: flex-start;
        gap: 0.75rem;
    }

    .action-card-content h5 {
        margin: 0 0 0.3rem;
        font-size: 1rem;
        color: var(--ks-content-primary);
    }

    .action-card-content .action-label {
        display: inline-block;
        margin-left: 0.5rem;
        padding: 0.16rem 0.5rem;
        border-radius: 999px;
        font-size: 0.75rem;
        font-weight: 700;
        color: color-mix(in srgb, var(--el-color-primary) 85%, black);
        background: color-mix(in srgb, var(--el-color-primary) 22%, transparent);
        border: 1px solid color-mix(in srgb, var(--el-color-primary) 45%, transparent);
        vertical-align: middle;
        letter-spacing: 0.01em;

        html.dark & {
            color: color-mix(in srgb, var(--el-color-primary) 45%, white);
            background: color-mix(in srgb, var(--el-color-primary) 32%, transparent);
            border-color: color-mix(in srgb, var(--el-color-primary) 62%, transparent);
        }
    }

    .action-card-content p {
        margin: 0;
        font-size: 0.87rem;
        color: var(--ks-content-secondary);
    }

    .action-card-content .meta-time {
        display: inline-block;
        margin-top: 0.35rem;
        font-size: 0.78rem;
        color: var(--ks-content-tertiary);
    }

    .main .section-1 {
        display: flex;
        flex-grow: 1;
        justify-content: center;
        align-items: flex-start;
        padding-top: 2rem;
        border-radius: var(--bs-border-radius);
    }
    .section-1-main {
        .section-content {
            width: 100%;
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 1.25rem;

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
                margin-bottom: 0.5rem;
            }
        }
    }

    :deep(.el-divider__text) {
        color: var(--ks-content-secondary);
        white-space: nowrap;
        font-size: var(--el-font-size-extra-small);
    }

    .resources {
        display: flex;
        gap: 1rem;
        justify-content: center;
        flex-wrap: wrap;
        margin-top: 0.5rem;
    }


    @container (max-width: 20px) {
        .main .section-1 .section-1-main {
            width: 90%;
        }
    }

    @media (max-width: 900px) {
        .welcome-actions {
            grid-template-columns: 1fr;
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
