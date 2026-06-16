<template>
    <KsAlert v-if="truncated" type="warning" :closable="false" class="mb-2">
        {{ $t('file preview truncated') }}
    </KsAlert>
    <KsForm class="ks-horizontal max-size mt-3">
        <KsFormItem :label="$t('row count')">
            <KsSelect
                v-model="maxPreview"
                filterable
                clearable
                :required="true"
            >
                <KsOption
                    v-for="item in maxPreviewOptions"
                    :key="item"
                    :label="item"
                    :value="item"
                />
            </KsSelect>
        </KsFormItem>
        <KsFormItem :label="$t('encoding')">
            <KsSelect
                v-model="encoding"
                filterable
                clearable
                :required="true"
            >
                <KsOption
                    v-for="item in encodingOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                />
            </KsSelect>
        </KsFormItem>
        <KsFormItem :label="($t('preview.view'))">
            <KsSwitch
                v-model="forceEditor"
                class="ml-3"
                :activeText="$t('preview.force-editor')"
                :inactiveText="$t('preview.auto-view')"
            />
        </KsFormItem>
    </KsForm>
</template>

<script lang="ts" setup>
    import {computed} from "vue"
    import {useMiscStore} from "override/stores/misc"

    const miscStore = useMiscStore()

    const encodingOptions = [
        {value: "UTF-8", label: "UTF-8"},
        {value: "ISO-8859-1", label: "ISO-8859-1/Latin-1"},
        {value: "Cp1250", label: "Windows 1250"},
        {value: "Cp1251", label: "Windows 1251"},
        {value: "Cp1252", label: "Windows 1252"},
        {value: "UTF-16", label: "UTF-16"},
        {value: "Cp500", label: "EBCDIC IBM-500"},
    ] as const 

    export type EncodingOption = typeof encodingOptions[number]

    const configPreviewMaxRows = computed((): number => {
        return  miscStore.configs?.preview.max || 5000
    })

    const maxPreviewOptions = computed(() => {
        return [50, 100, 250, 500, 1000, 5000, 10000, 25000, 50000].filter(
            value => value <= configPreviewMaxRows.value,
        )
    })

    const encoding = defineModel<typeof encodingOptions[number]["value"]>("encoding")
    const maxPreview = defineModel<number | undefined>("maxPreview")
    const forceEditor = defineModel<boolean>("forceEditor")

    defineProps<{
        truncated?: boolean;
    }>()
</script>
