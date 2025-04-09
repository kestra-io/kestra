<template>
    <div class="docs-button-row">
        <ContextDocsLink v-for="(button, index) in buttons" :key="button.label" :href="button.href.slice(6).replace(/#.+$/g, '')">
            <el-button :type="index===0?'primary':''" size="large">
                {{ button.label }}
            </el-button>
        </ContextDocsLink>
    </div>
</template>

<script setup>
    import ContextDocsLink from "../docs/ContextDocsLink.vue";

    defineProps({
        buttons: {
            type: Array,
            default: () => []
        }
    })
</script>

<style lang="scss" scoped>
.docs-button-row {
    display: flex;
    justify-content: flex-start;
    gap:.5rem;
    margin-bottom: 3rem;
    flex-wrap: wrap;
}
</style>