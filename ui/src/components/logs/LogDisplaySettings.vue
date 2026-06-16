<template>
    <KsPopover placement="bottom-end" trigger="click" :title="t('display_settings')" width="300">
        <template #reference>
            <KsButton type="default" size="default" class="display-settings-btn" :icon="Cog" :aria-label="t('display_settings')" />
        </template>
        <template #default>
            <div class="log-display-settings">
                <div class="row row--stack">
                    <span class="row-label">{{ t('density') }}</span>
                    <KsSegmented v-model="logsDensity" :options="densityOptions" block class="density-segmented" />
                </div>
                <div class="row">
                    <span class="row-label">{{ t('font size') }}</span>
                    <KsInputNumber v-model="logsFontSize" :min="10" :max="24" :step="1" controlsPosition="right" class="row-number" />
                </div>
                <div class="row">
                    <span class="row-label">{{ t('pretty_json') }}</span>
                    <KsSwitch v-model="logsPrettyJson" />
                </div>
                <div class="row">
                    <span class="row-label">{{ t('expand_by_default') }}</span>
                    <KsSwitch v-model="logsExpandByDefault" :disabled="!logsPrettyJson" />
                </div>
                <div class="row">
                    <span class="row-label">{{ t('body_line_clamp') }}</span>
                    <div class="clamp-control">
                        <KsInputNumber
                            v-if="clampEnabled"
                            v-model="logsBodyClamp"
                            :min="1"
                            :max="50"
                            :step="1"
                            controlsPosition="right"
                            class="row-number"
                        />
                        <KsSwitch v-model="clampEnabled" />
                    </div>
                </div>
            </div>
        </template>
    </KsPopover>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import Cog from "vue-material-design-icons/Cog.vue"
    import {logsFontSize, logsDensity, logsBodyClamp, logsPrettyJson, logsExpandByDefault} from "../../composables/useLogDisplay"

    const {t} = useI18n()

    const densityOptions = computed(() => [
        {label: t("density_compact"), value: "compact"},
        {label: t("density_normal"), value: "normal"},
        {label: t("density_expanded"), value: "expanded"},
    ])

    const clampEnabled = computed({
        get: () => logsBodyClamp.value > 0,
        set: (value: boolean) => {
            logsBodyClamp.value = value ? (logsBodyClamp.value || 5) : 0
        },
    })
</script>

<style scoped lang="scss">
    .display-settings-btn {
        margin: 0;
        padding: var(--ks-spacing-2);
        border-radius: var(--ks-radius-base);
    }

    .log-display-settings {
        display: flex;
        flex-direction: column;
    }

    .row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--ks-spacing-4);
        padding: var(--ks-spacing-2) 0;

        & + .row {
            border-top: 1px solid var(--ks-border-subtle);
        }
    }

    .row--stack {
        flex-direction: column;
        align-items: stretch;
        gap: var(--ks-spacing-2);
    }

    .density-segmented {
        width: 100%;
    }

    .row-label {
        color: var(--ks-text-primary);
        font-size: var(--ks-font-size-sm);
    }

    .row-number {
        width: 88px;
    }

    .clamp-control {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
    }
</style>
