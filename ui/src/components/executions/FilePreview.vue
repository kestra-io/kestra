<template>
    <FilePreviewForm
        v-model:encoding="encodingModel" 
        v-model:maxPreview="maxPreview" 
        v-model:forceEditor="forceEditor" 
        :truncated="preview?.truncated" 
    />
    <div class="big-file-warning" v-if="bigFile">
        <KsAlert type="warning" :closable="false">
            {{ $t("file_preview.big_file_warning", {size: humanSize}) }}
        </KsAlert>
        <KsButtonGroup>
            <KsButton
                type="primary"
                @click="bigFile = false;loadPreview()"
            >
                {{ $t("file_preview.load_anyway") }}
            </KsButton>
            <KsButton
                type="primary"
                tag="a"
                :href="itemUrl(path)"
                :icon="Download"
                rel="noopener noreferrer"
            >
                {{ $t('download') }}
            </KsButton>
        </KsButtonGroup>
    </div>
    <div v-else-if="!preview">
        Loading...
    </div>
    <template v-else>
        <RawPreview v-bind="preview" :type="forceEditor ? 'RAW' : preview?.type ?? 'RAW'" />
        <KsButton
            type="primary"
            tag="a"
            :href="itemUrl(path)"
            :icon="Download"
            rel="noopener noreferrer"
        >
            {{ $t('download') }}
        </KsButton>
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
    import {apiUrl} from "override/utils/route"
    import Download from "vue-material-design-icons/Download.vue"
    import * as Utils from "../../utils/utils"

    const executionsStore = useExecutionsStore()

    const props = defineProps<{
        path: string,
        executionId: string,
    }>()

    const itemUrl = (value: string): string => {
        return `${apiUrl()}/executions/${props.executionId}/file?path=${encodeURI(value)}`
    }

    const maxPreview = ref<number>()
    const encodingModel = ref<EncodingOption["value"]>()
    const forceEditor = ref<boolean>()
    const preview = ref<Preview>()
    /** is the file bigger than 10MB */
    const bigFile = ref<boolean>(false)
    const metadata = ref<FileMetas>()

    async function getFileMeta() {
        return await ExecutionsAPI.fileMetadatasFromExecution({
            executionId: props.executionId,
            path: props.path,
        })
    }

    const humanSize = computed(() => {
        return metadata.value?.size ? Utils.humanFileSize(metadata.value.size) : undefined
    })

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
            bigFile.value = metadata.value.size >= 10_000_000
            if(bigFile.value) {
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

<style scoped lang="scss">
    .big-file-warning {
        display: flex;
        flex-direction: column;
        align-items: end;
        gap: 1rem;
        margin-top: 2rem;
    }
</style>
