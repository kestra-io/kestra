<template>
    <el-dropdown
        v-if="!split"
        :trigger="trigger"
        @command="handleCommand"
        popperClass="button-dropdown-popper"
        v-bind="$attrs"
    >
        <el-button :type="type">
            <template #default>
                <div class="d-flex align-items-center gap-2">
                    <component :is="icon" v-if="icon" />
                    <slot name="label">
                        {{ label }}
                    </slot>
                </div>
            </template>
        </el-button>
        <template #dropdown>
            <el-dropdown-menu>
                <slot />
            </el-dropdown-menu>
        </template>
    </el-dropdown>

    <el-dropdown
        v-else
        splitButton
        :type="type"
        :trigger="trigger"
        @click="handleClick"
        @command="handleCommand"
        popperClass="button-dropdown-popper"
        v-bind="$attrs"
    >
        <template #default>
            <div class="d-flex align-items-center gap-2">
                <component :is="icon" v-if="icon" />
                <slot name="label">
                    {{ label }}
                </slot>
            </div>
        </template>
        <template #dropdown>
            <el-dropdown-menu>
                <slot />
            </el-dropdown-menu>
        </template>
    </el-dropdown>
</template>

<script setup lang="ts">
// Compiler macros defineProps, defineEmits, defineOptions are automatically available in <script setup>

    defineOptions({
        inheritAttrs: false
    });

    defineProps({
        label: {
            type: String,
            default: undefined
        },
        icon: {
            type: Object,
            default: undefined
        },
        type: {
            type: String,
            default: "primary"
        },
        trigger: {
            type: String,
            default: "click"
        },
        split: {
            type: Boolean,
            default: true
        }
    });

    const emit = defineEmits(["click", "command"]);

    const handleClick = (event: Event) => {
        emit("click", event);
    };

    const handleCommand = (command: string | number | object) => {
        emit("command", command);
    };
</script>

<style lang="scss">
    .button-dropdown-popper {
        .el-dropdown-menu__item {
            display: flex;
            align-items: center;
            gap: 0.5rem;
        }
    }
</style>
