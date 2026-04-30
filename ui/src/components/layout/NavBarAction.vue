<template>
    <el-dropdown-item
        v-if="asItem"
        :icon="icon"
        v-bind="$attrs"
        @click="onClick"
    >
        <slot>{{ label }}</slot>
    </el-dropdown-item>
    <el-button
        v-else
        :type="type ?? 'default'"
        :icon="icon"
        v-bind="$attrs"
        @click="onClick"
    >
        <slot>{{ label }}</slot>
    </el-button>
</template>

<script setup lang="ts">
    import {inject, type Component} from "vue";
    import {useRouter, type RouteLocationRaw} from "vue-router";
    import {asItemKey} from "./navBarActionsContext";

    defineOptions({inheritAttrs: false});

    const props = defineProps<{
        icon?: Component;
        type?: string;
        label?: string;
        to?: RouteLocationRaw;
    }>();

    const emit = defineEmits<{(e: "click"): void}>();

    const asItem = inject(asItemKey, false);

    const router = useRouter();

    const onClick = () => {
        if (props.to) {
            router.push(props.to);
        }
        emit("click");
    };
</script>
