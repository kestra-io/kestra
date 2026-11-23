<template>
    <KsDropdown>
        <KsButton :aria-label="$t('more actions')">
            <DotsVertical />
        </KsButton>
        <template #dropdown>
            <KsDropdownMenu>
                <slot />
            </KsDropdownMenu>
        </template>
    </KsDropdown>
</template>

<script setup lang="ts">
    import type {KsDropdown} from "@kestra-io/design-system"
    import DotsVertical from "vue-material-design-icons/DotsVertical.vue"

    defineProps</* @vue-ignore */ Pick<InstanceType<typeof KsDropdown>["$props"], "trigger" | "onCommand">>()
</script>

