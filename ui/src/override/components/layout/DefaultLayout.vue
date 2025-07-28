<template>
    <LeftMenu v-if="miscStore.configs" @menu-collapse="onMenuCollapse" />
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

<script setup>
    import LeftMenu from "override/components/LeftMenu.vue"
    import Errors from "../../../components/errors/Errors.vue"
    import ContextInfoBar from "../../../components/ContextInfoBar.vue"
    import SurveyDialog from "../../../components/SurveyDialog.vue"
    import {onMounted, ref} from "vue"
    import {useSurveySkip} from "../../../composables/useSurveyData"
    import {useCoreStore} from "../../../stores/core"
    import {useMiscStore} from "../../../stores/misc"

    const coreStore = useCoreStore()
    const miscStore = useMiscStore()
    const {markSurveyDialogShown} = useSurveySkip()
    const showSurveyDialog = ref(false)

    const onMenuCollapse = (collapse) => {
        const htmlElement = document.documentElement
        htmlElement.classList.toggle("menu-collapsed", collapse)
        htmlElement.classList.toggle("menu-not-collapsed", !collapse)
    }

    const handleSurveyDialogClose = () => {
        showSurveyDialog.value = false
        markSurveyDialogShown()
        localStorage.removeItem("showSurveyDialogAfterLogin")
    }

    const checkForSurveyDialog = () => {
        const shouldShow = localStorage.getItem("showSurveyDialogAfterLogin") === "true"
        if (shouldShow) {
            setTimeout(() => {
                showSurveyDialog.value = true
            }, 500)
        }
    }

    onMounted(() => {
        const isMenuCollapsed = localStorage.getItem("menuCollapsed") === "true"
        onMenuCollapse(isMenuCollapsed)
        checkForSurveyDialog()
    })
</script>