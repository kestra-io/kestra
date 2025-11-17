<template>
    <div class="edit-container">
        <el-button
            v-if="!!filterKey"
            ref="buttonRef"
            link
            size="small"
            :icon="PencilOutline"
            class="edit-button"
            @click.stop="toggleDialog"
        />

        <Teleport to="body">
            <Transition name="filter-popup" appear>
                <div
                    v-if="isDialogVisible"
                    class="edit-overlay"
                    @click="closeDialog"
                >
                    <div
                        class="edit-popup"
                        :style="positionStyle"
                        @click.stop
                    >
                        <FilterEditPopper
                            v-if="filterKey"
                            :filter="filter"
                            :filterKey="filterKey"
                            :showComparatorSelection="shouldShowComparatorInPopper"
                            @update="handleUpdate"
                            @close="closeDialog"
                            @remove="handleRemove"
                        />
                    </div>
                </div>
            </Transition>
        </Teleport>
    </div>
</template>

<script setup lang="ts">
    import {nextTick, onMounted, onUnmounted, ref} from "vue";
    import {PencilOutline} from "../../utils/icons";
    import {AppliedFilter, FilterKeyConfig} from "../../utils/filterTypes";
    import FilterEditPopper from "./FilterEditPopper.vue";

    defineProps<{
        filter: AppliedFilter;
        filterKey?: FilterKeyConfig | null;
        shouldShowComparatorInPopper?: boolean;
    }>();

    const emits = defineEmits<{
        update: [filter: AppliedFilter];
        remove: [filterId: string];
    }>();

    const buttonRef = ref<any>();
    const positionStyle = ref({});
    const isDialogVisible = ref(false);

    const updatePosition = () => {
        if (!buttonRef.value) return;

        const buttonElement = buttonRef.value.$el || buttonRef.value;
        const chipElement = buttonElement.closest(".chip");
        
        if (!chipElement) return;
        
        const chipRect = chipElement.getBoundingClientRect();
        const scrollY = window.scrollY;
        const scrollX = window.scrollX;
        const popupWidth = 300;

        positionStyle.value = {
            position: "absolute",
            top: `${chipRect.bottom + scrollY + 8}px`,
            left: `${chipRect.left + scrollX}px`,
            width: `${popupWidth}px`
        };
    };

    const toggleDialog = () => {
        isDialogVisible.value = !isDialogVisible.value;
        if (isDialogVisible.value) nextTick(updatePosition);
    };

    const closeDialog = () => {
        isDialogVisible.value = false;
    };

    const handleUpdate = (updatedFilter: AppliedFilter) => {
        emits("update", updatedFilter);
        closeDialog();
    };

    const handleRemove = (filterId: string) => {
        emits("remove", filterId);
        closeDialog();
    };

    onMounted(() => {
        const handleResize = () => {
            if (isDialogVisible.value) updatePosition();
        };

        window.addEventListener("resize", handleResize);
        window.addEventListener("scroll", handleResize, true);

        onUnmounted(() => {
            window.removeEventListener("resize", handleResize);
            window.removeEventListener("scroll", handleResize, true);
        });
    });

    defineExpose({toggleDialog, isDialogVisible});
</script>
<style lang="scss" scoped>
.edit-container {
    position: relative;
    display: inline-block;

    .edit-button {
        border: none;
        background: none;
        cursor: pointer;
        padding: 0;
        color: var(--ks-content-tertiary);

        &:hover {
            color: var(--ks-content-secondary);
        }

        :deep(svg) {
            font-size: 14px;
        }
    }
}

.edit-overlay {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 1000;

    .edit-popup {
        background: var(--ks-dropdown-background);
        border: 1px solid var(--ks-border-primary);
        border-radius: 8px;
        box-shadow: rgba(0, 0, 0, 0.09) 0px 3px 12px;
        padding: 0;
        min-height: 20px;
        position: relative;
    }
}

.filter-popup-enter-active,
.filter-popup-leave-active {
    transition: all 0.1s ease-out;
}

.filter-popup-enter-from {
    opacity: 0;
    transform: translateY(-4px);
}

.filter-popup-enter-to,
.filter-popup-leave-from {
    opacity: 1;
    transform: translateY(0);
}

.filter-popup-leave-to {
    opacity: 0;
    transform: translateY(-4px);
}
</style>