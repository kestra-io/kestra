import {computed} from "vue"
import ShapePlusOutline from "vue-material-design-icons/ShapePlusOutline.vue"
import Youtube from "vue-material-design-icons/Youtube.vue"
import Slack from "vue-material-design-icons/Slack.vue"
import CalendarMonth from "vue-material-design-icons/CalendarMonth.vue"

export function useOnboardingResources() {
    const items = computed(() => [
        {
            titleKey: "welcome_copilot.success_page.items.blueprints.title",
            descriptionKey: "welcome_copilot.success_page.items.blueprints.description",
            icon: ShapePlusOutline,
            iconClass: "is-blueprints",
            to: {name: "blueprints", params: {kind: "flow", tab: "community"}},
        },
        {
            titleKey: "welcome_copilot.success_page.items.slack.title",
            descriptionKey: "welcome_copilot.success_page.items.slack.description",
            icon: Slack,
            iconClass: "is-slack",
            href: "https://kestra.io/slack?utm_source=app&utm_medium=referral&utm_campaign=onboarding-welcome",
        },
        {
            titleKey: "welcome_copilot.success_page.items.videos.title",
            descriptionKey: "welcome_copilot.success_page.items.videos.description",
            icon: Youtube,
            iconClass: "is-videos",
            href: "https://kestra.io/tutorial-videos/all?utm_source=app&utm_medium=referral&utm_campaign=onboarding-welcome",
        },
        {
            titleKey: "welcome_copilot.success_page.items.demo.title",
            descriptionKey: "welcome_copilot.success_page.items.demo.description",
            icon: CalendarMonth,
            iconClass: "is-demo",
            href: "https://kestra.io/demo?utm_source=app&utm_medium=referral&utm_campaign=onboarding-welcome",
        },
    ])

    return {
        onboardingResources: items,
    }
}
