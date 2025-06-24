<template>
    <left-menu v-if="configs" @menu-collapse="onMenuCollapse" />
    <main>
        <errors v-if="error" :code="error" />
        <slot v-else />
    </main>
    <context-info-bar v-if="configs" />
    
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
    import {useStore} from "vuex"
    import {computed, onMounted, ref} from "vue"
    import {useSurveySkip} from "../../../composables/useSurveyData"

    const store = useStore()
    const configs = computed(() => store.getters["misc/configs"])
    const error = computed(() => store.getters["core/error"])
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