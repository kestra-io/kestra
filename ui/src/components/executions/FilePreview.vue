<template>
    <div v-if="bigFileWarning">
        <KsAlert type="warning" :closable="false">
            {{ $t("executions.file_preview.big_file_warning") }}
        </KsAlert>
        <KsButton
            type="primary"
            @click="bigFileWarning = false;loadPreview()"
        >
            {{ $t("executions.file_preview.load_anyway") }}
        </KsButton>
    </div>
    <div v-else-if="!preview">
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
    import {type FileMetas} from "@kestra-io/kestra-sdk"
    import * as ExecutionsAPI from "@kestra-io/kestra-sdk/executions"
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
    const bigFileWarning = ref<boolean>(false)
    const metadata = ref<FileMetas>()

    async function getFileMeta() {
        return await ExecutionsAPI.fileMetadatasFromExecution({
            executionId: props.executionId,
            path: props.path,
        })
    }

    async function loadPreview() {
        preview.value = await executionsStore
            .filePreview({
                executionId: props.executionId,
                path: props.path,
                maxRows: maxPreview.value,
                encoding: encodingModel.value,
            })
    }

    watch(
        [maxPreview, encodingModel],
        async ([maxRows, encoding]) => {
            if(maxRows === undefined || encoding === undefined) return
            metadata.value = await getFileMeta()
            if(metadata.value.size === 0) {
                preview.value = {
                    type: "RAW",
                    content: "",
                    truncated: false,
                }
                return
            }
            bigFileWarning.value = metadata.value.size >= maxRows * 10_000
            if(bigFileWarning.value) {
                // For big files, we want to signal the user that it can take 
                // significant time to load the preview, so we set maxRows to 
                // undefined to disable the limit and load the full file.
                return
            }
            await loadPreview()
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
