<template>
    <span>
        {{ humanReadableCron }}
    </span>
</template>

<script lang="ts" setup>
    import * as Utils from "../../utils/utils"
    import cronstrue from "cronstrue"
    import "cronstrue/locales/de"
    import "cronstrue/locales/es"
    import "cronstrue/locales/fr"
    import "cronstrue/locales/it"
    import "cronstrue/locales/ja"
    import "cronstrue/locales/ko"
    import "cronstrue/locales/pl"
    import "cronstrue/locales/pt_BR"
    import "cronstrue/locales/pt_PT"
    import "cronstrue/locales/ru"
    import "cronstrue/locales/zh_CN"
    import {computed} from "vue"

    // cronstrue names European Portuguese pt_PT and has no Hindi, which falls back to English.
    const CRON_LOCALES: Record<string, string> = {
        de: "de", es: "es", fr: "fr", it: "it", ja: "ja", ko: "ko", pl: "pl",
        pt: "pt_PT", pt_BR: "pt_BR", ru: "ru", zh_CN: "zh_CN",
    }

    const props =defineProps<{
        cronExpression:string;
    }>()

    const humanReadableCron = computed(() => {
        try {
            return cronstrue.toString(props.cronExpression, {locale: CRON_LOCALES[Utils.getLang()] ?? "en"})
        } catch {
            return props.cronExpression
        }
    })

</script>
