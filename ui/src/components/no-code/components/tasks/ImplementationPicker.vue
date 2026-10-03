<template>
    <KsDialog v-model="visible" :title="title" scrollable data-test="plugin-implementation-picker">
        <div class="implementation-picker-search">
            <KsInput
                v-model="search"
                :placeholder="$t('block_editor.plugin_implementation.search_placeholder')"
                :aria-label="$t('block_editor.plugin_implementation.search_placeholder')"
                clearable
                data-test="plugin-implementation-picker-search"
            >
                <template #prefix>
                    <Magnify />
                </template>
            </KsInput>
            <p class="implementation-picker-count" data-test="plugin-implementation-picker-count">
                {{ $t("block_editor.plugin_implementation.picker_count", {shown: filtered.length, total: branches.length}) }}
            </p>
        </div>

        <div v-if="filtered.length" class="implementation-picker-list" role="listbox" :aria-label="title">
            <button
                v-for="option in filtered"
                :key="option.branch.ref"
                type="button"
                role="option"
                class="implementation-picker-option"
                :aria-checked="option.branch.ref === current"
                :aria-selected="option.branch.ref === current"
                data-test="plugin-implementation-picker-option"
                @click="select(option.branch)"
            >
                <TaskIcon
                    class="implementation-picker-option-icon"
                    :cls="option.branch.ref"
                    :icons="pluginsStore.icons"
                    :loadIcon="pluginsStore.loadIcon"
                    :onlyIcon="true"
                />
                <span class="implementation-picker-option-main">
                    <span class="implementation-picker-option-name">{{ option.name }}</span>
                    <span v-if="option.description" class="implementation-picker-option-description">{{ option.description }}</span>
                </span>
                <Check v-if="option.branch.ref === current" class="implementation-picker-option-check" />
            </button>
        </div>
        <KsEmpty v-else data-test="plugin-implementation-picker-empty">
            <template #description>
                <strong>{{ $t("block_editor.plugin_implementation.no_results_title", {query: search}) }}</strong>
                <p>{{ $t("block_editor.plugin_implementation.no_results_description", {count: branches.length}) }}</p>
            </template>
        </KsEmpty>
    </KsDialog>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {KsDialog, KsInput, KsEmpty} from "@kestra-io/design-system"
    import Magnify from "vue-material-design-icons/Magnify.vue"
    import Check from "vue-material-design-icons/Check.vue"
    import TaskIcon from "../../../plugins/TaskIcon.vue"
    import {usePluginsStore} from "../../../../stores/plugins"
    import {humanizeClassName, simpleClassName, type ImplementationBranch} from "./discriminatedUnion"

    const props = defineProps<{
        title: string
        branches: ImplementationBranch[]
        current?: string
    }>()

    const emit = defineEmits<{
        select: [branch: ImplementationBranch]
    }>()

    const visible = defineModel<boolean>({default: false})

    const pluginsStore = usePluginsStore()

    const search = ref("")

    watch(visible, (isVisible) => {
        if (isVisible) search.value = ""
    })

    // A plugin description is full markdown and often embeds whole YAML examples, so only the
    // lead sentence is usable in a list of nineteen.
    function leadSentence(text: string | undefined): string {
        if (!text) return ""
        const beforeBlock = text.split(/\n\s*(?:#{1,6}\s|```)/)[0]
        const firstLine = beforeBlock.split(/\n\s*\n/)[0].replace(/\s+/g, " ").trim()
        const sentenceEnd = firstLine.search(/\.(?:\s|$)/)
        return sentenceEnd === -1 ? firstLine : firstLine.slice(0, sentenceEnd + 1)
    }

    const options = computed(() => props.branches.map((branch) => ({
        branch,
        name: humanizeClassName(simpleClassName(branch.ref)),
        description: leadSentence(
            branch.definition.title ?? branch.definition.description ?? branch.definition.markdownDescription,
        ),
    })))

    const filtered = computed(() => {
        const query = search.value.trim().toLowerCase()
        if (!query) return options.value
        return options.value.filter((option) =>
            option.name.toLowerCase().includes(query)
            || simpleClassName(option.branch.ref).toLowerCase().includes(query)
            || option.description.toLowerCase().includes(query),
        )
    })

    function select(branch: ImplementationBranch) {
        emit("select", branch)
    }
</script>

<style scoped lang="scss">
.implementation-picker-search {
    margin-bottom: var(--ks-spacing-3);
}

.implementation-picker-count {
    margin: var(--ks-spacing-2) 0 0;
    font-size: var(--ks-font-size-xs);
    color: var(--ks-text-muted);
    font-family: var(--ks-font-family-mono);
}

.implementation-picker-list {
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-1);
    max-height: 360px;
    overflow-y: auto;
}

.implementation-picker-option {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-3);
    width: 100%;
    padding: var(--ks-spacing-2) var(--ks-spacing-3);
    background: none;
    border: 1px solid transparent;
    border-radius: var(--ks-radius-sm);
    cursor: pointer;
    text-align: left;
    transition: background-color 0.12s, border-color 0.12s;
}

.implementation-picker-option:hover {
    background: var(--ks-bg-hover);
    border-color: var(--ks-border-default);
}

.implementation-picker-option[aria-checked="true"] {
    background: var(--ks-bg-hover);
    border-color: var(--ks-border-strong);
}

.implementation-picker-option-icon {
    flex-shrink: 0;
    width: var(--ks-icon-size-lg);
    height: var(--ks-icon-size-lg);
}

.implementation-picker-option-main {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 1px;
}

.implementation-picker-option-name {
    font-size: var(--ks-font-size-sm);
    font-weight: 500;
    color: var(--ks-text-primary);
    overflow-wrap: anywhere;
}

.implementation-picker-option-description {
    font-size: var(--ks-font-size-xs);
    color: var(--ks-text-secondary);
    overflow-wrap: anywhere;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
}

.implementation-picker-option-check {
    flex-shrink: 0;
    color: var(--ks-text-link);
}
</style>
