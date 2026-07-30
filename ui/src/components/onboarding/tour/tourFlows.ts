/**
 * Flow sources used by the product tour.
 *
 * The tour needs deterministic sources: every scene after the first one refers to known task ids,
 * to a known broken expression, and to a known webhook key, so the flows are defined here rather
 * than generated. They are only ever written to the `tutorial` namespace while the tour is running.
 */

import {TUTORIAL_NAMESPACE} from "../../../utils/constants"

export const TOUR_NAMESPACE = TUTORIAL_NAMESPACE
export const TOUR_FLOW_ID = "order_summary"
export const TOUR_REPORT_FLOW_ID = "daily_report"
export const TOUR_WEBHOOK_TRIGGER_ID = "new_order"
export const TOUR_WEBHOOK_TRIGGER_TYPE = "io.kestra.plugin.core.trigger.Webhook"

/**
 * Endpoint the notify task posts to, held in a flow variable.
 *
 * The task's `url` is a secret property, so a plain value there raises a validation warning in the
 * editor. A flow variable is a Pebble expression, so the warning is gone, the flow stays entirely
 * self-contained (nothing stored on the instance), and the line a user replaces in production is
 * obvious.
 */
export const TOUR_SLACK_VARIABLE = "slack_webhook"
export const TOUR_SLACK_MOCK_URL = "https://kestra.io/api/mock"

/**
 * Step 1: what the user gets back from the Copilot scene.
 *
 * Two tasks, and nothing else: the prompt asks for exactly this, and a variable for a task that is
 * only added later would be unused on the first screen.
 */
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

/**
 * Step 3: the task added by hand in the editor.
 *
 * `revenu` is missing its final `e` on purpose. Rendering the message fails, the task fails with a
 * message naming the missing variable, and the two tasks before it stay successful.
 */
export const TOUR_NOTIFY_TASK_TYPE = "io.kestra.plugin.slack.notifications.SlackIncomingWebhook"

/** Added with the notify task, which is what uses it. */
export const TOUR_SLACK_VARIABLES = `

variables:
  # A mock endpoint for this tour. In production: {{ secret('SLACK_WEBHOOK') }}
  ${TOUR_SLACK_VARIABLE}: ${TOUR_SLACK_MOCK_URL}`

export const TOUR_NOTIFY_TASK_BROKEN = `

  - id: notify
    type: ${TOUR_NOTIFY_TASK_TYPE}
    url: "{{ vars.${TOUR_SLACK_VARIABLE} }}"
    messageText: "Revenue today: \${{ outputs.summarize.vars.revenu }}"`

/** Step 4: the one-character fix. */
export const TOUR_NOTIFY_TASK_FIXED = TOUR_NOTIFY_TASK_BROKEN.replace(
    "vars.revenu }}",
    "vars.revenue }}",
)

/** The line the revision diff opens on, so the fix is not several screens below the fold. */
export const TOUR_FIXED_EXPRESSION = "vars.revenue }}"

/** Step 5: the webhook trigger, keyed per instance so two users never share a URL. */
export const tourWebhookTrigger = (key: string) => `

triggers:
  - id: ${TOUR_WEBHOOK_TRIGGER_ID}
    type: ${TOUR_WEBHOOK_TRIGGER_TYPE}
    key: ${key}
    labels:
      started_by: webhook event`

/** Step 5 (optional exit): the second flow, started by the first one finishing. */
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

/** Sources per tour stage, so a scene can render or restore the exact state it needs. */
export const tourFlowSource = {
    generated: () => TOUR_FLOW_BASE,
    // The variable goes above the tasks, where a flow would normally declare it.
    withBrokenNotify: () =>
        TOUR_FLOW_HEADER + TOUR_SLACK_VARIABLES + TOUR_BASE_TASKS + TOUR_NOTIFY_TASK_BROKEN,
    withFixedNotify: () =>
        TOUR_FLOW_HEADER + TOUR_SLACK_VARIABLES + TOUR_BASE_TASKS + TOUR_NOTIFY_TASK_FIXED,
    withWebhook: (key: string) =>
        TOUR_FLOW_HEADER + TOUR_SLACK_VARIABLES + TOUR_BASE_TASKS + TOUR_NOTIFY_TASK_FIXED
        + tourWebhookTrigger(key),
}

/** Label on executions the tour starts from the Execute button, next to the trigger ones. */
export const TOUR_MANUAL_LABEL = "started_by:you"

/** Payload prefilled in the Send test event dialog. */
export {SAMPLE_TEST_EVENT_PAYLOAD as TOUR_TEST_EVENT_PAYLOAD} from "../../flows/testEvent"
