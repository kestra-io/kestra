<template>
    <KsDropdown ref="dropdown" trigger="click" persistent>
        <KsIconButton :ariaLabel="$t('actions')">
            <DotsVertical />
        </KsIconButton>
        <template #dropdown>
            <KsDropdownMenu>
                <slot />
                <KsDropdownItem
                    v-if="isMobile && bookmarkAction"
                    :icon="bookmarkAction.bookmarked ? Star : StarOutline"
                    data-test="menu-bookmark"
                    @click="bookmarkAction.toggle()"
                >
                    {{ $t("topnav_bookmark") }}
                </KsDropdownItem>
            </KsDropdownMenu>
        </template>
    </KsDropdown>
</template>

<script setup lang="ts">
    import {onBeforeUnmount, onMounted, provide, ref} from "vue"
    import DotsVertical from "vue-material-design-icons/DotsVertical.vue"
    import Star from "vue-material-design-icons/Star.vue"
    import StarOutline from "vue-material-design-icons/StarOutline.vue"
    import {asItemKey, closeDropdownKey} from "./navBarActionsContext"
    import {useBookmarkAction} from "../../composables/useBookmarkAction"
    import {useIsMobile} from "../../composables/useIsMobile"

    const props = defineProps<{standalone?: boolean}>()

    const dropdown = ref<{handleClose: () => void}>()
    const isMobile = useIsMobile()
    const {bookmarkAction, actionsMenuCount} = useBookmarkAction()

    onMounted(() => {
        if (!props.standalone) actionsMenuCount.value++
    })
    onBeforeUnmount(() => {
        if (!props.standalone) actionsMenuCount.value--
    })

    provide(asItemKey, true)
    // Element Plus skips its own dismissal when the click was preventDefault-ed, which is what
    // RouterLink does on every click it handles, so an item holding a link has to close the menu.
    provide(closeDropdownKey, () => dropdown.value?.handleClose())
</script>
