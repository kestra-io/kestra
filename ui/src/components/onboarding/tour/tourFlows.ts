// Flow sources used by the product tour. Hardcoded rather than generated: every scene after the
// first refers to known task ids, a known broken expression and a known webhook key.

import {TUTORIAL_NAMESPACE} from "../../../utils/constants"

export const TOUR_NAMESPACE = TUTORIAL_NAMESPACE
export const TOUR_FLOW_ID = "order_summary"
export const TOUR_REPORT_FLOW_ID = "daily_report"
export const TOUR_WEBHOOK_TRIGGER_ID = "new_order"
export const TOUR_WEBHOOK_TRIGGER_TYPE = "io.kestra.plugin.core.trigger.Webhook"

// Held in a flow variable rather than inline: `url` is a secret property, so a plain value there
// raises a validation warning in the editor.
export const TOUR_SLACK_VARIABLE = "slack_webhook"
export const TOUR_SLACK_MOCK_URL = "https://kestra.io/api/mock"

const TOUR_FLOW_HEADER = `id: ${TOUR_FLOW_ID}
namespace: ${TOUR_NAMESPACE}`

const TOUR_BASE_TASKS = `

tasks:
  - id: fetch_orders
    type: io.kestra.plugin.core.http.Download
    uri: https://dummyjson.com/carts

  - id: summarize
    type: io.kestra.plugin.scripts.python.Script
    dependencies:
      - kestra
    script: |
      import json
      from kestra import Kestra

      data = json.load(open('{{ outputs.fetch_orders.uri }}'))
      revenue = round(sum(cart['total'] for cart in data['carts']), 2)

      print(f"Revenue today: \${revenue:,}")
      Kestra.outputs({'revenue': revenue})`

export const TOUR_FLOW_BASE = TOUR_FLOW_HEADER + TOUR_BASE_TASKS

export const TOUR_NOTIFY_TASK_TYPE = "io.kestra.plugin.slack.notifications.SlackIncomingWebhook"

export const TOUR_SLACK_VARIABLES = `

variables:
  # A mock endpoint for this tour. In production: {{ secret('SLACK_WEBHOOK') }}
  ${TOUR_SLACK_VARIABLE}: ${TOUR_SLACK_MOCK_URL}`

// `revenu` is missing its final `e` on purpose: rendering the message fails so the notify task fails
// while the two tasks before it stay successful. Do not "fix" it here.
export const TOUR_NOTIFY_TASK_BROKEN = `

  - id: notify
    type: ${TOUR_NOTIFY_TASK_TYPE}
    url: "{{ vars.${TOUR_SLACK_VARIABLE} }}"
    messageText: "Revenue today: \${{ outputs.summarize.vars.revenu }}"`

export const TOUR_NOTIFY_TASK_FIXED = TOUR_NOTIFY_TASK_BROKEN.replace(
    "vars.revenu }}",
    "vars.revenue }}",
)

// The line the revision diff scrolls to.
export const TOUR_FIXED_EXPRESSION = "vars.revenue }}"

// Keyed per instance so two users never share a webhook URL.
export const tourWebhookTrigger = (key: string) => `

triggers:
  - id: ${TOUR_WEBHOOK_TRIGGER_ID}
    type: ${TOUR_WEBHOOK_TRIGGER_TYPE}
    key: ${key}
    labels:
      started_by: webhook event`

export const TOUR_REPORT_FLOW = `id: ${TOUR_REPORT_FLOW_ID}
namespace: ${TOUR_NAMESPACE}

tasks:
  - id: build_report
    type: io.kestra.plugin.core.log.Log
    message: "Building the report after {{ trigger.flowId }} execution {{ trigger.executionId }}"

triggers:
  - id: after_orders
    type: io.kestra.plugin.core.trigger.Flow
    labels:
      started_by: flow trigger
    dependsOn:
      - namespace: ${TOUR_NAMESPACE}
        flowId: ${TOUR_FLOW_ID}
        states:
          - SUCCESS`

export const tourFlowSource = {
    generated: () => TOUR_FLOW_BASE,
    withBrokenNotify: () =>
        TOUR_FLOW_HEADER + TOUR_SLACK_VARIABLES + TOUR_BASE_TASKS + TOUR_NOTIFY_TASK_BROKEN,
    withFixedNotify: () =>
        TOUR_FLOW_HEADER + TOUR_SLACK_VARIABLES + TOUR_BASE_TASKS + TOUR_NOTIFY_TASK_FIXED,
    withWebhook: (key: string) =>
        TOUR_FLOW_HEADER + TOUR_SLACK_VARIABLES + TOUR_BASE_TASKS + TOUR_NOTIFY_TASK_FIXED
        + tourWebhookTrigger(key),
}

export const TOUR_MANUAL_LABEL = "started_by:you"

export {SAMPLE_TEST_EVENT_PAYLOAD as TOUR_TEST_EVENT_PAYLOAD} from "../../flows/testEvent"
