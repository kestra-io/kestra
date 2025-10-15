import type {Component} from "vue";

import {useI18n} from "vue-i18n";

import {useNetwork} from "@vueuse/core";
const {isOnline} = useNetwork();

import ContextNews from "../../components/layout/ContextNews.vue";
import ContextDocs from "../../components/docs/ContextDocs.vue";

import MessageOutline from "vue-material-design-icons/MessageOutline.vue";
import FileDocument from "vue-material-design-icons/FileDocument.vue";
import Slack from "vue-material-design-icons/Slack.vue";
import Github from "vue-material-design-icons/Github.vue";
import Calendar from "vue-material-design-icons/Calendar.vue";
import Star from "vue-material-design-icons/Star.vue";

interface Button {
    title: string;
    icon: Component;

    component?: Component;
    hasUnreadMarker?: boolean;

    url?: string;
}

export function useContextButtons() {
    const {t} = useI18n({useScope: "global"});

    const buttons: Record<string, Button> = isOnline.value
        ? {
              news: {
                  title: t("contextBar.news"),
                  icon: MessageOutline,

                  component: ContextNews,
                  hasUnreadMarker: true,
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

                  url: "https://kestra.io/slack",
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

                  url: "https://github.com/kestra-io/kestra",
              },
          }
        : {};

    return {buttons};
}
