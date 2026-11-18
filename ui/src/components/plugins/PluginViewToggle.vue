<template>
    <KsSegmented
        v-model="view"
        variant="neutral"
        size="small"
        :options="viewOptions"
        data-test="plugin-view-toggle"
    />
</template>

<script setup lang="ts">
    import {computed, markRaw} from "vue"
    import {useI18n} from "vue-i18n"
    import TableLarge from "vue-material-design-icons/TableLarge.vue"
    import ViewGridOutline from "vue-material-design-icons/ViewGridOutline.vue"
    import {usePluginView} from "./pluginView"

    defineOptions({inheritAttrs: false})

    const {t} = useI18n()
    const view = usePluginView()

    const viewOptions = computed(() => [
        {value: "cards", label: t("pluginPage.view.cards"), icon: markRaw(ViewGridOutline)},
        {value: "table", label: t("pluginPage.view.table"), icon: markRaw(TableLarge)},
    ])
</script>
