<template>
    <section id="welcome-success" class="container">
        <el-row justify="center">
            <el-col :xs="24" :sm="22" :md="18" :lg="16" :xl="14">
                <div class="welcome-success-hero">
                    <div class="welcome-success-icon">
                        <CheckBold />
                    </div>

                    <h1>{{ $t("welcome_copilot.success_page.title") }}</h1>
                    <p>{{ $t("welcome_copilot.success_page.description") }}</p>
                </div>

                <div class="welcome-success-list">
                    <component
                        :is="item.href ? 'a' : 'router-link'"
                        v-for="item in successItems"
                        :key="item.titleKey"
                        class="welcome-success-item"
                        :href="item.href"
                        :to="item.to"
                        :target="item.href ? '_blank' : undefined"
                        :rel="item.href ? 'noreferrer' : undefined"
                    >
                        <div class="welcome-success-item__icon" :class="item.iconClass">
                            <component :is="item.icon" />
                        </div>

                        <div class="welcome-success-item__content">
                            <h3>{{ $t(item.titleKey) }}</h3>
                            <p>{{ $t(item.descriptionKey) }}</p>
                        </div>

                        <ChevronRight class="welcome-success-item__arrow" />
                    </component>
                </div>

                <div class="welcome-success-actions">
                    <router-link
                        class="el-button"
                        :to="{name: 'welcome'}"
                    >
                        {{ $t("welcome_copilot.success_page.restart") }}
                    </router-link>
                </div>
            </el-col>
        </el-row>
    </section>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import {useI18n} from "vue-i18n";
    import {useRoute} from "vue-router";
    import CheckBold from "vue-material-design-icons/CheckBold.vue";
    import PlayBoxMultiple from "vue-material-design-icons/PlayBoxMultiple.vue";
    import BookOpenVariant from "vue-material-design-icons/BookOpenVariant.vue";
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue";
    import Monitor from "vue-material-design-icons/Monitor.vue";
    import VideoInputComponent from "vue-material-design-icons/VideoInputComponent.vue";

    import SlackLogo from "./components/SlackLogo.vue";
    import useRouteContext from "../../composables/useRouteContext";

    const {t} = useI18n();
    const route = useRoute();

    const routeInfo = computed(() => ({title: t("welcome_copilot.success_page.title")}));
    useRouteContext(routeInfo);
    const tutorialRoute = computed(() => ({
        name: "flows/create",
        query: {onboarding: "guided", reset: "true"},
        params: {tenant: route.params.tenant},
    }));

    const successItems = [
        {
            titleKey: "welcome_copilot.success_page.items.tutorial.title",
            descriptionKey: "welcome_copilot.success_page.items.tutorial.description",
            icon: PlayBoxMultiple,
            iconClass: "is-tutorial",
            to: tutorialRoute.value,
        },
        {
            titleKey: "welcome_copilot.success_page.items.blueprints.title",
            descriptionKey: "welcome_copilot.success_page.items.blueprints.description",
            icon: BookOpenVariant,
            iconClass: "is-blueprints",
            to: {name: "blueprints", params: {kind: "flow", tab: "all"}},
        },
        {
            titleKey: "welcome_copilot.success_page.items.slack.title",
            descriptionKey: "welcome_copilot.success_page.items.slack.description",
            icon: SlackLogo,
            iconClass: "is-slack",
            href: "https://kestra.io/slack",
        },
        {
            titleKey: "welcome_copilot.success_page.items.videos.title",
            descriptionKey: "welcome_copilot.success_page.items.videos.description",
            icon: VideoInputComponent,
            iconClass: "is-videos",
            href: "https://kestra.io/tutorial-videos/all",
        },
        {
            titleKey: "welcome_copilot.success_page.items.demo.title",
            descriptionKey: "welcome_copilot.success_page.items.demo.description",
            icon: Monitor,
            iconClass: "is-demo",
            href: "https://kestra.io/demo",
        },
    ] as const;
</script>

<style scoped lang="scss">
@import "@kestra-io/ui-libs/src/scss/_variables.scss";

#welcome-success {
    padding-top: 3rem;
    padding-bottom: 3rem;

    .welcome-success-hero {
        display: flex;
        flex-direction: column;
        align-items: center;
        text-align: center;
        margin-bottom: 2rem;

        h1 {
            margin: 1.5rem 0 1rem;
            color: var(--ks-content-primary);
            font-size: $font-size-lg;
            font-weight: 700;
        }

        p {
            max-width: 460px;
            margin: 0;
            color: var(--ks-content-secondary);
            font-size: $font-size-md;
            line-height: 1.5;
        }
    }

    .welcome-success-icon {
        display: grid;
        place-items: center;
        width: 68px;
        height: 68px;
        border-radius: 18px;
        background: linear-gradient(180deg, #35006c 0%, #240047 100%);
        color: #f2b8ff;

        :deep(svg) {
            width: 34px;
            height: 34px;
        }
    }

    .welcome-success-list {
        overflow: hidden;
        width: min(100%, 620px);
        margin: 0 auto;
        border: 1px solid var(--ks-border-primary);
        border-radius: 14px;
        background: var(--ks-background-card);
    }

    .welcome-success-item {
        display: flex;
        align-items: center;
        gap: 1rem;
        padding: 1.1rem 1.25rem;
        color: inherit;
        text-decoration: none;
        cursor: pointer;
        transition: background-color 0.15s ease;

        &:not(:last-child) {
            border-bottom: 1px solid var(--ks-border-primary);
        }

        &:hover {
            background: var(--ks-button-background-secondary);
            text-decoration: none;
        }
    }

    .welcome-success-item__icon {
        display: grid;
        place-items: center;
        width: 28px;
        height: 28px;
        flex-shrink: 0;

        &:deep(svg) {
            width: 22px;
            height: 22px;
        }

        &.is-tutorial {
            color: #4dabf7;
        }

        &.is-blueprints {
            color: #8b5cf6;
        }

        &.is-slack {
            color: #22c55e;
        }

        &.is-videos {
            color: #f87171;
        }

        &.is-demo {
            color: #fb923c;
        }
    }

    .welcome-success-item__content {
        flex: 1;
        min-width: 0;

        h3 {
            margin: 0 0 0.25rem;
            color: var(--ks-content-primary);
            font-size: $font-size-sm;
            font-weight: 600;
        }

        p {
            margin: 0;
            color: var(--ks-content-secondary);
            font-size: $font-size-sm;
            line-height: 1.4;
        }
    }

    .welcome-success-item__arrow {
        color: var(--ks-content-tertiary);
        flex-shrink: 0;
    }

    .welcome-success-actions {
        display: flex;
        justify-content: center;
        margin-top: 2rem;
    }
}
</style>
