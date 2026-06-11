<template>
    <KsDialog
        v-model="visible"
        :title="$t('customize sidebar')"
        width="480px"
        destroyOnClose
    >
        <div class="sidebar-customize">
            <div
                v-for="section in props.menu.filter(s => s.child?.length)"
                :key="menuSectionId(section)"
                class="sidebar-customize__section"
            >
                <KsText tag="h3" class="sidebar-customize__section-title">
                    {{ section.title }}
                </KsText>
                <KsCard shadow="never" :bodyStyle="CARD_BODY_STYLE">
                    <Motion
                        v-for="item in getOrderedItems(section)"
                        :key="item.id"
                        as="div"
                        layout
                        :animate="{
                            opacity: isDragging && dragItemId === item.id ? 0.35 : 1,
                            scale: isDragging && dragItemId === item.id ? 0.98 : 1,
                        }"
                        :transition="ITEM_TRANSITION"
                        class="sidebar-customize__item"
                        draggable="true"
                        @dragstart="onDragStart(section, item, $event)"
                        @dragover.prevent="onDragOver(section, item)"
                        @drop.prevent="onDrop"
                        @dragend="resetDrag"
                    >
                        <DotsGrid class="drag-handle" :size="18" />
                        <component
                            :is="item.icon?.element"
                            class="item-icon"
                            :size="18"
                        />
                        <KsText class="item-title" :class="{'item-title--hidden': !isVisible(item)}">{{ item.title }}</KsText>
                        <KsSwitch
                            :aria-label="item.title"
                            :modelValue="isVisible(item)"
                            @change="(v: boolean | string | number) => toggleVisibility(item, Boolean(v))"
                        />
                    </Motion>
                    <Motion
                        v-if="getOrderedItems(section).length === 0"
                        as="div"
                        layout
                        :animate="{
                            opacity: dropTargetEmptySectionId === menuSectionId(section) ? 1 : 0.5,
                            scale: dropTargetEmptySectionId === menuSectionId(section) ? 1.01 : 1,
                        }"
                        :transition="ITEM_TRANSITION"
                        class="sidebar-customize__empty"
                        :class="{'is-drag-over': dropTargetEmptySectionId === menuSectionId(section)}"
                        @dragover.prevent="onDragOverEmpty(section)"
                        @dragleave="dropTargetEmptySectionId = null"
                        @drop.prevent="onDropEmpty(section)"
                    >
                        {{ $t("drop items here") }}
                    </Motion>
                </KsCard>
            </div>
        </div>
        <template #footer>
            <KsButton type="default" @click="onReset">
                {{ $t("reset to defaults") }}
            </KsButton>
            <KsButton type="primary" @click="visible = false">
                {{ $t("close") }}
            </KsButton>
        </template>
    </KsDialog>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue"
    import {KsDialog, KsText, KsSwitch, KsButton, KsCard} from "@kestra-io/design-system"
    import {Motion} from "motion-v"
    import DotsGrid from "vue-material-design-icons/DotsGrid.vue"
    import {useLayoutStore} from "../../stores/layout"
    import {
        menuSectionId,
        resolveSectionItemIds,
        pickItemsByIds,
        isMenuItemVisible,
    } from "../../utils/menuCustomization"
    import type {MenuItem} from "override/components/useLeftMenu"

    const CARD_BODY_STYLE = {padding: "0"}
    const ITEM_TRANSITION = {type: "spring", stiffness: 400, damping: 30, mass: 0.6}

    const props = defineProps<{
        modelValue: boolean
        menu: MenuItem[]
    }>()

    const emit = defineEmits<{
        (e: "update:modelValue", value: boolean): void
    }>()

    const layoutStore = useLayoutStore()

    const visible = computed({
        get: () => props.modelValue,
        set: (v) => emit("update:modelValue", v),
    })

    const isDragging = ref(false)
    const dragSectionId = ref<string | null>(null)
    const dragItemId = ref<string | null>(null)
    const dropTargetEmptySectionId = ref<string | null>(null)
    const previewOrder = ref<Record<string, string[]>>({})

    function getSavedItemIds(sectionId: string): string[] {
        return resolveSectionItemIds(props.menu, layoutStore.menuItemOrder, sectionId)
    }

    function getEffectiveItemIds(sectionId: string): string[] {
        return isDragging.value && previewOrder.value[sectionId] !== undefined
            ? previewOrder.value[sectionId]
            : getSavedItemIds(sectionId)
    }

    function getOrderedItems(section: MenuItem): MenuItem[] {
        return pickItemsByIds(props.menu, getEffectiveItemIds(menuSectionId(section)))
    }

    function isVisible(item: MenuItem): boolean {
        return isMenuItemVisible(layoutStore.menuItemVisibility, item)
    }

    function toggleVisibility(item: MenuItem, value: boolean) {
        if (!item.id) return
        layoutStore.setMenuItemVisibility(item.id, value)
    }

    function onDragStart(section: MenuItem, item: MenuItem, event: DragEvent) {
        isDragging.value = true
        dragSectionId.value = menuSectionId(section)
        dragItemId.value = item.id ?? null
        previewOrder.value = {}
        if (event.dataTransfer) {
            event.dataTransfer.effectAllowed = "move"
        }
    }

    function computePreview(targetSection: MenuItem, targetItemId: string) {
        const sourceSectionId = dragSectionId.value!
        const targetSectionId = menuSectionId(targetSection)

        const sourceIds = getSavedItemIds(sourceSectionId)
        const targetIds = sourceSectionId === targetSectionId ? sourceIds : getSavedItemIds(targetSectionId)
        const toIndex = targetIds.indexOf(targetItemId)
        if (toIndex === -1 || !dragItemId.value) return

        if (sourceSectionId === targetSectionId) {
            const fromIndex = sourceIds.indexOf(dragItemId.value)
            if (fromIndex === -1 || fromIndex === toIndex) return
            const reordered = [...sourceIds]
            reordered.splice(fromIndex, 1)
            reordered.splice(toIndex, 0, dragItemId.value)
            previewOrder.value = {...previewOrder.value, [sourceSectionId]: reordered}
        } else {
            const newSourceIds = sourceIds.filter((id) => id !== dragItemId.value)
            const newTargetIds = [...targetIds]
            newTargetIds.splice(toIndex, 0, dragItemId.value)
            previewOrder.value = {
                ...previewOrder.value,
                [sourceSectionId]: newSourceIds,
                [targetSectionId]: newTargetIds,
            }
        }
    }

    function onDragOver(section: MenuItem, item: MenuItem) {
        dropTargetEmptySectionId.value = null
        if (!item.id || item.id === dragItemId.value) return
        computePreview(section, item.id)
    }

    function onDragOverEmpty(section: MenuItem) {
        dropTargetEmptySectionId.value = menuSectionId(section)
    }

    function onDrop() {
        for (const [sectionId, order] of Object.entries(previewOrder.value)) {
            layoutStore.setMenuItemOrder(sectionId, order)
        }
        resetDrag()
    }

    function onDropEmpty(targetSection: MenuItem) {
        if (!dragItemId.value) {
            resetDrag()
            return
        }
        const sourceSectionId = dragSectionId.value!
        const targetSectionId = menuSectionId(targetSection)
        if (sourceSectionId !== targetSectionId) {
            layoutStore.setMenuItemOrder(sourceSectionId, getSavedItemIds(sourceSectionId).filter((id) => id !== dragItemId.value))
            layoutStore.setMenuItemOrder(targetSectionId, [dragItemId.value])
        }
        resetDrag()
    }

    function resetDrag() {
        isDragging.value = false
        dragSectionId.value = null
        dragItemId.value = null
        dropTargetEmptySectionId.value = null
        previewOrder.value = {}
    }

    function onReset() {
        layoutStore.resetMenuCustomization()
    }
</script>

<style scoped lang="scss">
    .sidebar-customize {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-5);

        &__section-title {
            font-size: var(--ks-font-size-xs);
            font-weight: 600;
            color: var(--ks-text-secondary);
            text-transform: uppercase;
            letter-spacing: 0.06em;
            margin-bottom: var(--ks-spacing-2);
        }

        &__item {
            display: flex;
            align-items: center;
            gap: var(--ks-spacing-3);
            padding: var(--ks-spacing-3) var(--ks-spacing-4);
            background: var(--ks-bg-surface);
            cursor: grab;
            user-select: none;

            &:not(:last-child) {
                border-bottom: var(--ks-border-width-thin) solid var(--ks-border-default);
            }

            &:active {
                cursor: grabbing;
            }
        }

        &__empty {
            padding: var(--ks-spacing-4);
            text-align: center;
            color: var(--ks-text-dim);
            font-size: var(--ks-font-size-sm);
            border: var(--ks-border-width-base) dashed var(--ks-border-default);
            transition: border-color 0.1s ease;

            &.is-drag-over {
                border-color: var(--ks-text-link);
            }
        }

        .drag-handle {
            color: var(--ks-text-dim);
            flex-shrink: 0;
        }

        .item-icon {
            color: var(--ks-text-secondary);
            flex-shrink: 0;
        }

        .item-title {
            flex: 1;
            min-width: 0;

            &--hidden {
                color: var(--ks-text-secondary);
            }
        }
    }
</style>
