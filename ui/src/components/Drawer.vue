<template>
    <el-drawer
        :data-component="dataComponent"
        :model-value="props.modelValue"
        @update:model-value="emit('update:modelValue', $event)"
        destroy-on-close
        lock-scroll
        size=""
        :append-to-body="true"
        :class="{'full-screen': fullScreen}"
        ref="editorDomElement"
    >
        <template #header>
            <span>
                {{ title }}
                <slot name="header" />
            </span>
            <el-button link class="full-screen">
                <Fullscreen :title="$t('toggle fullscreen')" @click="toggleFullScreen" />
            </el-button>
        </template>

        <template #footer>
            <slot name="footer" />
        </template>

        <template #default>
            <slot />
        </template>
    </el-drawer>
</template>

<script setup>
    import {ref} from "vue";
    import Fullscreen from "vue-material-design-icons/Fullscreen.vue"
    import useDataComponent from "../composables/useDataComponent";

    const props = defineProps({
        modelValue: {
            type: Boolean,
            required: true
        },
        title: {
            type: String,
            required: false,
            default: undefined
        },
        fullScreen: {
            type: Boolean,
            required: false,
            default: false
        }
    });

    const dataComponent = useDataComponent();
    const emit = defineEmits(["update:modelValue"])

    const fullScreen = ref(props.fullScreen);

    const toggleFullScreen = () => {
        fullScreen.value = !fullScreen.value;
    }
</script>

<style scoped lang="scss">
    button.full-screen {
        font-size: 24px;
    }
</style>