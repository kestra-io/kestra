<template>
    <div class="filter-edit-container">
        <el-button
            v-if="!!filterKey"
            ref="buttonRef"
            type="text"
            size="small"
            :icon="Pencil"
            class="edit-button"
            @click.stop="toggleDialog"
        />

        <Teleport to="body">
            <Transition name="filter-popup" appear>
                <div
                    v-if="isDialogVisible"
                    class="filter-edit-overlay"
                    @click="closeDialog"
                >
                    <div
                        class="filter-edit-popup"
                        :style="positionStyle"
                        @click.stop
                    >
                        <FilterEditPopper
                            v-if="filterKey"
                            :filter="filter"
                            :filterKey="filterKey"
                            :showComparatorSelection="shouldShowComparatorInDialog"
                            @update="handleUpdate"
                            @close="closeDialog"
                        />
                    </div>
                </div>
            </Transition>
        </Teleport>
    </div>
</template>

<script setup lang="ts">
    import {ref, onMounted, onUnmounted, nextTick} from "vue";
    import {AppliedFilter, FilterKeyConfig} from "../utils/types";
    import Pencil from "vue-material-design-icons/Pencil.vue";
    import FilterEditPopper from "./FilterEditPopper.vue";

    defineProps<{
        filter: AppliedFilter;
        filterKey?: FilterKeyConfig | null;
        shouldShowComparatorInDialog?: boolean;
    }>();

    const emits = defineEmits<{
        update: [filter: AppliedFilter];
    }>();

    const buttonRef = ref<any>();
    const positionStyle = ref({});
    const isDialogVisible = ref(false);

    const updatePosition = () => {
        if (!buttonRef.value) return;

        const buttonElement = buttonRef.value.$el || buttonRef.value;
        const buttonRect = buttonElement.getBoundingClientRect();
        const scrollY = window.scrollY;
        const scrollX = window.scrollX;
        const popupWidth = 328;
        const buttonCenter = buttonRect.left + (buttonRect.width / 2);

        positionStyle.value = {
            position: "absolute",
            top: `${buttonRect.bottom + scrollY + 8}px`,
            left: `${buttonCenter + scrollX - (popupWidth / 3)}px`,
            width: `${popupWidth}px`,
        };
    };

    const toggleDialog = () => {
        isDialogVisible.value = !isDialogVisible.value;
        if (isDialogVisible.value) {
            nextTick(() => {
                updatePosition();
            });
        }
    };

    const closeDialog = () => {
        isDialogVisible.value = false;
    };

    const handleUpdate = (updatedFilter: AppliedFilter) => {
        emits("update", updatedFilter);
        closeDialog();
    };

    onMounted(() => {
        const handleResize = () => {
            if (isDialogVisible.value) {
                updatePosition();
            }
        };

        window.addEventListener("resize", handleResize);
        window.addEventListener("scroll", handleResize, true);

        onUnmounted(() => {
            window.removeEventListener("resize", handleResize);
            window.removeEventListener("scroll", handleResize, true);
        });
    });

    defineExpose({
        toggleDialog
    });
</script>
<style lang="scss" scoped>
.filter-edit-container {
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

.filter-edit-overlay {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 1000;

    .filter-edit-popup {
        background: var(--ks-background-body);
        border: 1px solid var(--ks-border-primary);
        border-radius: 8px;
        box-shadow: 2px 3px 3px 0px var(--ks-card-shadow);
        padding: 0;
        min-height: 20px;
        position: relative;
    }
}

.filter-popup-enter-active,
.filter-popup-leave-active {
    transition: all 0.2s ease-out;
}

.filter-popup-enter-from {
    opacity: 0;
    transform: translateY(-8px);
}

.filter-popup-enter-to,
.filter-popup-leave-from {
    opacity: 1;
    transform: translateY(0);
}

.filter-popup-leave-to {
    opacity: 0;
    transform: translateY(-8px);
}
</style>