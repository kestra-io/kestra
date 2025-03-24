<template>
    <el-button type="primary" :icon="ContentSave">
        {{ t(`no_code.save.${props.what}`) }}
    </el-button>
</template>

<script setup lang="ts">
    import {ContentSave} from "../utils/icons";

    const props = defineProps({
        what: {type: String, required: true, default: "tasks"},
    });

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});
</script>
