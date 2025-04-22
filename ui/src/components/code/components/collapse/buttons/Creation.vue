<template>
    <el-button @click.prevent.stop="handleClick()" type="primary" :icon="Plus">
        {{ t("add") }}
    </el-button>
</template>

<script setup lang="ts">
    import {inject, ref} from "vue";
    import {SECTION_INJECTION_KEY, CREATING_INJECTION_KEY} from "../../../injectionKeys";
    import {Plus} from "../../../utils/icons";

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    const props = defineProps({section: {type: String, required: true}});

    const sectionInjected = inject(SECTION_INJECTION_KEY, ref(""));
    const creating = inject(CREATING_INJECTION_KEY, ref(false));

    const handleClick = () => {
        sectionInjected.value = props.section.toLowerCase();
        creating.value = true;
    };
</script>
