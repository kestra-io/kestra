import {ref, computed} from "vue"

interface SurveySkipData {
    skipped: boolean
    skippedAt: string
    step_number: number
    survey_action: string
    dialogShown?: boolean
    [key: string]: any
}

export function useSurveySkip() {
    const SURVEY_SKIP_KEY = "basicAuthSurveySkipped"
    
    const surveySkipData = ref<SurveySkipData | null>(null)

    const loadSurveySkipData = (): SurveySkipData | null => {
        try {
            const stored = localStorage.getItem(SURVEY_SKIP_KEY)
            if (stored) {
                const data = JSON.parse(stored)
                surveySkipData.value = data
                return data
            }
        } catch (error) {
            console.warn("Failed to load survey skip data:", error)
        }
        return null
    }

    const storeSurveySkipData = (data: Partial<SurveySkipData>): void => {
        try {
            const skipData: SurveySkipData = {
                skipped: true,
                skippedAt: new Date().toISOString(),
                step_number: 3,
                survey_action: "skipped",
                dialogShown: false,
                ...data
            }
            localStorage.setItem(SURVEY_SKIP_KEY, JSON.stringify(skipData))
            surveySkipData.value = skipData
        } catch (error) {
            console.error("Failed to store survey skip data:", error)
        }
    }

    const shouldShowHelloDialog = (): boolean => {
        loadSurveySkipData()
        const skipData = surveySkipData.value
        
        if (!skipData?.skipped) return false
        
        // Check if 30 days have passed.
        const skippedDate = new Date(skipData.skippedAt)
        const currentDate = new Date()
        const daysDifference = Math.floor((currentDate.getTime() - skippedDate.getTime()) / (1000 * 60 * 60 * 24))
        
        // Show dialog if 30 days have passed and dialog hasn't been shown yet
        return daysDifference >= 30 && !skipData.dialogShown
    }

    const markSurveyDialogShown = (): void => {
        if (surveySkipData.value) {
            const updatedData = {
                ...surveySkipData.value,
                dialogShown: true
            }
            localStorage.setItem(SURVEY_SKIP_KEY, JSON.stringify(updatedData))
            surveySkipData.value = updatedData
        }
    }

    const resetSurveyForNewCycle = (): void => {
        try {
            const resetData: SurveySkipData = {
                skipped: true,
                skippedAt: new Date().toISOString(),
                step_number: 3,
                survey_action: "skipped_again",
                dialogShown: false
            }
            localStorage.setItem(SURVEY_SKIP_KEY, JSON.stringify(resetData))
            surveySkipData.value = resetData
        } catch (error) {
            console.error("Failed to reset survey for new cycle:", error)
        }
    }

    const clearSurveySkipData = (): void => {
        try {
            localStorage.removeItem(SURVEY_SKIP_KEY)
            surveySkipData.value = null
        } catch (error) {
            console.error("Failed to clear survey skip data:", error)
        }
    }

    const isSurveySkipped = computed(() => {
        return surveySkipData.value?.skipped === true
    })

    const surveySkipStatus = computed(() => {
        return isSurveySkipped.value ? "skipped" : "not_skipped"
    })

    const shouldShowDialog = computed(() => {
        if (!surveySkipData.value?.skipped) return false
        
        const skippedDate = new Date(surveySkipData.value.skippedAt)
        const currentDate = new Date()
        const daysDifference = Math.floor((currentDate.getTime() - skippedDate.getTime()) / (1000 * 60 * 60 * 24))
        
        return daysDifference >= 30 && !surveySkipData.value?.dialogShown
    })

    const getSurveySkipDetails = computed(() => {
        return surveySkipData.value || null
    })

    loadSurveySkipData()

    return {
        surveySkipData,
        loadSurveySkipData,
        storeSurveySkipData,
        clearSurveySkipData,
        shouldShowHelloDialog,
        markSurveyDialogShown,
        resetSurveyForNewCycle,
        isSurveySkipped,
        surveySkipStatus,
        shouldShowDialog,
        getSurveySkipDetails
    }
}
