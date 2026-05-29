<template>
    <KsSelect
        class="auth-selector"
        popperClass="user-select border border-0"
        placement="right-end"
        :popperOffset="20"
        :showArrow="false"
        :suffixIcon="ChevronRight"
        :placeholder="$t('kestra')"
    >
        <template #prefix>
            <img :src="KS_LOGO" width="24" height="24" alt="Kestra" class="user-avatar">
        </template>

        <template #label>
            {{ $t("kestra") }}
        </template>

        <template #header>
            <div class="menu-item">
                <img :src="KS_LOGO" width="40" alt="Kestra">
                {{ $t("kestra") }}
            </div>
        </template>

        <KsOption label="welcome" value="welcome">
            <RouterLink :to="startTutorial" class="menu-item">
                <RocketLaunchOutline class="menu-icon" />
                {{ $t("product_tour") }}
            </RouterLink>
        </KsOption>

        <KsOption label="settings" value="settings">
            <RouterLink :to="{name: 'preferences'}" class="menu-item">
                <CogOutline class="menu-icon" />
                {{ $t("settings.label") }}
            </RouterLink>
        </KsOption>

        <KsOption label="slack" value="slack">
            <a :href="SLACK_URL" target="_blank" class="menu-item">
                <Slack class="menu-icon" />
                {{ $t("join_slack") }}
            </a>
        </KsOption>

        <template #footer>
            <KsOption value="logout" class="list-unstyled" @click="logout">
                <div class="menu-item">
                    <Logout class="menu-icon" />
                    {{ $t("setup.logout") }}
                </div>
            </KsOption>
        </template>
    </KsSelect>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useRoute, useRouter} from "vue-router"

    import {useClient} from "@kestra-io/kestra-sdk"

    import ChevronRight from "vue-material-design-icons/ChevronRight.vue"
    import CogOutline from "vue-material-design-icons/CogOutline.vue"
    import Logout from "vue-material-design-icons/Logout.vue"
    import RocketLaunchOutline from "vue-material-design-icons/RocketLaunchOutline.vue"
    import Slack from "vue-material-design-icons/Slack.vue"

    import KS_LOGO from "../../../assets/ks-logo-small.svg"
    import * as BasicAuth from "../../../utils/basicAuth"

    const SLACK_URL = "https://kestra.io/slack?utm_source=app&utm_medium=referral&utm_campaign=top-auth"

    const route = useRoute()
    const router = useRouter()
    const axios = useClient()

    const startTutorial = computed(() => ({
        name: "flows/create",
        query: {onboarding: "guided", reset: "true"},
        params: {tenant: route.params.tenant},
    }))

    const logout = () => {
        BasicAuth.logout()
        delete axios.defaults.headers.common["Authorization"]
        router.push({name: "login"})
    }
</script>

<style scoped lang="scss">
    .menu-item {
        display: flex;
        align-items: center;
        gap: 1rem;
        color: var(--ks-text-primary);

        .menu-icon {
            color: var(--ks-text-dim);
            font-size: var(--ks-font-size-xl);
        }
    }

    :deep(.kel-select__wrapper) {
        padding: 8px 10px !important;
        height: 30px;
        font-size: var(--ks-font-size-xs);
        background-color: transparent;

        &.is-hovering:not(.is-focused) {
            box-shadow: 0 0 0 1px var(--ks-border-subtle) inset;
        }
    }

    :deep(.kel-select__placeholder.is-transparent) {
        color: var(--ks-text-primary);
    }
</style>

<!-- eslint-disable-next-line vue/enforce-style-attribute -->
<style lang="scss">
    .user-select {
        &.kel-select-dropdown {
            width: 328px;
            background: var(--ks-bg-input);
            box-shadow: 2px 3px 3px var(--ks-shadow-element);
            border-radius: var(--kel-border-radius-base);
            border: 1px solid var(--ks-border-default) !important;

            .kel-select-dropdown__item {
                min-height: 30px;
                height: fit-content;
                padding: 10px 16px 8px 16px;
                font-weight: var(--ks-font-weight-bold);
            }

            .kel-select-dropdown__footer {
                padding: 5px 0;

                .kel-select-dropdown__item {
                    margin: 0 !important;
                }
            }
        }
    }

    .user-avatar {
        padding: 0.25rem;
        border-radius: var(--ks-radius-base);
    }
</style>
