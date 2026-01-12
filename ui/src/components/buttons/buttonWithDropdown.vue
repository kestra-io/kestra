<template>
    <div class="button-with-dropdown" :class="{'split-button': showDropdownIcon && validDropdownItems.length > 0}">
        <el-button
            :type="type"
            :size="size"
            :loading="loading"
            :disabled="disabled"
            :icon="icon"
            :aria-label="text"
            @click="onPrimaryClick"
        >
            <span v-if="text">{{ text }}</span>
        </el-button>

        <el-dropdown
            v-if="showDropdownIcon && validDropdownItems.length > 0"
            :trigger="trigger"
            :disabled="disabled"
            :hideOnClick="hideOnClick"
            aria-haspopup="menu"
            @visible-change="onVisibleChange"
        >
            <el-button
                :type="type"
                :size="size"
                :disabled="disabled"
                class="dropdown-toggle"
                :aria-label="`${text} dropdown menu`"
                :aria-expanded="dropdownVisible.toString()"
            >
                <el-icon class="el-icon--right">
                    <component :is="dropdownIcon" />
                </el-icon>
            </el-button>

            <template #dropdown>
                <el-dropdown-menu>
                    <el-dropdown-item
                        v-for="item in validDropdownItems"
                        :key="item.command"
                        :command="item.command"
                        :icon="item.icon"
                        :disabled="item.disabled"
                        :divided="item.divided"
                        @click="onItemClick(item)"
                    >
                        {{ item.label }}
                    </el-dropdown-item>
                </el-dropdown-menu>
            </template>
        </el-dropdown>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue";
    import {ArrowDown, ArrowUp} from "@element-plus/icons-vue";

    interface DropdownItem {
        command: string;
        label: string;
        icon?: any;
        disabled?: boolean;
        divided?: boolean;
        action: (item: DropdownItem) => void;
    }

    interface Props {
        text: string;
        type?: "primary" | "success" | "warning" | "danger" | "info";
        icon?: any;
        trigger?: "click" | "hover";
        dropdownItems?: DropdownItem[];
        hideOnClick?: boolean;
        showDropdownIcon?: boolean;
        size?: "large" | "default" | "small";
        loading?: boolean;
        disabled?: boolean;
    }

    const validateDropdownItems = (items: DropdownItem[]) => {
        if (!Array.isArray(items)) {
            console.warn("ButtonWithDropdown: dropdownItems must be an array");
            return [];
        }

        return items.filter((item, index) => {
            if (!item.command) {
                console.warn(`ButtonWithDropdown: dropdown item at index ${index} missing required 'command' property`);
                return false;
            }
            return true;
        });
    };

    const props = withDefaults(defineProps<Props>(), {
        type: "primary",
        hideOnClick: true,
        showDropdownIcon: true,
        size: "default",
        loading: false,
        disabled: false,
        trigger: "click",
        dropdownItems: () => []
    });

    const validDropdownItems = computed(() => validateDropdownItems(props.dropdownItems));
    const dropdownIcon = computed(() => dropdownVisible.value ? ArrowUp : ArrowDown);

    interface Emits {
        (e: "primary-click"): void;
        (e: "item-click", item: DropdownItem): void;
        (e: "visible-change", visible: boolean): void;
    }

    const emit = defineEmits<Emits>();
    const dropdownVisible = ref(false);

    const onPrimaryClick = () => {
        emit("primary-click");
    };

    const onItemClick = (item: DropdownItem) => {
        item.action(item);
        emit("item-click", item);
    };

    const onVisibleChange = (visible: boolean) => {
        dropdownVisible.value = visible;
        emit("visible-change", visible);
    };
</script>

<style scoped lang="scss">
.button-with-dropdown {
    display: inline-flex;
    align-items: center;

    .el-button.is-disabled {
        opacity: 0.6;
        cursor: not-allowed;
        pointer-events: none;
    }

    &.split-button {
        .el-button {
            &:first-child {
                border-top-right-radius: 0;
                border-bottom-right-radius: 0;
                border-right: 1px solid var(--el-button-border-color);

                &.is-disabled {
                    border-right: none;
                }
            }

            &.dropdown-toggle {
                border-top-left-radius: 0;
                border-bottom-left-radius: 0;
                border-top-right-radius: var(--el-border-radius-base);
                border-bottom-right-radius: var(--el-border-radius-base);
                border-left: none;
                padding-left: 8px;
                padding-right: 8px;
                min-width: 40px;
                position: relative;

                &::before {
                    content: '';
                    position: absolute;
                    left: -1px;
                    top: 0;
                    width: 1px;
                    height: 100%;
                    background-color: var(--el-button-active-bg-color);
                    z-index: 1;
                }

                .el-icon {
                    margin: 0;
                }

                &:hover {
                    &::before {
                        background-color: var(--el-button-active-bg-color);
                    }
                }

                &.is-disabled {
                    border-left: none;

                    &::before {
                        display: none;
                    }
                }
            }

            &.is-disabled + .el-dropdown .el-button {
                opacity: 0.6;
                cursor: not-allowed;
                pointer-events: none;
            }
        }
    }

    &:not(.split-button) {
        .el-button {
            .el-icon.el-icon--right {
                margin-left: 8px;
            }
        }
    }
}

.el-dropdown-menu {
    min-width: 160px;

    .el-dropdown-menu__item {
        display: flex;
        align-items: center;
        gap: var(--spacing-xs, 4px);

        &.is-disabled {
            opacity: 0.6;
            cursor: not-allowed;
        }

        .el-icon {
            font-size: 14px;
        }
    }
}

@media (max-width: 768px) {
    .el-dropdown-menu {
        min-width: 140px;
    }
}
</style>