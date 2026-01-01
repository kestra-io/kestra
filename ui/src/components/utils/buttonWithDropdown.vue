<template>
    <!-- Split Button Container -->
    <div class="button-with-dropdown" :class="{'split-button': showDropdownIcon && validDropdownItems.length > 0}">
        <!-- Primary Button -->
        <el-button
            :type="primaryButtonType"
            :size="size"
            :loading="loading"
            :disabled="disabled"
            :icon="primaryIcon"
            :class="buttonClass"
            :aria-label="primaryText || 'Primary action'"
            @click="onPrimaryClick"
        >
            <span v-if="$slots.primaryText || primaryText">
                <slot name="primaryText">{{ primaryText }}</slot>
            </span>
        </el-button>

        <!-- Dropdown Button (only shown if there are dropdown items) -->
        <el-dropdown
            v-if="showDropdownIcon && validDropdownItems.length > 0"
            :trigger="trigger"
            :placement="placement"
            :disabled="disabled"
            :hideOnClick="hideOnClick"
            aria-haspopup="menu"
            @visible-change="onVisibleChange"
            @command="onCommand"
        >
            <el-button
                :type="primaryButtonType"
                :size="size"
                :disabled="disabled"
                class="dropdown-toggle"
                :aria-label="`${primaryText || 'Actions'} dropdown menu`"
                :aria-expanded="dropdownVisible.toString()"
            >
                <el-icon class="el-icon--right">
                    <ArrowDown />
                </el-icon>
            </el-button>

            <!-- Dropdown Menu -->
            <template #dropdown>
                <el-dropdown-menu :class="menuClass">
                    <!-- Dropdown Items -->
                    <el-dropdown-item
                        v-for="item in validDropdownItems"
                        :key="item.command"
                        :command="item.command"
                        :icon="item.icon"
                        :disabled="item.disabled"
                        :divided="item.divided"
                        @click="onItemClick(item)"
                    >
                        <span v-if="item.label">{{ item.label }}</span>
                        <slot 
                            v-else 
                            :name="`item-${item.command}`"
                            v-bind="{item}"
                        />
                    </el-dropdown-item>

                    <!-- Custom Slot for Dropdown Items -->
                    <slot name="dropdownItems" />
                </el-dropdown-menu>
            </template>
        </el-dropdown>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue";
    import {ArrowDown} from "@element-plus/icons-vue";

    // Types
    interface DropdownItem {
        command: string;
        label?: string;
        icon?: any;
        disabled?: boolean;
        divided?: boolean;
        action?: (item: DropdownItem) => void;
    }

    // Props
    interface Props {
        // Primary Button Props
        primaryText?: string;
        primaryButtonType?: "primary" | "success" | "warning" | "danger" | "info" | "default";
        primaryIcon?: any;
        primaryAction?: () => void;
    
        // Dropdown Props
        dropdownItems?: DropdownItem[];
        trigger?: "click" | "hover" | "contextmenu";
        placement?: string;
        hideOnClick?: boolean;
        showDropdownIcon?: boolean;
    
        // Common Props
        size?: "large" | "default" | "small";
        loading?: boolean;
        disabled?: boolean;
    
        // Styling Props
        buttonClass?: string | string[] | object;
        menuClass?: string | string[] | object;
    }

    // Prop validation function
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
        primaryButtonType: "primary",
        trigger: "click",
        placement: "bottom",
        hideOnClick: true,
        showDropdownIcon: true,
        size: "default",
        loading: false,
        disabled: false,
        dropdownItems: () => [],
        buttonClass: "",
        menuClass: ""
    });

    // Computed property for validated dropdown items
    const validDropdownItems = computed(() => validateDropdownItems(props.dropdownItems));

    // Emits
    interface Emits {
        (e: "primary-click"): void;
        (e: "item-click", item: DropdownItem): void;
        (e: "visible-change", visible: boolean): void;
        (e: "command", command: string): void;
    }

    const emit = defineEmits<Emits>();

    const dropdownVisible = ref(false);

    const onPrimaryClick = () => {
        if (props.primaryAction) {
            props.primaryAction();
        }
        emit("primary-click");
    };

    const onItemClick = (item: DropdownItem) => {
        if (item.action) {
            item.action(item);
        }
        emit("item-click", item);
    };

    const onVisibleChange = (visible: boolean) => {
        dropdownVisible.value = visible;
        emit("visible-change", visible);
    };

    const onCommand = (command: string) => {
        emit("command", command);
    };
</script>

<style scoped lang="scss">
.button-with-dropdown {
    display: inline-flex;
    align-items: center;

    &.split-button {
        .el-button {
            &:first-child {
                border-top-right-radius: 0;
                border-bottom-right-radius: 0;
                border-right: 1px solid var(--el-button-border-color, var(--ks-border-primary));

                // Hide border when disabled
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
                &.is-disabled,
                &.dropdown-toggle.is-disabled,
                &.el-button.is-disabled {
                    border-left: none;
                    
                    &::before {
                        display: none;
                    }
                }
            }
        }
    }

    // When not in split mode, show as regular button
    &:not(.split-button) {
        .el-button {
            .el-icon.el-icon--right {
                margin-left: 8px;
            }
        }
    }
}

           
.button-with-dropdown {
    .el-button.is-disabled {
        opacity: 0.6;
        cursor: not-allowed;
        pointer-events: none;
    }
    
    &.split-button {
        .el-button {
            &:first-child.is-disabled {
                border-right: none;
            }
            
            &.dropdown-toggle.is-disabled {
                border-left: none;
                
                &::before {
                    display: none;
                }
            }
            
            &.is-disabled + .el-dropdown .el-button {
                opacity: 0.6;
                cursor: not-allowed;
                pointer-events: none;
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