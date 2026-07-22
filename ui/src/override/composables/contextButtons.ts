import type {Component} from "vue"
import {computed} from "vue"
import {useRoute} from "vue-router"

import {useI18n} from "vue-i18n"

import {useNetwork, useStorage} from "@vueuse/core"
const {isOnline} = useNetwork()

import ContextNews from "../../components/layout/ContextNews.vue"
import ContextDocs from "../../components/docs/ContextDocs.vue"
import CopilotChat from "../../components/ai/copilot/CopilotChat.vue"
import AiIcon from "../../components/ai/AiIcon.vue"
import {useApiStore} from "../../stores/api"

import MessageOutline from "vue-material-design-icons/MessageOutline.vue"
import FileDocument from "vue-material-design-icons/FileDocument.vue"
import Slack from "vue-material-design-icons/Slack.vue"
import Github from "vue-material-design-icons/Github.vue"
import Calendar from "vue-material-design-icons/Calendar.vue"
import Star from "vue-material-design-icons/Star.vue"

export interface Button {
    title: string;
    icon?: Component;

    component?: Component;
    hasUnreadMarker?: boolean;
    unread?: {readonly value: boolean};
    hidden?: boolean;
    /** Opened programmatically as a stripless panel (never a tab), e.g. the notifications bell. */
    panelOnly?: boolean;

    url?: string;
}

export function useContextButtons() {
    const {t} = useI18n({useScope: "global"})
    const route = useRoute()

    const apiStore = useApiStore()
    const lastNewsReadDate = useStorage<string | null>("feeds", null)
    const newsUnread = computed<boolean>(() => {
        const feeds = apiStore.feeds
        return Boolean(
            lastNewsReadDate.value === null ||
            (feeds?.[0] && (new Date(lastNewsReadDate.value) < new Date(feeds[0].publicationDate))),
        )
    })

    const buttons: Record<string, Button> = isOnline.value
        ? {
              ai: {
                  title: t("contextBar.ai"),
                  icon: AiIcon,

                  component: CopilotChat,
                  // The full-page AI Copilot (`/ai`) is the same agent — hide the redundant dock tab there.
                  get hidden() {
                      return route.name === "ai"
                  },
              },
              news: {
                  title: t("contextBar.news"),
                  icon: MessageOutline,

                  component: ContextNews,
                  hasUnreadMarker: true,
                  unread: newsUnread,
              },
              docs: {
                  title: t("contextBar.docs"),
                  icon: FileDocument,

                  component: ContextDocs,
                  hasUnreadMarker: false,
              },
              help: {
                  title: t("contextBar.help"),
                  icon: Slack,

                  url: "https://kestra.io/slack?utm_source=app&utm_medium=referral&utm_campaign=context-bar",
              },
              issue: {
                  title: t("contextBar.issue"),
                  icon: Github,

                  url: "https://github.com/kestra-io/kestra/issues/new/choose",
              },
              demo: {
                  title: t("contextBar.demo"),
                  icon: Calendar,

                  url: "https://kestra.io/demo",
              },
              star: {
                  title: t("contextBar.star"),
                  icon: Star,

                  url: "https://github.com/kestra-io/kestra?utm_source=app&utm_medium=referral&utm_campaign=context-bar",
              },
          }
        : {}

    return {buttons}
}
