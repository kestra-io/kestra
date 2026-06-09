<template>
    <div v-if="!preview">
        Loading...
    </div>
    <template v-else>
        <FilePreviewForm
            v-model:encoding="encodingModel" 
            v-model:maxPreview="maxPreview" 
            v-model:forceEditor="forceEditor" 
            :truncated="preview?.truncated" 
        />
        <RawPreview v-bind="preview" :type="forceEditor ? 'RAW' : preview?.type ?? 'RAW'" />
    </template>
</template>

<script lang="ts" setup>
    import {computed, onMounted, ref, watch} from "vue"
    import FilePreviewForm, {type EncodingOption} from "./FilePreviewForm.vue"
    import RawPreview, {type Preview} from "./RawPreview.vue"
    import {useExecutionsStore} from "../../stores/executions.ts"
    import {useMiscStore} from "override/stores/misc.ts"

    const executionsStore = useExecutionsStore()

    const props = defineProps<{
        path: string,
        executionId: string,
    }>()

    const maxPreview = ref<number>()
    const encodingModel = ref<EncodingOption["value"]>()
    const forceEditor = ref<boolean>()
    const preview = ref<Preview>()

    const emits = defineEmits(["preview"])

    watch(
        [maxPreview, encodingModel],
        async ([maxRows, encoding]) => {
            if(maxRows === undefined || encoding === undefined) return
            preview.value = await executionsStore
                .filePreview({
                    executionId: props.executionId,
                    path: props.path,
                    maxRows,
                    encoding,
                })
        },
        {immediate: true},
    )

    const miscStore = useMiscStore()

    const configPreviewInitialRows = computed((): number => {
        return  miscStore.configs?.preview.initial || 500
    })

    onMounted(() => {
        maxPreview.value = configPreviewInitialRows.value
        encodingModel.value = "UTF-8"
    })
</script>
