<template>
    <KsTooltip :content="$t('flow_publish.description')" :disabled="!isPublishPrimary">
        <KsDropdown
            splitButton
            :disabled="isReadOnly || (!isPublishPrimary && !canSave)"
            :buttonProps="{disabled: isPublishPrimary && hasErrors}"
            @click="isPublishPrimary ? publish() : saveAsDraft()"
        >
            {{ isPublishPrimary ? $t("publish") : $t("save_draft") }}
            <template #dropdown>
                <KsDropdownMenu>
                    <KsDropdownItem v-if="isPublishPrimary" :icon="FileDocumentEditOutline" disabled>
                        {{ $t("save_draft") }}
                    </KsDropdownItem>
                    <KsDropdownItem v-else :icon="Publish" :disabled="hasErrors || unreachable" data-test="flow-publish" @click="publish">
                        <div class="publish-item">
                            {{ $t("publish") }}
                            <KsText size="small" type="secondary">{{ $t("flow_publish.description") }}</KsText>
                        </div>
                    </KsDropdownItem>
                    <KsDropdownItem
                        v-if="haveChange && !flowStore.isCreating"
                        :icon="PlayBoxOutline"
                        :disabled="hasErrors || isReadOnly || unreachable"
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
    import {useServerReachability} from "../../composables/useServerReachability"

    const flowStore = useFlowStore()
    const {haveChange, canSave, hasErrors, isReadOnly, isDraft, publish, saveAsDraft, saveAndExecute} = useFlowEditorActions()

    const {unreachable} = useServerReachability()

    // A draft is the only save that can wait for the server, so Publish never takes the button while there is none.
    const isPublishPrimary = computed(() => isDraft.value && !haveChange.value && !flowStore.isCreating && !unreachable.value)
</script>

<style scoped>
.publish-item {
    display: flex;
    flex-direction: column;
    max-width: 18rem;
    white-space: normal;
}
</style>
