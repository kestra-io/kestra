<template>
    <el-button @click.prevent.stop="handleClick()" type="primary" :icon="Plus">
        {{ t("add") }}
    </el-button>
</template>

<script setup lang="ts">
    import {Plus} from "../../../utils/icons";

    import {useRouter, useRoute} from "vue-router";
    const router = useRouter();
    const route = useRoute();

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    const props = defineProps({section: {type: String, required: true}});

    const handleClick = () => {
        router.replace({
            query: {
                ...route.query,
                section: props.section.toLowerCase(),
                identifier: "new",
            },
        });
    };
</script>
