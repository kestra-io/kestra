<template>
    <KsForm class="schedule-panel" labelPosition="top" @submit.prevent>
        <KsFormItem :label="$t('recipe.schedule.frequency')">
            <KsSegmented
                v-model="selectedFrequency"
                :options="frequencyOptions"
                data-test="recipe-frequency-segmented"
            />
        </KsFormItem>

        <KsFormItem :label="$t('recipe.schedule.cron')">
            <KsInput
                v-model="recipe.cron"
                class="cron-input"
                :placeholder="DEFAULT_CRON"
                data-test="recipe-cron-input"
            />
            <span v-if="cronHint" class="hint">{{ cronHint }}</span>
        </KsFormItem>

        <KsFormItem :label="$t('recipe.schedule.timezone')">
            <KsSelect
                v-model="recipe.timezone"
                filterable
                clearable
                :placeholder="$t('recipe.schedule.timezone_placeholder')"
                data-test="recipe-timezone-select"
            >
                <KsOption
                    v-for="tz in timezones"
                    :key="tz"
                    :label="tz"
                    :value="tz"
                />
            </KsSelect>
        </KsFormItem>
    </KsForm>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import type {RecipeState} from "../../../../composables/useFlowRecipe"
    import {DEFAULT_CRON} from "../../../../utils/recipeToYaml"
    import {timeZones} from "../../../../utils/timeZones"

    const props = defineProps<{
        recipe: RecipeState
    }>()

    const {t} = useI18n()

    const FREQUENCY_CRONS: Record<string, string> = {
        daily: DEFAULT_CRON,
        hourly: "0 * * * *",
        weekly: "0 9 * * 1",
    }

    const frequencyOptions = computed(() => [
        {label: t("recipe.schedule.daily"), value: "daily"},
        {label: t("recipe.schedule.hourly"), value: "hourly"},
        {label: t("recipe.schedule.weekly"), value: "weekly"},
        {label: t("recipe.schedule.custom"), value: "custom"},
    ])

    const selectedFrequency = computed({
        get() {
            for (const [key, cron] of Object.entries(FREQUENCY_CRONS)) {
                if (props.recipe.cron === cron) return key
            }
            return "custom"
        },
        set(value: string) {
            if (value !== "custom" && FREQUENCY_CRONS[value]) {
                props.recipe.cron = FREQUENCY_CRONS[value]
            }
        },
    })

    const cronHint = computed(() => {
        const hints: Record<string, string> = {
            daily: t("recipe.schedule.daily_hint"),
            hourly: t("recipe.schedule.hourly_hint"),
            weekly: t("recipe.schedule.weekly_hint"),
        }
        return hints[selectedFrequency.value] ?? ""
    })

    const timezones = timeZones()
</script>

<style scoped lang="scss">
    .schedule-panel {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-4);
    }

    .cron-input {
        font-family: var(--ks-font-family-mono, monospace);
    }

    .hint {
        display: block;
        margin-top: var(--ks-spacing-1);
        font-size: var(--ks-font-size-sm);
        color: var(--ks-text-secondary);
    }
</style>
