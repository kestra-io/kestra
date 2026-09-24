<template>
    <BlockSectionCard
        :name="section"
        :title="title"
        :icon="icon"
        :count="blocks.length"
        :addLabel="addLabel"
        :addTest="addTest"
        :tone="tone"
        @add="(e: Event) => emit('add', e)"
    >
        <div
            class="block-section-list"
            :data-test="listTest"
            @dragend="onDragEnd"
        >
            <template v-for="(block, index) in blocks" :key="resolveBlockDomId(blocks, index)">
                <FlowableClusterCard
                    v-if="supportsFlowable && isFlowable(block)"
                    :block="block"
                    :path="`${section}[${index}]`"
                    :icons="icons"
                    :selectedId="selectedId"
                    :focusedId="focusedId"
                    :domId="resolveBlockDomId(blocks, index)"
                    :depth="0"
                    :playgroundEnabled="playgroundEnabled"
                    :draggable="true"
                    :data-block-id="resolveBlockDomId(blocks, index)"
                    data-test="block-card"
                    @select="(path: string) => emit('selectPath', path)"
                    @open-split="(path: string) => emit('openSplitPath', path)"
                    @delete="(path: string) => emit('deletePath', path)"
                    @duplicate="(path: string) => emit('duplicatePath', path)"
                    @run="(id: string) => emit('run', id)"
                    @add-at-path="(parentPath: string, refIndex: number, evt?: Event, position?: 'before' | 'after') => emit('addAtPath', parentPath, refIndex, evt, position)"
                    @update-depends-on="(itemPath: string, dependsOn: string[]) => emit('updateDependsOn', itemPath, dependsOn)"
                    @drag-start="onItemDragStart($event, index)"
                    @drag-over="onItemDragOver($event, index)"
                    @drop="onItemDrop($event, index)"
                    @drag-end="onDragEnd"
                />
                <BlockCard
                    v-else
                    :block="block"
                    :selected="selectedId === String(block.id)"
                    :focused="focusedId === resolveBlockDomId(blocks, index)"
                    :draggable="true"
                    :dragOver="dragOverIndex === index"
                    :runnable="playgroundEnabled"
                    :icons="icons"
                    :data-block-id="resolveBlockDomId(blocks, index)"
                    @select="emit('select', block)"
                    @delete="emit('delete', block.id)"
                    @duplicate="emit('duplicate', block.id)"
                    @open-split="emit('openSplit', block)"
                    @run="emit('run', String(block.id))"
                    @drag-start="onItemDragStart($event, index)"
                    @drag-over="onItemDragOver($event, index)"
                    @drop="onItemDrop($event, index)"
                    @drag-end="onDragEnd"
                />
            </template>

            <BlockEmptyDrop
                v-if="blocks.length === 0"
                variant="empty"
                :dataTest="endDropTest"
                :label="emptyLabel"
                :hint="emptyHint"
                :dropState="trailingDropState"
                :data-block-id="sentinelId"
                :class="{'block-kbd-focused': focusedId === sentinelId}"
                :tabindex="focusedId === sentinelId ? 0 : -1"
                :aria-selected="focusedId === sentinelId"
                @add="(e: Event) => emit('add', e)"
                @dragover="onTrailingDragOver"
                @drop="onTrailingDrop"
            />
            <BlockEmptyDrop
                v-else
                variant="inline"
                tabindex="-1"
                :dataTest="endDropTest"
                :label="emptyLabel"
                :hint="emptyHint"
                :dropState="trailingDropState"
                @add="(e: Event) => emit('add', e)"
                @dragover="onTrailingDragOver"
                @drop="onTrailingDrop"
            />
        </div>
    </BlockSectionCard>
</template>

<script setup lang="ts">
    import {computed, type Component} from "vue"
    import BlockCard from "./BlockCard.vue"
    import BlockSectionCard from "./BlockSectionCard.vue"
    import BlockEmptyDrop from "./BlockEmptyDrop.vue"
    import FlowableClusterCard from "./FlowableClusterCard.vue"
    import {isFlowableType, resolveBlockDomId, type BlockSection} from "../../../utils/flowableBlockOps"
    import {sectionSentinelId} from "./blockSections"
    import type {PluginIconData} from "../../../stores/plugins"
    import {useLaneDrag} from "./useLaneDrag"

    const props = defineProps<{
        section: BlockSection
        title: string
        icon: Component
        addLabel: string
        addTest?: string
        tone?: "default" | "error" | "warning"
        blocks: Record<string, unknown>[]
        icons?: Record<string, PluginIconData>
        selectedId?: string
        focusedId?: string
        playgroundEnabled: boolean
        supportsFlowable?: boolean
        emptyLabel: string
        emptyHint?: string
        listTest?: string
        endDropTest?: string
    }>()

    const emit = defineEmits<{
        add: [event: Event]
        select: [block: Record<string, unknown>]
        openSplit: [block: Record<string, unknown>]
        delete: [id: unknown]
        duplicate: [id: unknown]
        run: [id: string]
        selectPath: [path: string]
        openSplitPath: [path: string]
        deletePath: [path: string]
        duplicatePath: [path: string]
        addAtPath: [parentPath: string, refIndex: number, evt?: Event, position?: "before" | "after"]
        updateDependsOn: [itemPath: string, dependsOn: string[]]
    }>()

    const {
        dragOverIndex,
        dropState: trailingDropState,
        onItemDragStart,
        onItemDragOver,
        onItemDrop,
        onDragEnd,
        onTrailingDragOver,
        onTrailingDrop,
    } = useLaneDrag(() => props.section, () => props.blocks.length)

    const sentinelId = computed(() => sectionSentinelId(props.section))

    const isFlowable = (block: Record<string, unknown>) => isFlowableType(String(block.type ?? ""), props.icons)
</script>

<style scoped lang="scss">
    .block-section-list {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
    }
</style>
