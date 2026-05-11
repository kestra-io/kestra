<template>
    <KsDialog
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
            <div class="question-section">
                <h4>{{ $t('setup.survey.company_size') }}</h4>
                <div class="company-size-options">
                    <KsRadioGroup v-model="companySize">
                        <KsRadio
                            v-for="option in companySizeOptions"
                            :key="option.value"
                            :value="option.value"
                        >
                            {{ $t(option.labelKey) }}
                        </KsRadio>
                    </KsRadioGroup>
                </div>
            </div>

            <KsDivider />

            <div class="question-section">
                <h4>{{ $t('setup.survey.use_case') }}</h4>
                <div class="use-case-options">
                    <KsCheckboxGroup v-model="useCases">
                        <KsCheckbox
                            v-for="option in useCaseOptions"
                            :key="option.value"
                            :value="option.value"
                        >
                            {{ $t(option.labelKey) }}
                        </KsCheckbox>
                    </KsCheckboxGroup>
                </div>
            </div>

            <KsDivider />


            <div class="newsletter-section">
                <KsCheckbox v-model="subscribeNewsletter">
                    <span v-html="$t('setup.survey.newsletter')" />
                </KsCheckbox>
            </div>
        </div>

        <template #footer>
            <div class="dialog-footer">
                <KsButton @click="handleSkip">
                    {{ $t('setup.survey.skip') }}
                </KsButton>
                <KsButton type="primary" @click="handleSubmit">
                    {{ $t('setup.survey.continue') }}
                </KsButton>
            </div>
        </template>
    </KsDialog>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue"
    import {useApiStore} from "../stores/api"
    import {useMiscStore} from "override/stores/misc"

    interface Props {
        visible?: boolean
    }

    const props = withDefaults(defineProps<Props>(), {
        visible: false,
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
        {value: "personal", labelKey: "setup.survey.company_personal"},
    ]

    const useCaseOptions = [
        {value: "infrastructure", labelKey: "setup.survey.use_case_infrastructure"},
        {value: "business", labelKey: "setup.survey.use_case_business"},
        {value: "data", labelKey: "setup.survey.use_case_data"},
        {value: "ml", labelKey: "setup.survey.use_case_ml"},
        {value: "other", labelKey: "setup.survey.use_case_other"},
    ]

    const isVisible = computed({
        get: () => props.visible,
        set: (value: boolean) => {
            if (!value) emit("close")
        },
    })

    const handleClose = () => {
        emit("close")
    }

    const handleSkip = () => {
        trackSurveyEvent("survey_skipped", {
            company_size: companySize.value || undefined,
            use_cases: useCases.value.length > 0 ? useCases.value : undefined,
            newsletter_subscribed: subscribeNewsletter.value,
        })
        emit("skip")
        emit("close")
    }

    const handleSubmit = () => {
        const surveyData = {
            companySize: companySize.value,
            useCases: useCases.value,
            subscribeNewsletter: subscribeNewsletter.value,
        }

        trackSurveyEvent("survey_submitted", {
            company_size: surveyData.companySize,
            use_cases: surveyData.useCases,
            newsletter_subscribed: surveyData.subscribeNewsletter,
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
            ...additionalData,
        })
    }
</script>

<style scoped lang="scss">
:deep(.hello-survey-dialog) {
    border-radius: 8px;
    border: 1px solid var(--ks-dialog-border, #404559);

    .kel-dialog {
        border-radius: 8px;
    }

    .kel-dialog__header {
        background-color: var(--ks-background-card, #2c2f36);
        border-bottom: 1px solid var(--ks-border-primary, #404559);
        padding: 20px 24px;

        .kel-dialog__title {
            color: var(--ks-content-primary, #ffffff);
            font-size: var(--ks-font-size-md);
            font-weight: 600;
        }
    }

    .kel-dialog__body {
        padding: 24px;
        background-color: var(--ks-background-card, #2c2f36);
    }

    .kel-dialog__footer {
        background-color: var(--ks-background-card, #2c2f36);
        border-top: 1px solid var(--ks-border-primary, #404559);
        padding: 20px 24px;
    }
}

.survey-content {
    padding: 1rem;

    .question-section {
        margin-bottom: 32px;

        h4 {
            color: var(--ks-content-primary, #ffffff);
            font-size: var(--ks-font-size-base);
            font-weight: 700;
            margin: 0 0 16px 0;
        }
    }

    .company-size-options {
        :deep(.kel-radio-group) {
            display: flex;
            flex-wrap: wrap;
            gap: 16px;

            .kel-radio {
                margin-right: 0;
                margin-bottom: 0;

                .kel-radio__input {
                    .kel-radio__inner {
                        background-color: transparent;
                        border-color: #918BA9;
                        border-width: 2px;
                        width: 24px;
                        height: 24px;

                        &::after {
                            display: none;
                        }
                    }

                    &.is-checked .kel-radio__inner {
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

                .kel-radio__label {
                    color: var(--ks-content-primary, #ffffff);
                    padding-left: 8px;
                    font-size: var(--ks-font-size-sm);
                }
            }
        }
    }

    .use-case-options {
        :deep(.kel-checkbox-group) {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 12px 24px;

            .kel-checkbox {
                margin-right: 0;
                margin-bottom: 0;

                .kel-checkbox__input {
                    .kel-checkbox__inner {
                        background-color: transparent;
                        border-color: #918BA9;
                        width: 18px;
                        height: 18px;
                        border-radius: 2px;

                        &::after {
                            border-color: var(--ks-white);
                            width: 6px;
                            height: 9px;
                            left: 4px;
                            top: 1px;
                        }
                    }

                    &.is-checked .kel-checkbox__inner {
                        background-color: var(--ks-button-background-primary, #7c3aed);
                        border-color: var(--ks-button-background-primary, #7c3aed);
                    }
                }

                .kel-checkbox__label {
                    color: var(--ks-content-primary, #ffffff);
                    padding-left: 10px;
                    font-size: var(--ks-font-size-sm);
                    line-height: 22px;
                }
            }
        }
    }

    .newsletter-section {
        padding-top: 4px;

        :deep(.kel-checkbox) {
            .kel-checkbox__input {
                .kel-checkbox__inner {
                    background-color: transparent;
                    border-color: #918BA9;
                    width: 18px;
                    height: 18px;
                    border-radius: 2px;

                    &::after {
                        border-color: var(--ks-white);
                        width: 6px;
                        height: 9px;
                        left: 4px;
                        top: 1px;
                    }
                }

                &.is-checked .kel-checkbox__inner {
                    background-color: var(--ks-button-background-primary, #7c3aed);
                    border-color: var(--ks-button-background-primary, #7c3aed);
                }
            }

            .kel-checkbox__label {
                color: var(--ks-content-secondary, #9ca3af);
                font-size: var(--ks-font-size-sm);
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
