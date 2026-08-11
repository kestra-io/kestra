<template>
    <div>
        <KsButton
            type="primary"
            size="large"
            tag="a"
            target="_blank"
            :href="`https://kestra.io/demo?utm_source=app&utm_medium=referral&utm_campaign=demo-${type}`"
        >
            {{ $t("demos.get_a_demo_button") }}
        </KsButton>
    </div>
</template>

<script setup lang="ts">
    import {KsButton} from "@kestra-io/design-system"

    defineProps<{
        type: string;
    }>()
</script>
