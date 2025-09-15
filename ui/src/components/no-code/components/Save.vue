<template>
    <div class="d-flex justify-content-end mb-5">
        <el-button type="primary">
            {{ t(`no_code.close.${props.what}`) }}
        </el-button>
    </div>
</template>

<script setup lang="ts">
    const props = defineProps({
        what: {
            type: String,
            required: false,
            default: "tasks"
        },
    });

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});
</script>
