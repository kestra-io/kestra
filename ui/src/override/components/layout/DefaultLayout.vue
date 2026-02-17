<template>
    <LeftMenu v-if="miscStore.configs && !layoutStore.sideMenuCollapsed" @menu-collapse="onMenuCollapse" />
    <main>
        <Errors v-if="coreStore.error" :code="coreStore.error" />
        <slot v-else />
    </main>
    <ContextInfoBar v-if="miscStore.configs" />

    <SurveyDialog
        :visible="showSurveyDialog"
        @close="handleSurveyDialogClose"
    />
</template>

<script setup lang="ts">
    import LeftMenu from "override/components/LeftMenu.vue"
    import Errors from "../../../components/errors/Errors.vue"
    import ContextInfoBar from "../../../components/ContextInfoBar.vue"
    import SurveyDialog from "../../../components/SurveyDialog.vue"
    import {onMounted, ref, watch} from "vue"
    import {useSurveySkip} from "../../../composables/useSurveyData"
    import {useCoreStore} from "../../../stores/core"
    import {useMiscStore} from "override/stores/misc"
    import {useLayoutStore} from "../../../stores/layout"

    const coreStore = useCoreStore()
    const miscStore = useMiscStore()
    const layoutStore = useLayoutStore()
    const {markSurveyDialogShown} = useSurveySkip()
    const showSurveyDialog = ref(false)

    function onMenuCollapse(collapse: boolean) {
        layoutStore.setSideMenuCollapsed(collapse)
    }

    function handleSurveyDialogClose() {
        showSurveyDialog.value = false
        markSurveyDialogShown()
        localStorage.removeItem("showSurveyDialogAfterLogin")
    }

    function checkForSurveyDialog() {
        const shouldShow = localStorage.getItem("showSurveyDialogAfterLogin") === "true"
        if (shouldShow) {
            setTimeout(() => {
                showSurveyDialog.value = true
            }, 500)
        }
    }

    onMounted(() => {
        // ensure UI state is synchronized with store
        onMenuCollapse(Boolean(layoutStore.sideMenuCollapsed))
        checkForSurveyDialog()
    })

    watch(
        () => layoutStore.sideMenuCollapsed,
        (val: boolean) => {
            onMenuCollapse(val)
        },
    )
</script>