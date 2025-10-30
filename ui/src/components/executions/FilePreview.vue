<template>
    <el-button
        size="small"
        type="primary"
        :icon="EyeOutline"
        @click="getFilePreview"
        :disabled="isZipFile"
    >
        {{ $t("preview.label") }}
    </el-button>
    <Drawer
        v-if="selectedPreview === value && preview"
        v-model="isPreviewOpen"
    >
        <template #header>
            {{ $t("preview.label") }}
        </template>
        <template #default>
            <el-alert v-if="preview.truncated" showIcon type="warning" :closable="false" class="mb-2">
                {{ $t('file preview truncated') }}
            </el-alert>
            <el-form class="ks-horizontal max-size mt-3">
                <el-form-item :label="$t('row count')">
                    <el-select
                        v-model="maxPreview"
                        filterable
                        clearable
                        :required="true"
                        :persistent="false"
                        @change="getFilePreview"
                    >
                        <el-option
                            v-for="item in maxPreviewOptions"
                            :key="item"
                            :label="item"
                            :value="item"
                        />
                    </el-select>
                </el-form-item>
                <el-form-item :label="$t('encoding')">
                    <el-select
                        v-model="encoding"
                        filterable
                        clearable
                        :required="true"
                        :persistent="false"
                        @change="getFilePreview"
                    >
                        <el-option
                            v-for="item in encodingOptions"
                            :key="item.value"
                            :label="item.label"
                            :value="item.value"
                        />
                    </el-select>
                </el-form-item>
                <el-form-item :label="($t('preview.view'))">
                    <el-switch
                        v-model="forceEditor"
                        class="ml-3"
                        :activeText="$t('preview.force-editor')"
                        :inactiveText="$t('preview.auto-view')"
                    />
                </el-form-item>
            </el-form>
            <ListPreview v-if="!forceEditor && preview.type === 'LIST'" :value="preview.content" />
            <img v-else-if="!forceEditor && preview.type === 'IMAGE'" :src="imageContent" alt="Image output preview">
            <PdfPreview v-else-if="!forceEditor && preview.type === 'PDF'" :source="preview.content" />
            <Markdown v-else-if="!forceEditor && preview.type === 'MARKDOWN'" :source="preview.content" />
            <Editor
                v-else
                :modelValue="!forceEditor ? preview.content : JSON.stringify(preview.content, null, 2)"
                :lang="!forceEditor ? extensionToMonacoLang : 'json'"
                readOnly
                input
                :wordWrap="wordWrap"
                :fullHeight="false"
                :navbar="false"
                class="position-relative"
            >
                <template #absolute>
                    <CopyToClipboard :text="!forceEditor ? preview.content : JSON.stringify(preview.content, null, 2)">
                        <template #right>
                            <el-tooltip
                                :content="$t('toggle_word_wrap')"
                                placement="bottom"
                                :autoClose="2000"
                            >
                                <el-button
                                    :icon="Wrap"
                                    type="default"
                                    @click="wordWrap = !wordWrap"
                                />
                            </el-tooltip>
                        </template>
                    </CopyToClipboard>
                </template>
            </Editor>
        </template>
    </Drawer>
</template>

<script setup lang="ts">
    import {ref, computed, onMounted} from "vue";
    import EyeOutline from "vue-material-design-icons/EyeOutline.vue";
    import Wrap from "vue-material-design-icons/Wrap.vue";
    import CopyToClipboard from "../layout/CopyToClipboard.vue";
    import Editor from "../inputs/Editor.vue";
    import ListPreview from "../ListPreview.vue";
    import PdfPreview from "../PdfPreview.vue";
    import Markdown from "../layout/Markdown.vue";
    import Drawer from "../Drawer.vue";
    import {useMiscStore} from "override/stores/misc";
    import {useExecutionsStore} from "../../stores/executions";

    interface EncodingOption {
        value: string;
        label: string;
    }

    interface Preview {
        truncated?: boolean;
        type?: string;
        content?: any;
        extension?: string;
    }

    const props = defineProps({
        value: {
            type: String,
            required: true
        },
        executionId: {
            type: String,
            required: false,
            default: undefined
        }
    });

    const emits = defineEmits(["preview"]);

    const isPreviewOpen = ref(false);
    const selectedPreview = ref<string | null>(null);
    const maxPreview = ref<number>();
    const encoding = ref<string>();
    const preview = ref<Preview>();
    const wordWrap = ref(false);
    const forceEditor = ref(false);

    const miscStore = useMiscStore();
    const executionsStore = useExecutionsStore();

    const encodingOptions: EncodingOption[] = [
        {value: "UTF-8", label: "UTF-8"},
        {value: "ISO-8859-1", label: "ISO-8859-1/Latin-1"},
        {value: "Cp1250", label: "Windows 1250"},
        {value: "Cp1251", label: "Windows 1251"},
        {value: "Cp1252", label: "Windows 1252"},
        {value: "UTF-16", label: "UTF-16"},
        {value: "Cp500", label: "EBCDIC IBM-500"}
    ];

    const configPreviewInitialRows = (): number => {
        return miscStore.configs?.preview.initial || 50;
    };

    const configPreviewMaxRows = (): number => {
        return miscStore.configs?.preview.max || 5000;
    };

    const maxPreviewOptions = computed(() => {
        return [10, 25, 50, 100, 500, 1000, 5000, 10000, 25000, 50000].filter(
            value => value <= configPreviewMaxRows()
        );
    });

    const extensionToMonacoLang = computed(() => {
        switch (preview.value?.extension) {
        case "json":
            return "json";
        case "jsonl":
            return "jsonl";
        case "yaml":
        case "yml":
        case "ion":
            return "yaml";
        case "csv":
            return "csv";
        case "py":
            return "python";
        default:
            return preview.value?.extension;
        }
    });

    const imageContent = computed(() => {
        return `data:image/${preview.value?.extension};base64,${preview.value?.content}`;
    });

    const isZipFile = computed(() => {
        return props.value?.toLowerCase().endsWith(".zip");
    });

    const getFilePreview = (): void => {
        const data = {
            path: props.value,
            maxRows: maxPreview.value,
            encoding: encoding.value
        };
        selectedPreview.value = props.value;
        if (props.executionId !== undefined) {
            executionsStore
                .filePreview({
                    executionId: props.executionId,
                    ...data
                })
                .then(response => {
                    preview.value = response;
                    isPreviewOpen.value = true;
                });
        } else {
            emits("preview", {
                data: data,
                callback: (response: Preview) => {
                    preview.value = response;
                    isPreviewOpen.value = true;
                }
            });
        }
    };

    onMounted(() => {
        maxPreview.value = configPreviewInitialRows();
        encoding.value = encodingOptions[0].value;
    });
</script>