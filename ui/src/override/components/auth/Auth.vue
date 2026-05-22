<template>
    <KsSelect
        class="auth-selector"
        placement="right-end"
        :popperOffset="20"
        :showArrow="false"
        :suffixIcon="ChevronRight"
        :placeholder="$t('kestra')"
        popperClass="user-select border border-0"
    >
        <template #prefix>
            <img src="../../../assets/ks-logo-small.svg" width="24" height="24" alt="Kestra" class="user-avatar">
        </template>
        <template #header>
            <KsOption :value="{}" class=" list-unstyled">
                <div class="menu-item">
                    <img src="../../../assets/ks-logo-small.svg" width="40" alt="Kestra">
                    {{ $t("kestra") }}
                </div>
            </KsOption>
        </template>
        <KsOption label="welcome" value="welcome">
            <RouterLink :to="startTutorial" class="menu-item">
                <RocketLaunchOutline class="menu-icon" />
                {{ $t("product_tour") }}
            </RouterLink>
        </KsOption>
        <KsOption label="Settings" value="settings">
            <RouterLink :to="{name: 'settings'}" class="menu-item">
                <CogOutline class="menu-icon" />
                {{ $t("settings.label") }}
            </RouterLink>
        </KsOption>
        <KsOption label="slack" value="slack">
            <a href="https://kestra.io/slack?utm_source=app&utm_medium=referral&utm_campaign=top-auth" target="_blank" class="menu-item">
                <Slack class="menu-icon" />
                {{ $t("join_slack") }}
            </a>
        </KsOption>
        <template #footer>
            <KsOption class="list-unstyled" :value="'logout'" @click="logout">
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

    import CogOutline from "vue-material-design-icons/CogOutline.vue"
    import Slack from "vue-material-design-icons/Slack.vue"
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue"
    import Logout from "vue-material-design-icons/Logout.vue"
    import RocketLaunchOutline from "vue-material-design-icons/RocketLaunchOutline.vue"

    import * as BasicAuth from "../../../utils/basicAuth"
    import {useClient} from "@kestra-io/kestra-sdk"
    const axios = useClient()

    const route = useRoute()
    const router = useRouter()

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
.menu-item{
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
    padding: 8px 16px !important;
    height: 30px;
    font-size: var(--ks-font-size-xs);

    &.is-hovering:not(.is-focused) {
        box-shadow: 0 0 0 1px var(--ks-border-subtle) inset;
    }
}
</style>

<!-- eslint-disable-next-line vue/enforce-style-attribute -->
<style lang="scss">
.user-select  {
    &.kel-select-dropdown {
        width: 328px;
        background: var(--ks-bg-input);
        box-shadow: 2px 3px 3px var(--ks-shadow-element);
        border-radius: var(--kel-border-radius-base);
        border: 1px solid var(--ks-border-default) !important;
        margin-bottom: -2px;

        .kel-select-dropdown__item {
            min-height: 34px;
            height: fit-content;
            padding: 10px 16px 8px 16px;
            margin: 0;
            font-size: var(--ks-font-size-sm);
            font-weight: 700;
        }

        .kel-select-dropdown__header {
            .kel-select-dropdown__item {
                padding: 0;
                margin: 0;
                background: none;

                &.is-hovering {
                    background: none;
                }
            }
        }

        .kel-select-dropdown__footer {
            padding: 5px 0;
            .kel-select-dropdown__item {
                margin: 0 !important;
            }
        }
    }
}

html.menu-collapsed .auth-selector {
    .kel-select__suffix {
        display: none;
    }
}

.user-avatar {
    padding: 0.25rem;
    border-radius: var(--ks-radius-base);
}
</style>
