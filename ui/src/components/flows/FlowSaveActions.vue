<template>
    <KsTooltip :content="$t('flow_publish.description')" :disabled="!isPublishPrimary">
        <KsDropdown
            splitButton
            :disabled="isReadOnly || (!isPublishPrimary && !canSave)"
            :mainButtonDisabled="isPublishPrimary && hasErrors"
            @click="isPublishPrimary ? publish() : saveAsDraft()"
        >
            {{ isPublishPrimary ? $t("publish") : $t("save_draft") }}
            <template #dropdown>
                <KsDropdownMenu>
                    <KsDropdownItem v-if="isPublishPrimary" :icon="FileDocumentEditOutline" disabled>
                        {{ $t("save_draft") }}
                    </KsDropdownItem>
                    <KsDropdownItem v-else :icon="Publish" :disabled="hasErrors" data-test="flow-publish" @click="publish">
                        <div class="publish-item">
                            {{ $t("publish") }}
                            <KsText size="small" type="secondary">{{ $t("flow_publish.description") }}</KsText>
                        </div>
                    </KsDropdownItem>
                    <KsDropdownItem
                        v-if="haveChange && !flowStore.isCreating"
                        :icon="PlayBoxOutline"
                        :disabled="hasErrors || isReadOnly"
                        @click="saveAndExecute"
                    >
                        {{ $t("save_and_execute") }}
                    </KsDropdownItem>
                </KsDropdownMenu>
            </template>
        </KsDropdown>
    </KsTooltip>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import FileDocumentEditOutline from "vue-material-design-icons/FileDocumentEditOutline.vue"
    import PlayBoxOutline from "vue-material-design-icons/PlayBoxOutline.vue"
    import Publish from "vue-material-design-icons/Publish.vue"
    import {useFlowStore} from "../../stores/flow"
    import {useFlowEditorActions} from "./useFlowEditorActions"

    const flowStore = useFlowStore()
    const {haveChange, canSave, hasErrors, isReadOnly, isDraft, publish, saveAsDraft, saveAndExecute} = useFlowEditorActions()

    const isPublishPrimary = computed(() => isDraft.value && !haveChange.value && !flowStore.isCreating)
</script>

<style scoped>
.publish-item {
    display: flex;
    flex-direction: column;
    max-width: 18rem;
    white-space: normal;
}
</style>
