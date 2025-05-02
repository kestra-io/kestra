<template>
    <el-select
        placement="right-end"
        :popper-offset="20"
        :show-arrow="false"
        :suffix-icon="ChevronRight"
        :placeholder="$t('kestra')"
        :popper-class="'user-select border border-0'"
    >
        <template #prefix>
            <img src="../../../assets/ks-logo-small.svg" width="40" alt="Kestra">
        </template>
        <template #header>
            <el-option :value="{}" class=" list-unstyled">
                <div class="d-flex align-items-center gap-2">
                    <img src="../../../assets/ks-logo-small.svg" width="40" alt="Kestra">
                    {{ $t("kestra") }}
                </div>
            </el-option>
        </template>
        <el-option
            v-for="item in menuItems"
            :key="item.label"
            :label="item.label"
            :value="item.label"
            @click="item.action"
        >
            <template #default>
                <div class="d-flex align-items-center gap-2">
                    <component :is="item.icon" class="fs-4 menu-icon" />
                    {{ $t(item.label) }}
                </div>
            </template>
        </el-option>
    </el-select>
</template>

<script setup>
    import {computed} from "vue";
    import {useRouter} from "vue-router";

    import CogOutline from "vue-material-design-icons/CogOutline.vue";
    import Slack from "vue-material-design-icons/Slack.vue";
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue";

    const router = useRouter();

    const menuItems = computed(() => [
        {
            label: "settings.label",
            icon: CogOutline,
            action: () => {
                router.push({name: "settings"});
            },
        },
        {
            label: "join_slack",
            icon: Slack,
            action: () => {
                window.open("https://kestra.io/slack", "_blank");
            },
        },
    ]);
</script>

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

            .menu-icon {
                color: var(--ks-content-tertiary);
            }
        }

        .el-select-dropdown__header {
            .el-select-dropdown__item {
                padding: 0;
                margin: 0;

                &.is-hovering {
                    background: none;
                }
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