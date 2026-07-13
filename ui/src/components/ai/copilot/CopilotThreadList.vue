<template>
    <div class="copilot-threads" data-test="copilot-thread-list">
        <KsText v-if="loading" size="small" class="copilot-threads-muted">{{ t("ai.copilot.threads.loading") }}</KsText>

        <KsText v-else-if="!threads.length" size="small" class="copilot-threads-muted">
            {{ t("ai.copilot.threads.empty") }}
        </KsText>

        <ul v-else class="copilot-thread-rows">
            <li
                v-for="thread in threads"
                :key="thread.uid"
                class="copilot-thread-row"
                :class="{'copilot-thread-row--active': thread.uid === activeId}"
            >
                <!-- Rename -->
                <template v-if="editingId === thread.uid">
                    <KsInput v-model="draftTitle" size="small" class="copilot-thread-input" @keydown.enter="saveRename(thread.uid)" />
                    <KsButton size="small" type="primary" data-test="copilot-thread-rename-save" @click="saveRename(thread.uid)">
                        {{ t("ai.copilot.threads.save") }}
                    </KsButton>
                    <KsButton size="small" text @click="editingId = null">{{ t("ai.copilot.threads.cancel") }}</KsButton>
                </template>

                <!-- Delete confirm -->
                <template v-else-if="deletingId === thread.uid">
                    <KsText size="small" class="copilot-thread-confirm">{{ t("ai.copilot.threads.deleteConfirm") }}</KsText>
                    <KsButton size="small" type="primary" data-test="copilot-thread-delete-confirm" @click="confirmDelete(thread.uid)">
                        {{ t("ai.copilot.threads.delete") }}
                    </KsButton>
                    <KsButton size="small" text @click="deletingId = null">{{ t("ai.copilot.threads.cancel") }}</KsButton>
                </template>

                <!-- Normal row: pick / rename / delete -->
                <template v-else>
                    <button class="copilot-thread-select" data-test="copilot-thread-select" @click="emit('select', thread.uid)">
                        {{ thread.title || t("ai.copilot.threads.untitled") }}
                    </button>
                    <KsButton size="small" text :aria-label="t('ai.copilot.threads.rename')" data-test="copilot-thread-rename" @click="startRename(thread)">
                        {{ t("ai.copilot.threads.rename") }}
                    </KsButton>
                    <KsButton size="small" text :aria-label="t('ai.copilot.threads.delete')" data-test="copilot-thread-delete" @click="deletingId = thread.uid">
                        {{ t("ai.copilot.threads.delete") }}
                    </KsButton>
                </template>
            </li>
        </ul>
    </div>
</template>

<script setup lang="ts">
    import {ref, onMounted} from "vue"
    import {useI18n} from "vue-i18n"
    import {useAiThreads} from "./useAiThreads"
    import type {ThreadSummary} from "./types"

    defineProps<{
        /** The currently open thread, highlighted in the list. */
        activeId?: string | null
    }>()

    const emit = defineEmits<{
        (e: "select", threadId: string): void
    }>()

    const {t} = useI18n()

    const {threads, loading, list, rename, remove} = useAiThreads()

    const editingId = ref<string | null>(null)
    const deletingId = ref<string | null>(null)
    const draftTitle = ref("")

    function startRename(thread: ThreadSummary): void {
        editingId.value = thread.uid
        draftTitle.value = thread.title ?? ""
    }

    async function saveRename(threadId: string): Promise<void> {
        const title = draftTitle.value.trim()
        if (title) await rename(threadId, title)
        editingId.value = null
    }

    async function confirmDelete(threadId: string): Promise<void> {
        await remove(threadId)
        deletingId.value = null
    }

    // Exposed so the parent can refresh the list (e.g. after "New chat" creates a thread).
    defineExpose({refresh: list})

    onMounted(list)
</script>

<style scoped>
    .copilot-threads {
        min-width: 14rem;
        max-height: 20rem;
        overflow-y: auto;
    }

    .copilot-threads-muted {
        display: block;
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        --kel-text-color: var(--ks-text-secondary);
    }

    .copilot-thread-rows {
        margin: 0;
        padding: 0;
        list-style: none;
    }

    .copilot-thread-row {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
        border-radius: var(--ks-radius-sm);
    }

    .copilot-thread-row--active {
        background: var(--ks-bg-tag);
    }

    .copilot-thread-select {
        flex: 1 1 auto;
        min-width: 0;
        text-align: left;
        border: none;
        background: transparent;
        color: var(--ks-text-primary);
        font: inherit;
        font-size: var(--ks-font-size-sm);
        cursor: pointer;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .copilot-thread-input {
        flex: 1 1 auto;
    }

    .copilot-thread-confirm {
        flex: 1 1 auto;
        --kel-text-color: var(--ks-text-secondary);
    }
</style>
