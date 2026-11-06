<template>
    <el-empty :image="noData" :imageSize="180">
        <template #description>
            <!-- nosemgrep: kestra-vue-avoid-v-html -- description is translated markup from the caller, who must escape any interpolated value. -->
            <span v-html="description" />
        </template>
    </el-empty>
</template>

<script setup lang="ts">
    import {computed} from "vue";

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import noData from "../../assets/no_data.png";

    const props = defineProps({text: {type: String, default: undefined}});

    const description = computed(() => props.text ?? t("no_data"));
</script>

<style scoped lang="scss">
:deep(.el-empty__description) {
    font-size: var(--el-font-size-small);
    color: var(--ks-content-secondary);
}
</style>
