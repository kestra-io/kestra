<template>
    <div class="p-4 h-100">
        <span class="fs-6 fw-bold">
            {{ t("dashboard.next_scheduled_executions") }}
        </span>

        <div class="pt-4">
            <span class="align-middle">
                {{ t("dashboard.not_auth") }}
            </span>
        </div>
    </div>
</template>

<script setup>
    import {useI18n} from "vue-i18n";

    const {t} = useI18n({useScope: "global"});
</script>