<template>
    <el-empty :image :image-size="180">
        <template #description>
            <span v-html="description" />
        </template>
    </el-empty>
</template>

<script setup lang="ts">
    import {computed} from "vue";

    import Utils from "../../utils/utils";

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import Dark from "../../assets/dark.png";
    import Light from "../../assets/light.png";

    const props = defineProps({text: {type: String, default: undefined}});

    const image = computed(() => (Utils.getTheme() === "light" ? Light : Dark));
    const description = computed(() => props.text ?? t("no_data"));
</script>

<style lang="scss">
.el-empty {
    padding-top: 0;

    .el-empty__description {
        font-size: var(--el-font-size-small);
    }
}
</style>
