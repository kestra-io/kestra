<template>
    <div class="namespace-files-empty">
        <KsEmptyState
            :title="t('empty.namespaceFiles.title')"
            :image="artwork"
            :video="namespaceFilesLinks?.video"
            :learnMore="namespaceFilesLinks?.learnMore"
        >
            <template #description>
                {{ t("empty.namespaceFiles.content") }}
                <code class="namespace-files-empty-snippet">read('path/to/file')</code>
            </template>
            <template #action>
                <KsButton type="primary" :icon="FilePlus" @click="emit('newFile')">
                    {{ t("namespace files.new_file") }}
                </KsButton>
            </template>
        </KsEmptyState>
    </div>
</template>

<script setup lang="ts">
    import {useI18n} from "vue-i18n"
    import {KsButton, KsEmptyState} from "@kestra-io/design-system"
    import FilePlus from "vue-material-design-icons/FilePlus.vue"

    import artwork from "../../../assets/empty_visuals/generic.svg"
    import {links} from "../../layout/empty/links"

    const {t} = useI18n()

    const namespaceFilesLinks = links.namespaceFiles

    const emit = defineEmits<{
        newFile: [];
    }>()
</script>

<style lang="scss" scoped>
    .namespace-files-empty {
        width: 100%;
        height: 100%;
        display: flex;
        align-items: center;
        justify-content: center;
    }

    .namespace-files-empty-snippet {
        display: inline-block;
        margin-top: var(--ks-spacing-1);
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
        background: var(--ks-bg-surface);
        border: 1px solid var(--ks-border-subtle);
        border-radius: var(--ks-radius-base);
        font-family: var(--ks-font-family-mono);
        color: var(--ks-text-primary);
    }
</style>
