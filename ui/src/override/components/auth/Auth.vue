<template>
    <KsDropdown class="auth" trigger="click" placement="right-end" :showArrow="false" @command="onCommand">
        <button type="button" class="trigger">
            <img class="logo" :src="KS_LOGO" alt="Kestra">
            <span class="name">{{ $t("kestra") }}</span>
            <KsIcon class="chevron" size="sm">
                <ChevronRight />
            </KsIcon>
        </button>

        <template #dropdown>
            <div class="menu">
                <div class="identity">
                    <img class="logo" :src="KS_LOGO" alt="Kestra">
                    <KsText class="name" truncated>{{ $t("kestra") }}</KsText>
                </div>

                <KsDropdownMenu>                
                    <KsDropdownItem command="tour">
                        <KsIcon size="base">
                            <RocketLaunchOutline />
                        </KsIcon>
                        {{ $t("product_tour") }}
                    </KsDropdownItem>

                    <KsDropdownItem command="slack">
                        <KsIcon size="base">
                            <Slack />
                        </KsIcon>
                        {{ $t("join_slack") }}
                    </KsDropdownItem>
                    
                    <KsDropdownItem v-if="configs" divided command="version" >
                        <KsIcon size="base">
                            <InformationIcon />
                        </KsIcon>
                        {{ $t("version") }}
                        <KsPopover width="max-content" trigger="hover" placement="right" :showArrow="false" :offset="24">
                            <template #default>
                                <div class="auth-item__popover">
                                    <div>{{ $t("version") }}: {{ configs.version }}</div>
                                    <div >
                                        {{ $t("commit_id") }}:
                                        <span class="auth-item__commit">
                                            {{ configs.commitId}}
                                        </span>
                                    </div>
                                    <div >
                                        {{ $t("date") }}:
                                        {{ dateUtils.dateFilter(configs.commitDate) }}
                                    </div>
                                </div>
                            </template>
                            <template #reference>
                                    <span class="auth-item__version">
                                        v{{ configs.version.split("-")[0] }}
                                    </span>
                                </template>
                        </KsPopover>
                    </KsDropdownItem>

                    <KsDropdownItem danger command="logout">
                        <KsIcon size="base">
                            <Logout />
                        </KsIcon>
                        {{ $t("setup.logout") }}
                    </KsDropdownItem>
                </KsDropdownMenu>
            </div>
        </template>
    </KsDropdown>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useRoute, useRouter} from "vue-router"
    import {useClient} from "@kestra-io/kestra-sdk"
import ChevronRight from "vue-material-design-icons/ChevronRight.vue"
import Logout from "vue-material-design-icons/Logout.vue"
import RocketLaunchOutline from "vue-material-design-icons/RocketLaunchOutline.vue"
import Slack from "vue-material-design-icons/Slack.vue"
import KS_LOGO from "../../../assets/ks-logo-small.svg"
import * as BasicAuth from "../../../utils/basicAuth"
import { KsPopover, dateUtils } from "@kestra-io/design-system"
import { useMiscStore } from "override/stores/misc"
import InformationIcon from 'vue-material-design-icons/Information.vue';
const SLACK_URL = "https://kestra.io/slack?utm_source=app&utm_medium=referral&utm_campaign=top-auth"

const route = useRoute()
const router = useRouter()
const axios = useClient()
const miscStore = useMiscStore()
const configs = computed(() => miscStore.configs)
const startTutorial = computed(() => ({
    name: "ai",
        query: {tour: "start"},
        params: {tenant: route.params.tenant},
}))

function onCommand(command: string) {
    if (command === "tour") {
        router.push(startTutorial.value)
    } else if (command === "slack") {
        window.open(SLACK_URL, "_blank", "noopener")
    }
    else if (command === "version") {// does nothing, version is displayed in the menu
    }
    else {
        logout()
    }
}

function logout() {
    BasicAuth.logout()
    delete axios.defaults.headers.common["Authorization"]
        router.push({name: "login"})
}
</script>

<style scoped lang="scss">
.auth {
    display: flex;
    width: 100%;
}

.trigger {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-2);
    width: 100%;
    height: 2rem;
    padding: 0 var(--ks-spacing-2);
    background: transparent;
    border: 1px solid var(--ks-border-strong);
    border-radius: var(--ks-radius-base);
    color: var(--ks-text-primary);
    font: inherit;
    font-size: var(--ks-font-size-xs);
    text-align: left;
    cursor: pointer;
    transition: background-color 0.15s ease, border-color 0.15s ease;

    &:hover {
        background: var(--ks-bg-hover);
    }

    &:focus-visible,
    &[aria-expanded="true"] {
        outline: none;
        border-color: var(--ks-border-focus);
        box-shadow: 0 0 0 1px var(--ks-border-focus) inset;
    }

    .logo {
        width: 1.5rem;
        height: 1.5rem;
    }

    .name {
        flex: 1;
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .chevron {
        flex-shrink: 0;
        color: var(--ks-icon-muted);
    }
}

.logo {
    flex-shrink: 0;
    border-radius: var(--ks-radius-base);
}

.menu {
    width: 18rem;
    font-weight: var(--ks-font-weight-medium);

    .identity {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-3);
        padding: var(--ks-spacing-3) var(--ks-spacing-4);
        border-bottom: 1px solid var(--ks-border-default);

        .logo {
            width: 2.5rem;
            height: 2.5rem;
            border-radius: var(--ks-radius-lg);
        }

        .name {
            color: var(--ks-text-primary);
            font-size: var(--ks-font-size-base);
            font-weight: var(--ks-font-weight-semibold);
        }
    }
}
.auth-item__version {
    padding: var(--ks-spacing-1);
    font-size: var(--ks-font-size-base);
    font-weight: var(--ks-font-weight-regular);
    color: var(--ks-text-secondary);
    line-height: 1;
    white-space: nowrap;
    margin-left: auto;
}

.auth-item__popover {
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-1);
    font-size: var(--ks-font-size-base);
    font-weight: var(--ks-font-weight-regular);
    
}

.auth-item__commit {
    color: var(--ks-text-link);
    font-family: var(--kel-font-family-monospace);
}
</style>
