import {describe, expect, it, vi} from "vitest"
import {ref} from "vue"

const executionsStore = vi.hoisted(() => ({flow: null as unknown, execution: null as unknown}))
const flowStore = vi.hoisted(() => ({loadFlow: vi.fn()}))
const federated = vi.hoisted(() => ({
    resolveRemoteComponent: vi.fn(),
    taskAdditionalInfoRemote: {value: {} as Record<string, {title?: string}>},
}))

vi.mock("../../../stores/executions", () => ({useExecutionsStore: () => executionsStore}))
vi.mock("../../../stores/flow", () => ({useFlowStore: () => flowStore}))
vi.mock("../../../remoteComponents/useFederatedModule", () => ({
    useFederatedModule: () => ({
        RemoteComponent: undefined,
        taskAdditionalInfoRemote: federated.taskAdditionalInfoRemote,
        manifestReady: ref(true),
        hasResolvedComponent: () => true,
        hasFailedComponent: () => false,
        resolveRemoteComponent: federated.resolveRemoteComponent,
        subtitleResolvers: {},
    }),
}))

import {useExecutionArtifacts} from "./useExecutionArtifacts"

const flush = () => new Promise((resolve) => setTimeout(resolve))

const SHELL = "io.kestra.plugin.scripts.shell.Commands"
const RUNNER = "io.kestra.plugin.ee.aws.runner.Batch"

const sourceOf = (runner?: string) => `id: f
namespace: ns
tasks:
  - id: shell
    type: ${SHELL}
${runner ? `    taskRunner:\n      type: ${runner}\n` : ""}`

const strippedFlow = {id: "f", namespace: "ns", tasks: [{id: "shell", type: SHELL}]}
const execution = {id: "e", flowId: "f", namespace: "ns", flowRevision: 3}

const givenSource = (source: string) => {
    executionsStore.flow = strippedFlow
    executionsStore.execution = execution
    flowStore.loadFlow.mockResolvedValue({source})
}

describe("useExecutionArtifacts", () => {
    it("shouldListArtifactsOfTheTaskRunnerAndOfTheTask", async () => {
        // Given
        givenSource(sourceOf(RUNNER))
        federated.taskAdditionalInfoRemote.value = {
            [`${SHELL}/logs`]: {},
            [`${RUNNER}/details`]: {title: "Runner details"},
        }

        // When
        const {artifacts} = useExecutionArtifacts()
        await flush()

        // Then
        expect(artifacts.value).toEqual([
            {key: `shell:${SHELL}/logs`, taskId: "shell", taskType: `${SHELL}/logs`, cls: SHELL, title: "logs", description: undefined},
            {key: `shell:${RUNNER}/details`, taskId: "shell", taskType: `${RUNNER}/details`, cls: SHELL, title: "Runner details", description: undefined},
        ])
    })

    it("shouldResolveTheTaskRunnerTypeAlongsideTheTaskType", async () => {
        // Given
        federated.resolveRemoteComponent.mockClear()
        givenSource(sourceOf(RUNNER))

        // When
        useExecutionArtifacts()
        await flush()

        // Then
        expect(federated.resolveRemoteComponent).toHaveBeenCalledWith([RUNNER, SHELL].map((cls) => ({cls, version: undefined})))
    })

    it("shouldNotListTheRunnerArtifactsOfAnotherTask", async () => {
        // Given
        givenSource(sourceOf())
        federated.taskAdditionalInfoRemote.value = {[`${RUNNER}/details`]: {}}

        // When
        const {artifacts} = useExecutionArtifacts()
        await flush()

        // Then
        expect(artifacts.value).toEqual([])
    })

    it("shouldLoadTheExecutedRevisionWhenNoFlowIsInTheStore", async () => {
        // Given
        givenSource(sourceOf(RUNNER))
        federated.taskAdditionalInfoRemote.value = {[`${RUNNER}/details`]: {}}

        // When
        const {artifacts} = useExecutionArtifacts()
        await flush()

        // Then
        expect(flowStore.loadFlow).toHaveBeenCalledWith({namespace: "ns", id: "f", revision: "3", store: false, optional: true})
        expect(artifacts.value).toHaveLength(1)
    })

    it("shouldIncludeTasksAndArtifactsFromErrorsFinallyAndAfterExecutionSections", async () => {
        const ERROR_TASK = "io.kestra.plugin.core.log.Log"
        const FINALLY_TASK = "io.kestra.plugin.scripts.python.Script"
        const AFTER_TASK = "io.kestra.plugin.notifications.slack.SlackIncomingWebhook"

        const sourceWithSections = `id: f
namespace: ns
tasks:
  - id: shell
    type: ${SHELL}
errors:
  - id: err
    type: ${ERROR_TASK}
finally:
  - id: fin
    type: ${FINALLY_TASK}
afterExecution:
  - id: after
    type: ${AFTER_TASK}
`
        executionsStore.flow = {
            id: "f",
            namespace: "ns",
            tasks: [{id: "shell", type: SHELL}],
            errors: [{id: "err", type: ERROR_TASK}],
            finally: [{id: "fin", type: FINALLY_TASK}],
            afterExecution: [{id: "after", type: AFTER_TASK}],
        }
        executionsStore.execution = execution
        flowStore.loadFlow.mockResolvedValue({source: sourceWithSections})

        federated.taskAdditionalInfoRemote.value = {
            [`${SHELL}/logs`]: {title: "Shell logs"},
            [`${ERROR_TASK}/errorLogs`]: {title: "Error logs"},
            [`${FINALLY_TASK}/cleanup`]: {title: "Cleanup artifacts"},
            [`${AFTER_TASK}/summary`]: {title: "Summary report"},
        }

        const {artifacts, tasks} = useExecutionArtifacts()
        await flush()

        expect(tasks.value.map(t => t.id)).toEqual(["shell", "err", "fin", "after"])
        expect(artifacts.value).toEqual([
            {key: `shell:${SHELL}/logs`, taskId: "shell", taskType: `${SHELL}/logs`, cls: SHELL, title: "Shell logs", description: undefined},
            {key: `err:${ERROR_TASK}/errorLogs`, taskId: "err", taskType: `${ERROR_TASK}/errorLogs`, cls: ERROR_TASK, title: "Error logs", description: undefined},
            {key: `fin:${FINALLY_TASK}/cleanup`, taskId: "fin", taskType: `${FINALLY_TASK}/cleanup`, cls: FINALLY_TASK, title: "Cleanup artifacts", description: undefined},
            {key: `after:${AFTER_TASK}/summary`, taskId: "after", taskType: `${AFTER_TASK}/summary`, cls: AFTER_TASK, title: "Summary report", description: undefined},
        ])
    })
})
