<template>
    <el-drawer
        data-component="FILENAME_PLACEHOLDER"
        v-model="modelValue"
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
                <Fullscreen :title="t('toggle fullscreen')" @click="toggleFullScreen" />
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

<script lang="ts" setup>
    import {ref} from "vue";
    import {useI18n} from "vue-i18n";
    import Fullscreen from "vue-material-design-icons/Fullscreen.vue"

    const {t} = useI18n();

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