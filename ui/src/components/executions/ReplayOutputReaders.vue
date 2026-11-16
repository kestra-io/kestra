<template>
    <KsDrawer v-model="open" data-test="replay-output-readers">
        <template #header>
            <i18n-t keypath="replayOverrides.readers_title" tag="span" scope="global">
                <template #path>
                    <code>{{ path }}</code>
                </template>
            </i18n-t>
        </template>

        <div class="readers">
            <section v-for="reader in readers" :key="reader.taskId" class="reader" data-test="replay-output-reader">
                <h4 class="section-title">
                    <code>{{ reader.taskId }}</code>
                </h4>
                <KsText size="small" type="info">
                    {{ $t("replayOverrides.readers_reads", {paths: reader.paths.join(", ")}) }}
                </KsText>
                <div v-if="sources[reader.taskId]" class="code">
                    <KsEditor
                        :ref="editorRef(reader.taskId)"
                        :modelValue="sources[reader.taskId]"
                        :path="`replay-reader-${reader.taskId}`"
                        lang="yaml"
                        readOnly
                        :navbar="false"
                        :options="{fullHeight: false}"
                        @editorMounted="highlight(reader)"
                    />
                </div>
                <KsText v-else size="small" type="info">
                    {{ $t("replayOverrides.code_error", {taskId: reader.taskId}) }}
                </KsText>
            </section>
        </div>
    </KsDrawer>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import type {KsEditorExposes} from "@kestra-io/design-system"
    import {firstReadingLine, taskSource} from "./replayOverrides"

    const props = defineProps<{
        path: string
        readers: {taskId: string, paths: string[]}[]
        flowSource?: string
    }>()

    const open = defineModel<boolean>({required: true})

    const editors: Record<string, KsEditorExposes | null> = {}

    const sources = computed(() => Object.fromEntries(
        props.readers.map(reader => [reader.taskId, props.flowSource ? taskSource(props.flowSource, reader.taskId) : undefined]),
    ))

    const editorRefs = new Map<string, (editor: unknown) => void>()

    function editorRef(taskId: string) {
        if (!editorRefs.has(taskId)) editorRefs.set(taskId, editor => editors[taskId] = editor as KsEditorExposes | null)
        return editorRefs.get(taskId)
    }

    function highlight(reader: {taskId: string, paths: string[]}) {
        const source = sources.value[reader.taskId]
        const line = source ? firstReadingLine(source, reader.paths) : undefined
        if (line) editors[reader.taskId]?.highlightLinesRange({start: line, end: line})
    }
</script>

<style scoped>
.readers {
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-5);
}

.reader {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: var(--ks-spacing-2);
}

.section-title {
    font-size: var(--ks-font-size-sm);
    font-weight: var(--ks-font-weight-semibold);
    margin: 0;
    color: var(--ks-text-primary);
}

.code {
    align-self: stretch;
    border: 1px solid var(--ks-border-default);
    border-radius: var(--ks-radius-base);
}
</style>
