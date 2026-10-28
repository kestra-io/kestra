<template>
    <div class="iteration-selector" data-test="task-run-iteration-selector">
        <div class="search">
            <KsSearch
                v-model="query"
                :placeholder="$t('search')"
                :aria-label="$t('iteration')"
            />
        </div>
        <KsScrollbar :maxHeight="240">
            <KsDropdownItem
                v-for="entry in filteredEntries"
                :key="entry.run.id"
                data-test="task-run-iteration"
                @click.stop="emit('select', entry.run.id)"
            >
                <span :class="['row', {active: entry.run.id === selectedId}]">
                    <KsIcon size="xs" class="check">
                        <Check />
                    </KsIcon>
                    <span class="name" :title="entry.label">{{ entry.label }}</span>
                </span>
            </KsDropdownItem>

            <KsText
                v-if="!filteredEntries.length"
                size="small"
                class="empty"
            >
                {{ $t("dependency.search.no_results", {term: query}) }}
            </KsText>
        </KsScrollbar>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue"
    import {useI18n} from "vue-i18n"
    import Check from "vue-material-design-icons/Check.vue"
    import type {ExecutionTaskRun} from "./executionTaskRun"
    import {iterationLabel} from "./iterationLabel"

    const props = defineProps<{
        taskRuns: ExecutionTaskRun[]
        selectedId?: string
    }>()

    const emit = defineEmits<{
        select: [taskRunId: string]
    }>()

    const {t} = useI18n()

    const query = ref("")

    const filteredEntries = computed(() => {
        const needle = query.value.trim().toLowerCase()
        const entries = props.taskRuns.map((run, index) => ({run, label: iterationLabel(t, run, index + 1)}))
        return needle ? entries.filter(({label}) => label.toLowerCase().includes(needle)) : entries
    })
</script>

<style scoped>
    .iteration-selector {
        border-bottom: var(--ks-border-block-primary);
        padding: var(--ks-spacing-2);

        .search {
            padding-bottom: var(--ks-spacing-2);
        }

        .row {
            display: flex;
            align-items: center;
            gap: var(--ks-spacing-2);
            width: 100%;
            min-width: 0;

            .check {
                visibility: hidden;
                color: var(--ks-text-link);
                flex: 0 0 auto;
            }

            &.active .check {
                visibility: visible;
            }

            .name {
                flex: 1 1 auto;
                min-width: 0;
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
            }
        }

        .empty {
            display: block;
            color: var(--ks-text-muted);
        }
    }
</style>
