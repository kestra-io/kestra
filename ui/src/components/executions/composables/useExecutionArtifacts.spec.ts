import {describe, expect, it, vi} from "vitest"
import {ref} from "vue"

const executionsStore = vi.hoisted(() => ({flow: null as unknown}))
const federated = vi.hoisted(() => ({
    resolveRemoteComponent: vi.fn(),
    taskAdditionalInfoRemote: {value: {} as Record<string, {title?: string}>},
}))

vi.mock("../../../stores/executions", () => ({useExecutionsStore: () => executionsStore}))
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

const SHELL = "io.kestra.plugin.scripts.shell.Commands"
const RUNNER = "io.kestra.plugin.ee.aws.runner.Batch"

describe("useExecutionArtifacts", () => {
    it("shouldListArtifactsOfTheTaskRunnerAndOfTheTask", () => {
        // Given
        executionsStore.flow = {tasks: [{id: "shell", type: SHELL, taskRunner: {type: RUNNER}}]}
        federated.taskAdditionalInfoRemote.value = {
            [`${SHELL}/logs`]: {},
            [`${RUNNER}/details`]: {title: "Runner details"},
        }

        // When
        const {artifacts} = useExecutionArtifacts()

        // Then
        expect(artifacts.value).toEqual([
            {key: `shell:${SHELL}/logs`, taskId: "shell", taskType: `${SHELL}/logs`, cls: SHELL, title: "logs", description: undefined},
            {key: `shell:${RUNNER}/details`, taskId: "shell", taskType: `${RUNNER}/details`, cls: SHELL, title: "Runner details", description: undefined},
        ])
    })

    it("shouldResolveTheTaskRunnerTypeAlongsideTheTaskType", () => {
        // Given
        federated.resolveRemoteComponent.mockClear()
        executionsStore.flow = {tasks: [{id: "shell", type: SHELL, taskRunner: {type: RUNNER}}]}

        // When
        useExecutionArtifacts()

        // Then
        expect(federated.resolveRemoteComponent).toHaveBeenCalledWith([RUNNER, SHELL].map((cls) => ({cls, version: undefined})))
    })

    it("shouldNotListTheRunnerArtifactsOfAnotherTask", () => {
        // Given
        executionsStore.flow = {tasks: [{id: "plain", type: SHELL}]}
        federated.taskAdditionalInfoRemote.value = {[`${RUNNER}/details`]: {}}

        // When
        const {artifacts} = useExecutionArtifacts()

        // Then
        expect(artifacts.value).toEqual([])
    })
})
