<template>
    <el-dialog
        v-model="isVisible"
        :title="$t('setup.titles.survey')"
        width="550px"
        :showClose="true"
        :closeOnClickModal="false"
        :closeOnPressEscape="true"
        @close="handleClose"
        customClass="hello-survey-dialog"
    >
        <div class="survey-content">
            <h3>{{ $t('setup.subtitles.survey') }}</h3>
            
            <div class="question-section">
                <h4>{{ $t('setup.survey.company_size') }}</h4>
                <div class="company-size-options">
                    <el-radio-group v-model="companySize">
                        <el-radio 
                            v-for="option in companySizeOptions" 
                            :key="option.value" 
                            :value="option.value"
                        >
                            {{ $t(option.labelKey) }}
                        </el-radio>
                    </el-radio-group>
                </div>
            </div>

            <el-divider />
            
            <div class="question-section">
                <h4>{{ $t('setup.survey.use_case') }}</h4>
                <div class="use-case-options">
                    <el-checkbox-group v-model="useCases">
                        <el-checkbox 
                            v-for="option in useCaseOptions" 
                            :key="option.value" 
                            :value="option.value"
                        >
                            {{ $t(option.labelKey) }}
                        </el-checkbox>
                    </el-checkbox-group>
                </div>
            </div>

            <el-divider />

            
            <div class="newsletter-section">
                <el-checkbox v-model="subscribeNewsletter">
                    <span v-html="$t('setup.survey.newsletter')" />
                </el-checkbox>
            </div>
        </div>
        
        <template #footer>
            <div class="dialog-footer">
                <el-button @click="handleSkip">
                    {{ $t('setup.survey.skip') }}
                </el-button>
                <el-button type="primary" @click="handleSubmit">
                    {{ $t('setup.survey.continue') }}
                </el-button>
            </div>
        </template>
    </el-dialog>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue"
    import {useApiStore} from "../stores/api"
    import {useMiscStore} from "override/stores/misc"

    interface Props {
        visible?: boolean
    }

    const props = withDefaults(defineProps<Props>(), {
        visible: false
    })

    const emit = defineEmits<{
        close: []
        skip: []
        submit: [data: {
            companySize: string
            useCases: string[]
            subscribeNewsletter: boolean
        }]
    }>()

    const apiStore = useApiStore()
    const miscStore = useMiscStore()

    const companySize = ref("")
    const useCases = ref<string[]>([])
    const subscribeNewsletter = ref(false)

    const companySizeOptions = [
        {value: "1-10", labelKey: "setup.survey.company_1_10"},
        {value: "11-50", labelKey: "setup.survey.company_11_50"},
        {value: "50-250", labelKey: "setup.survey.company_50_250"},
        {value: "250+", labelKey: "setup.survey.company_250_plus"},
        {value: "personal", labelKey: "setup.survey.company_personal"}
    ]

    const useCaseOptions = [
        {value: "infrastructure", labelKey: "setup.survey.use_case_infrastructure"},
        {value: "business", labelKey: "setup.survey.use_case_business"},
        {value: "data", labelKey: "setup.survey.use_case_data"},
        {value: "ml", labelKey: "setup.survey.use_case_ml"},
        {value: "other", labelKey: "setup.survey.use_case_other"}
    ]

    const isVisible = computed({
        get: () => props.visible,
        set: (value: boolean) => {
            if (!value) emit("close")
        }
    })

    const handleClose = () => {
        emit("close")
    }

    const handleSkip = () => {
        trackSurveyEvent("survey_skipped", {
            company_size: companySize.value || undefined,
            use_cases: useCases.value.length > 0 ? useCases.value : undefined,
            newsletter_subscribed: subscribeNewsletter.value
        })
        emit("skip")
        emit("close")
    }

    const handleSubmit = () => {
        const surveyData = {
            companySize: companySize.value,
            useCases: useCases.value,
            subscribeNewsletter: subscribeNewsletter.value
        }
        
        trackSurveyEvent("survey_submitted", {
            company_size: surveyData.companySize,
            use_cases: surveyData.useCases,
            newsletter_subscribed: surveyData.subscribeNewsletter
        })
        
        emit("submit", surveyData)
        emit("close")
    }

    const trackSurveyEvent = (eventName: string, additionalData: Record<string, any> = {}) => {
        const configs = miscStore.configs
        
        apiStore.posthogEvents({
            type: eventName,
            instance_id: configs?.uuid,
            survey_context: "standalone_dialog",
            ...additionalData
        })
    }
</script>

<style scoped lang="scss">
:deep(.hello-survey-dialog) {
    border-radius: 8px;
    border: 1px solid var(--ks-dialog-border, #404559);
    
    .el-dialog {
        border-radius: 8px;
    }
    
    .el-dialog__header {
        background-color: var(--ks-background-card, #2c2f36);
        border-bottom: 1px solid var(--ks-border-primary, #404559);
        padding: 20px 24px;
        
        .el-dialog__title {
            color: var(--ks-content-primary, #ffffff);
            font-size: 18px;
            font-weight: 600;
        }
    }
    
    .el-dialog__body {
        padding: 24px;
        background-color: var(--ks-background-card, #2c2f36);
    }
    
    .el-dialog__footer {
        background-color: var(--ks-background-card, #2c2f36);
        border-top: 1px solid var(--ks-border-primary, #404559);
        padding: 20px 24px;
    }
}

.survey-content {
    padding: 1rem;
    h3 {
        color: var(--ks-content-primary, #ffffff);
        font-size: 18.4px;
        font-weight: 600;
        margin: 0 0 24px 0;
        line-height: 1.4;
    }
    
    .question-section {
        margin-bottom: 32px;
        
        h4 {
            color: var(--ks-content-primary, #ffffff);
            font-size: 16px;
            font-weight: 700;
            margin: 0 0 16px 0;
        }
    }
    
    .company-size-options {
        :deep(.el-radio-group) {
            display: flex;
            flex-wrap: wrap;
            gap: 16px;
            
            .el-radio {
                margin-right: 0;
                margin-bottom: 0;
                
                .el-radio__input {
                    .el-radio__inner {
                        background-color: transparent;
                        border-color: #918BA9;
                        border-width: 2px;
                        width: 24px;
                        height: 24px;
                        
                        &::after {
                            display: none;
                        }
                    }
                    
                    &.is-checked .el-radio__inner {
                        background-color: transparent;
                        border-color: #8405FF;
                        border-width: 2px;
                        
                        &::after {
                            display: block;
                            content: '';
                            background-color: #8405FF;
                            width: 12px;
                            height: 12px;
                            border-radius: 50%;
                            position: absolute;
                            left: 50%;
                            top: 50%;
                            transform: translate(-50%, -50%);
                        }
                    }
                }
                
                .el-radio__label {
                    color: var(--ks-content-primary, #ffffff);
                    padding-left: 8px;
                    font-size: 14px;
                }
            }
        }
    }
    
    .use-case-options {
        :deep(.el-checkbox-group) {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 12px 24px;
            
            .el-checkbox {
                margin-right: 0;
                margin-bottom: 0;
                
                .el-checkbox__input {
                    .el-checkbox__inner {
                        background-color: transparent;
                        border-color: #918BA9;
                        width: 18px;
                        height: 18px;
                        border-radius: 2px;
                        
                        &::after {
                            border-color: #ffffff;
                            width: 6px;
                            height: 9px;
                            left: 4px;
                            top: 1px;
                        }
                    }
                    
                    &.is-checked .el-checkbox__inner {
                        background-color: var(--el-color-primary, #7c3aed);
                        border-color: var(--el-color-primary, #7c3aed);
                    }
                }
                
                .el-checkbox__label {
                    color: var(--ks-content-primary, #ffffff);
                    padding-left: 10px;
                    font-size: 14px;
                    line-height: 22px;
                }
            }
        }
    }
    
    .newsletter-section {
        padding-top: 4px;
        
        :deep(.el-checkbox) {
            .el-checkbox__input {
                .el-checkbox__inner {
                    background-color: transparent;
                    border-color: #918BA9;
                    width: 18px;
                    height: 18px;
                    border-radius: 2px;
                    
                    &::after {
                        border-color: #ffffff;
                        width: 6px;
                        height: 9px;
                        left: 4px;
                        top: 1px;
                    }
                }
                
                &.is-checked .el-checkbox__inner {
                    background-color: var(--el-color-primary, #7c3aed);
                    border-color: var(--el-color-primary, #7c3aed);
                }
            }
            
            .el-checkbox__label {
                color: var(--ks-content-secondary, #9ca3af);
                font-size: 14px;
                line-height: 22px;
                padding-left: 10px;
            }
        }
    }
}

.dialog-footer {
    padding: 1rem;
    display: flex;
    gap: 12px;
    justify-content: flex-end;
}
</style>
