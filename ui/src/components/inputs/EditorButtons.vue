<template>
    <div v-if="!isNamespace && (isAllowedEdit || canDelete)">
        <KsDropdown>
            <KsButton type="default" :disabled="isReadOnly">
                <DotsVertical title="" />
                {{ $t("actions") }}
            </KsButton>
            <template #dropdown>
                <KsDropdownMenu class="m-dropdown-menu">
                    <KsDropdownItem
                        v-if="isAllowedEdit"
                        :icon="Download"
                        size="large"
                        @click="forwardEvent('export')"
                    >
                        {{ $t("flow_export") }}
                    </KsDropdownItem>
                    <KsDropdownItem
                        v-if="!isCreating && canDelete"
                        :icon="Delete"
                        size="large"
                        @click="forwardEvent('delete-flow', $event)"
                    >
                        {{ $t("delete") }}
                    </KsDropdownItem>

                    <KsDropdownItem
                        v-if="!isCreating"
                        :icon="ContentCopy"
                        size="large"
                        @click="forwardEvent('copy', $event)"
                    >
                        {{ $t("copy") }}
                    </KsDropdownItem>
                </KsDropdownMenu>
            </template>
        </KsDropdown>
    </div>
    <div data-onboarding-target="flow-save-button">
        <KsButton
            v-if="isNamespace || isAllowedEdit"
            :icon="ContentSave"
            @click="forwardEvent(showSaveAndExecute ? 'save-and-execute' : 'save', $event)"
            :type="playgroundStore.enabled ? undefined : 'primary'"
            :class="{
                'el-button--playground': playgroundStore.enabled,
                'onboarding-save-execute-button': showSaveAndExecute,
            }"
            :disabled="hasErrors || !canSave"
            class="edit-flow-save-button"
            :id="showSaveAndExecute ? 'execute-button' : undefined"
        >
            {{ $t(showSaveAndExecute ? "save_and_execute" : "save") }}
        </KsButton>
    </div>
</template>
<script setup lang="ts">
    import {computed} from "vue"

    import DotsVertical from "vue-material-design-icons/DotsVertical.vue"

    import Delete from "vue-material-design-icons/Delete.vue"
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"
    import ContentSave from "vue-material-design-icons/ContentSave.vue"
    import Download from "vue-material-design-icons/Download.vue"
    import {usePlaygroundStore} from "../../stores/playground"

    const playgroundStore = usePlaygroundStore()

    const props = defineProps<{
        isCreating: boolean;
        isReadOnly: boolean;
        canDelete: boolean;
        isAllowedEdit: boolean;
        haveChange: boolean;
        flowHaveTasks: boolean;
        errors: string[] | undefined;
        warnings: string[] | undefined;
        isNamespace: boolean;
        showSaveAndExecute?: boolean;
    }>()

    const forwardEvent = defineEmits([
        "delete-flow",
        "copy",
        "save",
        "save-and-execute",
        "export",
    ])

    const hasErrors = computed(() => props.errors && props.errors.length > 0)

    const canSave = computed(() => {
        return props.haveChange || props.isCreating
    })
</script>

<style scoped lang="scss">
    .onboarding-save-execute-button {
        position: relative;
        z-index: 1;
        animation: onboardingSaveExecutePulse 1s ease-in-out infinite alternate;
        will-change: transform, box-shadow;
    }

    @keyframes onboardingSaveExecutePulse {
        from {
            transform: translateZ(0) scale(1);
            box-shadow:
                0 0 0 0 color-mix(in srgb, var(--ks-button-background-primary) 42%, transparent),
                0 0 14px 4px color-mix(in srgb, var(--ks-button-background-primary) 28%, transparent);
        }

        to {
            transform: translateZ(0) scale(1.04);
            box-shadow:
                0 0 0 8px color-mix(in srgb, var(--ks-button-background-primary) 12%, transparent),
                0 0 22px 8px color-mix(in srgb, var(--ks-button-background-primary) 34%, transparent),
                0 0 36px 14px color-mix(in srgb, var(--ks-button-background-primary) 20%, transparent);
        }
    }

    :global(html.dark) .onboarding-save-execute-button {
        animation-name: onboardingSaveExecutePulseDark;
    }

    @keyframes onboardingSaveExecutePulseDark {
        from {
            transform: translateZ(0) scale(1);
            box-shadow:
                0 0 0 0 color-mix(in srgb, var(--ks-button-background-primary) 54%, transparent),
                0 0 16px 5px color-mix(in srgb, var(--ks-button-background-primary) 34%, transparent);
        }

        to {
            transform: translateZ(0) scale(1.035);
            box-shadow:
                0 0 0 10px color-mix(in srgb, var(--ks-button-background-primary) 14%, transparent),
                0 0 24px 9px color-mix(in srgb, var(--ks-button-background-primary) 40%, transparent),
                0 0 42px 16px color-mix(in srgb, var(--ks-button-background-primary) 24%, transparent);
        }
    }
</style>
