import {describe, expect, it, vi} from "vitest"
import {ref} from "vue"
import type {FlowForExecution} from "@kestra-io/kestra-sdk"

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

    it("shouldLoadTheSourceOfTheGivenFlowRatherThanOfTheExecutionOnScreen", async () => {
        // Given
        executionsStore.flow = null
        executionsStore.execution = {id: "other", flowId: "other", namespace: "elsewhere", flowRevision: 1}
        flowStore.loadFlow.mockResolvedValue({source: sourceOf(RUNNER)})
        federated.taskAdditionalInfoRemote.value = {[`${RUNNER}/details`]: {}}

        // When
        const {artifacts} = useExecutionArtifacts(() => ({...strippedFlow, revision: 3}) as unknown as FlowForExecution)
        await flush()

        // Then
        expect(flowStore.loadFlow).toHaveBeenLastCalledWith({namespace: "ns", id: "f", revision: "3", store: false, optional: true})
        expect(artifacts.value).toHaveLength(1)
    })
})
