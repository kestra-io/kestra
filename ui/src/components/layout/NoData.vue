<template>
    <el-empty :image :description />
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import Utils from "../../utils/utils";

    import Dark from "../../assets/dark.png";
    import Light from "../../assets/light.png";

    const image = computed(() => (Utils.getTheme() === "light" ? Light : Dark));

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    defineProps({
        description: {
            type: String,
            default: t("no_data"), // eslint-disable-line vue/valid-define-props
        },
    });
</script>
