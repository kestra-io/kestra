<template>
    <el-select
        placement="right-end"
        :popper-offset="20"
        :show-arrow="false"
        :suffix-icon="ChevronRight"
        :placeholder="t('kestra')"
        popper-class="user-select border border-0"
    >
        <template #prefix>
            <img src="../../../assets/ks-logo-small.svg" width="40" alt="Kestra">
        </template>
        <template #header>
            <el-option :value="{}" class=" list-unstyled">
                <div class="menu-item">
                    <img src="../../../assets/ks-logo-small.svg" width="40" alt="Kestra">
                    {{ t("kestra") }}
                </div>
            </el-option>
        </template>
        <el-option label="Settings" value="settings">
            <RouterLink :to="{name: 'settings'}" class="menu-item">
                <CogOutline class="menu-icon" />
                {{ t("settings.label") }}
            </RouterLink>
        </el-option>
        <el-option label="slack" value="slack">
            <a href="https://kestra.io/slack" target="_blank" class="menu-item">
                <Slack class="menu-icon" />
                {{ t("join_slack") }}
            </a>
        </el-option>
        <template #footer>
            <el-option class="list-unstyled" :value="'logout'" @click="logout">
                <div class="menu-item">
                    <Logout class="menu-icon" />
                    {{ t("setup.logout") }}
                </div>
            </el-option>
        </template>
    </el-select>
</template>

<script setup lang="ts">
    import {RouterLink, useRouter} from "vue-router";
    import {useStore} from "vuex";
    import {useI18n} from "vue-i18n";

    import CogOutline from "vue-material-design-icons/CogOutline.vue";
    import Slack from "vue-material-design-icons/Slack.vue";
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue";
    import Logout from "vue-material-design-icons/Logout.vue";

    import * as BasicAuth from "../../../utils/basicAuth";

    const router = useRouter();
    const store = useStore() as any;
    const {t} = useI18n();

    const logout = () => {
        BasicAuth.logout();
        delete store.$http?.defaults?.headers?.common?.["Authorization"];
        router.push({name: "login"});
    };
</script>

<style lang="scss" scoped>
.menu-item{
    display: flex;
    align-items: center;
    gap: 1rem;
    color: var(--ks-content-primary);

    .menu-icon {
        color: var(--ks-content-tertiary);
        font-size: 1.5rem;
    }
}
</style>

<style lang="scss">
.user-select  {
    &.el-select-dropdown {
        width: 328px;
        background: var(--ks-select-background);
        box-shadow: 2px 3px 3px var(--ks-card-shadow);
        border-radius: var(--bs-border-radius);
        border: 1px solid var(--ks-border-primary) !important;
        margin-bottom: -2px;

        .el-select-dropdown__item {
            min-height: 34px;
            height: fit-content;
            padding: 10px 16px 8px 16px;
            margin: 0;
            font-size: 14px;
            font-weight: 700;
        }

        .el-select-dropdown__header {
            .el-select-dropdown__item {
                padding: 0;
                margin: 0;
                background: none;

                &.is-hovering {
                    background: none;
                }
            }
        }

        .el-select-dropdown__footer {
            padding: 5px 0;
            .el-select-dropdown__item {
                margin: 0 !important;
            }
        }
    }
}

.el-select {
    >.el-select__wrapper {
        padding: 2px 8px;
        padding-left: 6px !important;
    }
}

html.menu-collapsed {
    .el-select__suffix {
        display: none;
    }
}
</style>