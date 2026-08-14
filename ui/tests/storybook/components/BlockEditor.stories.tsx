import type {Meta, StoryObj} from "@storybook/vue3-vite"
import BlockEditor from "../../../src/components/no-code/blocks/BlockEditor.vue"
import {useFlowStore} from "../../../src/stores/flow"

const EMPTY_YAML = "id: my_flow\nnamespace: company.team"

const SIMPLE_YAML = `id: my_flow
namespace: company.team
tasks:
  - id: log_task
    type: io.kestra.plugin.core.log.Log
    message: Hello World
  - id: http_request
    type: io.kestra.plugin.core.http.Request
    uri: https://example.com
  - id: script_task
    type: io.kestra.plugin.core.runner.Script
    script: echo "done"
`

const YAML_WITH_IF = `id: my_flow
namespace: company.team
tasks:
  - id: start_log
    type: io.kestra.plugin.core.log.Log
    message: Starting
  - id: branch_if
    type: io.kestra.plugin.core.flow.If
    condition: "{{ true }}"
    then:
      - id: then_log
        type: io.kestra.plugin.core.log.Log
        message: In then branch
    else:
      - id: else_log
        type: io.kestra.plugin.core.log.Log
        message: In else branch
    errors:
      - id: err_handler
        type: io.kestra.plugin.core.log.Log
        message: Error occurred
  - id: end_log
    type: io.kestra.plugin.core.log.Log
    message: Done
`

const YAML_WITH_SWITCH = `id: my_flow
namespace: company.team
tasks:
  - id: env_switch
    type: io.kestra.plugin.core.flow.Switch
    value: "{{ inputs.env }}"
    cases:
      prod:
        - id: prod_log
          type: io.kestra.plugin.core.log.Log
          message: Production
        - id: prod_notify
          type: io.kestra.plugin.core.log.Log
          message: Notify prod
      staging:
        - id: staging_log
          type: io.kestra.plugin.core.log.Log
          message: Staging
      dev:
        - id: dev_log
          type: io.kestra.plugin.core.log.Log
          message: Dev
    defaults:
      - id: default_log
        type: io.kestra.plugin.core.log.Log
        message: Default
`

const YAML_WITH_PARALLEL = `id: my_flow
namespace: company.team
tasks:
  - id: parallel_block
    type: io.kestra.plugin.core.flow.Parallel
    tasks:
      - id: parallel_a
        type: io.kestra.plugin.core.log.Log
        message: Parallel A
      - id: parallel_b
        type: io.kestra.plugin.core.log.Log
        message: Parallel B
      - id: parallel_c
        type: io.kestra.plugin.core.log.Log
        message: Parallel C
`

const YAML_DEEPLY_NESTED = `id: my_flow
namespace: company.team
tasks:
  - id: outer_if
    type: io.kestra.plugin.core.flow.If
    condition: "{{ inputs.check }}"
    then:
      - id: inner_parallel
        type: io.kestra.plugin.core.flow.Parallel
        tasks:
          - id: deep_task_a
            type: io.kestra.plugin.core.log.Log
            message: Deep A
          - id: deep_if
            type: io.kestra.plugin.core.flow.If
            condition: "{{ true }}"
            then:
              - id: deepest_task
                type: io.kestra.plugin.core.log.Log
                message: Deepest
    else:
      - id: fallback
        type: io.kestra.plugin.core.log.Log
        message: Fallback
`

const YAML_WITH_TRIGGERS = `id: my_flow
namespace: company.team
tasks:
  - id: notify
    type: io.kestra.plugin.core.log.Log
    message: Triggered
triggers:
  - id: on_schedule
    type: io.kestra.plugin.core.trigger.Schedule
    cron: "0 9 * * *"
  - id: on_webhook
    type: io.kestra.plugin.core.trigger.Webhook
    key: my-key
`

const YAML_WITH_FLOW_LEVEL_ERRORS = `id: my_flow
namespace: company.team
tasks:
  - id: main_task
    type: io.kestra.plugin.core.log.Log
    message: Main
errors:
  - id: on_error
    type: io.kestra.plugin.core.log.Log
    message: Flow error handler
finally:
  - id: cleanup
    type: io.kestra.plugin.core.log.Log
    message: Cleanup
`

const meta: Meta<typeof BlockEditor> = {
    title: "Components/BlockEditor",
    component: BlockEditor,
    parameters: {
        layout: "fullscreen",
    },
}

export default meta
type Story = StoryObj<typeof BlockEditor>

const makeRender = (yaml: string): Story["render"] => () => ({
    setup() {
        const flowStore = useFlowStore()
        flowStore.flowYaml = yaml
        return () => (
            <div style="height: 600px; border: 1px solid var(--ks-border-default); border-radius: var(--ks-radius-base); overflow: hidden;">
                <BlockEditor />
            </div>
        )
    },
})

const YAML_DAG_INVALID_SUBTASK = `id: my_flow
namespace: company.team
tasks:
  - id: my_dag
    type: io.kestra.plugin.core.flow.Dag
    tasks:
      - task:
          id: a
          type: io.kestra.plugin.core.log.Log
          message: a
      - task:
          id: b
          type: io.kestra.plugin.core.log.Log
          message: b
        dependsOn:
          - a
      - task:
          id: log
          type: io.kestra.plugin.core.log.Log
`

export const Empty: Story = {
    render: makeRender(EMPTY_YAML),
    parameters: {
        docs: {description: {story: "Empty flow — no tasks or triggers yet."}},
    },
}

export const DagWithInvalidSubtask: Story = {
    render: () => ({
        setup() {
            const flowStore = useFlowStore()
            flowStore.flowYaml = YAML_DAG_INVALID_SUBTASK
            flowStore.flowValidation = {constraints: "Validation error: log.log.task.message: must not be null\n"}
            return () => (
                <div style="height: 600px; border: 1px solid var(--ks-border-default); border-radius: var(--ks-radius-base); overflow: hidden;">
                    <BlockEditor />
                </div>
            )
        },
    }),
    parameters: {
        docs: {description: {story: "A DAG whose inner 'log' sub-task is missing its required message. The nested sub-task card surfaces the validation helper (red marker), grouped from the flow's constraints by task id — proving the helper reaches tasks inside a flowable, not just the flowable itself."}},
    },
}

export const WithTasks: Story = {
    render: makeRender(SIMPLE_YAML),
    parameters: {
        docs: {description: {story: "Flow with three flat leaf tasks."}},
    },
}

export const WithIfFlowable: Story = {
    render: makeRender(YAML_WITH_IF),
    parameters: {
        docs: {description: {story: "Flow with an If task showing Then / Else / Errors lanes. The flowable renders as an expandable cluster card."}},
    },
}

export const WithSwitchFlowable: Story = {
    render: makeRender(YAML_WITH_SWITCH),
    parameters: {
        docs: {description: {story: "Flow with a Switch task. Each case key renders as a named lane. Default renders as its own lane. Use the '+ Add case' UI to create new branches."}},
    },
}

export const WithParallelFlowable: Story = {
    render: makeRender(YAML_WITH_PARALLEL),
    parameters: {
        docs: {description: {story: "Flow with a Parallel task showing a single tasks lane."}},
    },
}

export const DeeplyNested: Story = {
    render: makeRender(YAML_DEEPLY_NESTED),
    parameters: {
        docs: {description: {story: "Three levels of nesting: If > Parallel > If. Tests the depth cap (depth pill appears at level 4) and recursive lane rendering."}},
    },
}

export const WithTasksAndTriggers: Story = {
    render: makeRender(YAML_WITH_TRIGGERS),
    parameters: {
        docs: {description: {story: "Flow with both tasks and triggers."}},
    },
}

export const WithFlowLevelErrors: Story = {
    render: makeRender(YAML_WITH_FLOW_LEVEL_ERRORS),
    parameters: {
        docs: {description: {story: "Flow with top-level errors and finally lanes rendered below the tasks section."}},
    },
}
