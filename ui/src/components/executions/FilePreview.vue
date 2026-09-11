<template>
    <FilePreviewForm
        v-if="isTextFile && !isHtmlFile"
        v-model:encoding="encodingModel"
        v-model:maxRows="maxRows"
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
                @click="loadAnyway()"
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
    <div class="load-error" v-else-if="loadError">
        <KsAlert type="error" :closable="false">
            {{ $t("file_preview.load_error") }}
        </KsAlert>
        <KsButtonGroup>
            <KsButton
                type="primary"
                @click="loadFile()"
            >
                {{ $t("file_preview.retry") }}
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
    <div v-else-if="isHtmlFile ? htmlContent === undefined : !preview">
        {{ $t("loading") }}
    </div>
    <template v-else>
        <div class="button-bar">
            <KsText>
                {{ path.split("/").slice(-1)[0] }}
            </KsText>
            <KsTag v-if="humanSize">
                {{ humanSize }}
            </KsTag>
            <div style="flex:1"/>
            <KsButton
                type="primary"
                tag="a"
                :href="itemUrl(path)"
                :icon="Download"
                rel="noopener noreferrer"
            >
                {{ $t('download') }}
            </KsButton>
        </div>
        <template v-if="isHtmlFile">
            <KsAlert type="info" :closable="false" class="html-asset-note">
                {{ $t("file_preview.html_asset_warning") }}
            </KsAlert>
            <!--
                The full file is fetched as text (loadContent) and injected via srcdoc,
                which bypasses the Content-Disposition:attachment header the /file endpoint
                sets and avoids the row/byte truncation of the /file/preview endpoint.
                sandbox="allow-scripts" lets inline scripts run (e.g. Plotly charts) but
                blocks access to the parent page's cookies and storage (no allow-same-origin).
                Because srcdoc has an about:blank base URL, relative asset references
                (img, link, script src) cannot resolve — self-contained documents only.
            -->
            <iframe
                :srcdoc="htmlContent"
                class="html-preview-frame"
                sandbox="allow-scripts"
                referrerpolicy="no-referrer"
                :title="$t('file_preview.html_preview_title')"
            />
        </template>
        <RawPreview v-else-if="isTextFile" v-bind="preview" :type="forceEditor ? 'RAW' : preview?.type ?? 'RAW'" />
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

    const BIG_FILE_THRESHOLD = 10 * 1024 * 1024 // 10MB

    const props = defineProps<{
        path: string,
        executionId: string,
    }>()

    const itemUrl = (value: string): string => {
        return `${apiUrl()}/executions/${props.executionId}/file?path=${encodeURIComponent(value)}`
    }

    const maxRows = ref<number>()
    const encodingModel = ref<EncodingOption["value"]>()
    const forceEditor = ref<boolean>()
    const preview = ref<Preview>()
    /** full HTML document text, injected into the sandboxed iframe via srcdoc */
    const htmlContent = ref<string>()
    /** is the file bigger than 10MB */
    const bigFile = ref<boolean>(false)
    /** true when a metadata or content fetch failed; surfaces the error + retry UI */
    const loadError = ref<boolean>(false)
    const metadata = ref<FileMetas>()

    async function getFileMeta() {
        return await ExecutionsAPI.fileMetadatasFromExecution({
            executionId: props.executionId,
            path: props.path,
        })
    }

    const isHtmlFile = computed(() => {
        // Runs during render (used in v-if), so guard against an undefined path —
        // some callers mount FilePreview without the path prop set yet.
        const lower = props.path?.toLowerCase() ?? ""
        return lower.endsWith(".html") || lower.endsWith(".htm")
    })

    const isTextFile = computed(() => {
        return isTextString(preview.value?.content)
    })

    function isTextString(str: any, sampleSize = 8192) {
        if(!str) return false
        let normalizedStr = str
        if (typeof str !== "string") {
            try {
                normalizedStr = JSON.stringify(str)
            } catch(e) {
                return false
            }
        }

        const sample = normalizedStr.slice(0, sampleSize)
        if (sample.length === 0) return true

        let nonText = 0
        for (let i = 0; i < sample.length; i++) {
            const code = sample.charCodeAt(i)

            // Null character — strong binary signal
            if (code === 0x00) return false

            const isPrintable =
                (code >= 0x09 && code <= 0x0D) || // tab, LF, VT, FF, CR
                (code >= 0x20 && code <= 0x7E) || // printable ASCII
                code >= 0x80                      // extended / unicode

            if (!isPrintable) nonText++
        }

        return nonText / sample.length <= 0.10
    }


    const humanSize = computed(() => {
        return metadata.value?.size ? Utils.humanFileSize(metadata.value.size) : undefined
    })

    async function loadPreview() {
        preview.value = await executionsStore
            .filePreview({
                executionId: props.executionId,
                path: props.path,
                maxRows: maxRows.value,
                encoding: encodingModel.value,
            })
    }

    // Loads the complete HTML file (not the truncated preview) for iframe rendering.
    // The bytes are decoded as UTF-8; the encoding selector is intentionally hidden
    // for HTML (see FilePreviewForm v-if), so non-UTF-8 documents are not re-decoded.
    async function loadContent() {
        htmlContent.value = await executionsStore.fileContent({
            executionId: props.executionId,
            path: props.path,
        })
    }

    // Fetches the body once size checks have passed: full document for HTML, the
    // (row/byte-capped) preview for everything else.
    async function fetchBody() {
        if(isHtmlFile.value) {
            await loadContent()
        } else {
            await loadPreview()
        }
    }

    // Entry point: resolves metadata, applies the empty/big-file guards, then fetches.
    // Any failure surfaces the error + retry UI rather than an indefinite loading state.
    async function loadFile() {
        loadError.value = false
        try {
            metadata.value = await getFileMeta()
            if(metadata.value.size === 0) {
                if(isHtmlFile.value) {
                    htmlContent.value = ""
                } else {
                    preview.value = {
                        type: "RAW",
                        content: "",
                        truncated: false,
                    }
                }
                return
            }
            bigFile.value = metadata.value.size >= BIG_FILE_THRESHOLD
            if(bigFile.value) {
                // For big files, warn the user before loading the full content into
                // memory (the srcdoc iframe holds the entire document as a string).
                // "Load anyway" calls loadAnyway() to fetch it explicitly.
                return
            }
            await fetchBody()
        } catch (e) {
            loadError.value = true
        }
    }

    // Bypasses the big-file guard when the user opts in from the warning.
    async function loadAnyway() {
        bigFile.value = false
        loadError.value = false
        try {
            await fetchBody()
        } catch (e) {
            loadError.value = true
        }
    }

    watch(
        [maxRows, encodingModel],
        async ([mRows, encoding]) => {
            // encoding must be set before any fetch; the RAW/TEXT viewer also needs maxRows.
            if(encoding === undefined) return
            if(!isHtmlFile.value && mRows === undefined) return
            await loadFile()
        },
        {immediate: true},
    )

    const miscStore = useMiscStore()

    const configPreviewInitialRows = computed((): number => {
        return  miscStore.configs?.preview.initial || 500
    })

    onMounted(() => {
        maxRows.value = configPreviewInitialRows.value
        encodingModel.value = "UTF-8"
    })
</script>

<style scoped lang="scss">
    .big-file-warning,
    .load-error {
        display: flex;
        flex-direction: column;
        align-items: end;
        gap: 1rem;
        margin-top: 2rem;
    }
    .button-bar {
        display: flex;
        gap: 1rem;
        align-items: center;
        justify-content: space-between;
        margin-block: 1rem;
    }
    .html-asset-note {
        margin-bottom: 1rem;
    }
    .html-preview-frame {
        width: 100%;
        min-height: 480px;
        border: 1px solid var(--ks-border-subtle);
        border-radius: 4px;
    }
</style>
