<template>
    <div class="copy-wrapper">
        <el-tooltip trigger="click" :content="$t('copied')" placement="left" :auto-close="2000" effect="light">
            <el-button text round :icon="ContentCopy" @click="Utils.copy(text)">
                <span v-if="label">
                    {{ label }}
                </span>
            </el-button>
        </el-tooltip>
    </div>
</template>

<script setup>
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
</script>

<script>
    import Utils from "../../utils/utils";

    export default {
        props: {
            text: {
                type: String,
                required: true
            },
            label: {
                type: String,
                required: false,
                default: undefined
            }
        }
    }
</script>

<style scoped lang="scss">
    @import "@kestra-io/ui-libs/src/scss/variables";

    .copy-wrapper {
        right: $spacer;
        top: $spacer;
        z-index: 1
    }
</style>