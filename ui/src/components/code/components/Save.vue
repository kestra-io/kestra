<template>
    <div class="d-flex justify-content-end mb-5">
        <el-button type="primary" :icon="saveMode === 'button' ? ContentSave: undefined">
            {{ t(`no_code.${saveOrClose}.${props.what}`) }}
        </el-button>
    </div>
</template>

<script setup lang="ts">
    import {computed, inject} from "vue";
    import {ContentSave} from "../utils/icons";
    import {SAVEMODE_INJECTION_KEY} from "../injectionKeys";

    const props = defineProps({
        what: {
            type: String,
            required: false,
            default: "tasks"
        },
    });

    const saveMode = inject(SAVEMODE_INJECTION_KEY, "button");

    const saveOrClose = computed(() => {
        if (saveMode === "button") {
            return "save";
        } else if (saveMode === "auto") {
            return "close";
        }
        return "save";
    });

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});
</script>
