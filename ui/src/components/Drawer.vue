<template>
    <el-drawer
        v-model="modelValue"
        destroyOnClose
        lockScroll
        size=""
        :appendToBody="true"
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

<script setup lang="ts">
    import {ref} from "vue";
    import Fullscreen from "vue-material-design-icons/Fullscreen.vue"

    const props = defineProps({
        title: {
            type: String,
            default: undefined
        },
        fullScreen: {
            type: Boolean,
            default: false
        }
    });

    const modelValue = defineModel({
        type: Boolean,
        required: true
    });

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
