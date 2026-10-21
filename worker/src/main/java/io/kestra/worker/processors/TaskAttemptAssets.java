package io.kestra.worker.processors;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.assets.Asset;
import io.kestra.core.models.assets.AssetIdentifier;
import io.kestra.core.models.assets.AssetsDeclaration;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunWithOutput;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.AssetEmit;
import io.kestra.core.runners.AssetEmitter;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunVariables;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.serializers.JacksonMapper;

import jakarta.validation.ConstraintViolationException;

import static io.kestra.core.models.flows.State.Type.RUNNING;

/**
 * The assets of one attempt of a task run: those it declares, written when it starts and again when it ends, and those it emits while running.
 */
final class TaskAttemptAssets {
    private static final Set<String> ASSET_IDENTITY_KEYS = Set.of("id", "type", "namespace");

    private final RunContext runContext;
    private final WorkerTask workerTask;
    private final Instant startDate;
    private final Set<String> used = new HashSet<>();
    private final List<String> failures = new ArrayList<>();
    private TaskRunWithOutput.AssetEmission emission = TaskRunWithOutput.AssetEmission.OK;

    TaskAttemptAssets(RunContext runContext, WorkerTask workerTask, Instant startDate) {
        this.runContext = runContext;
        this.workerTask = workerTask;
        this.startDate = startDate;
    }

    // rendered before the task runs, so a property needing its outputs is skipped, and an asset whose identity needs them is left out until the end
    @SuppressWarnings({"unchecked", "rawtypes"})
    void emitAtStart() {
        AssetsDeclaration assetsDeclaration = workerTask.getTask().getAssets();
        if (assetsDeclaration == null || !(runContext instanceof DefaultRunContext defaultRunContext)) {
            return;
        }

        Logger logger = runContext.logger();
        try {
            List<String> skipped = new ArrayList<>();
            List<AssetIdentifier> renderedInputs = new ArrayList<>();
            Optional<List<Map>> rawInputs = Property.asStaticList((Property) assetsDeclaration.getInputs(), Map.class);
            if (rawInputs.isPresent()) {
                for (Map<String, Object> rawInput : rawInputs.get()) {
                    try {
                        Map<String, Object> renderedInput = new LinkedHashMap<>();
                        for (Map.Entry<String, Object> entry : rawInput.entrySet()) {
                            renderedInput.put(entry.getKey(), renderValue(entry.getValue()));
                        }
                        renderedInputs.add(JacksonMapper.ofJson().convertValue(renderedInput, AssetIdentifier.class));
                    } catch (IllegalVariableEvaluationException | IllegalArgumentException e) {
                        skipped.add(String.valueOf(rawInput.get("id")));
                    }
                }
            } else if (assetsDeclaration.getInputs() != null) {
                try {
                    renderedInputs.addAll(runContext.render(assetsDeclaration.getInputs().skipCache()).asList(AssetIdentifier.class));
                } catch (IllegalVariableEvaluationException e) {
                    skipped.add("inputs");
                }
            }

            List<AssetIdentifier> inputs = new ArrayList<>();
            for (AssetIdentifier input : renderedInputs) {
                try {
                    runContext.validate(input);
                    inputs.add(input);
                } catch (ConstraintViolationException e) {
                    // validated again when the task ends, which fails it
                }
            }

            List<Asset> rendered = new ArrayList<>();
            Optional<List<Map>> rawOutputs = Property.asStaticList((Property) assetsDeclaration.getOutputs(), Map.class);
            if (rawOutputs.isPresent()) {
                for (Map<String, Object> rawOutput : rawOutputs.get()) {
                    try {
                        rendered.add(renderStartAsset(rawOutput, skipped));
                    } catch (IllegalVariableEvaluationException | IllegalArgumentException e) {
                        skipped.add(String.valueOf(rawOutput.get("id")));
                    }
                }
            } else if (assetsDeclaration.getOutputs() != null) {
                try {
                    rendered.addAll(runContext.render(assetsDeclaration.getOutputs().skipCache()).asList(Asset.class));
                } catch (IllegalVariableEvaluationException e) {
                    skipped.add("outputs");
                }
            }

            List<Asset> outputs = new ArrayList<>();
            for (Asset asset : rendered) {
                try {
                    runContext.validate(asset);
                    // the declared status describes the asset once the task succeeded
                    if (asset.getMetadata() != null) {
                        asset.getMetadata().remove(Asset.STATUS_METADATA_KEY);
                    }
                    outputs.add(asset);
                } catch (ConstraintViolationException e) {
                    skipped.add(asset.getId());
                }
            }

            if (!skipped.isEmpty()) {
                logger.warn("Some declared assets reference values that do not exist when the task starts, they are only written when it ends: {}", String.join(", ", skipped));
            }

            if (inputs.isEmpty() && outputs.isEmpty()) {
                return;
            }
            AssetEmitter declared = defaultRunContext.assets(new DefaultRunContext.Declared(RUNNING, startDate, null, Set.of()));
            declared.emit(new AssetEmit(withDefaultNamespace(inputs, workerTask.getTaskRun().getNamespace()), outputs));
            inputs.forEach(input -> used.add(input.id()));
            outputs.forEach(output -> used.add(output.getId()));
            failures.addAll(declared.failures());
        } catch (Exception e) {
            logger.warn("Unable to emit the assets at the start of the task", e);
            emission = TaskRunWithOutput.AssetEmission.FAILED;
        }
    }

    /**
     * @param taskRun the task run with the attempt that just ended, whose outputs the declared assets may reference
     */
    TaskRunWithOutput.AssetEmission emitAtEnd(TaskRun taskRun, State.Type state, Map<String, Object> outputs) {
        Logger logger = runContext.logger();
        try {
            if (workerTask.getTask().getAssets() != null && runContext instanceof DefaultRunContext defaultRunContext) {
                // We need to have the task outputs injected before rendering the assets
                Map<String, Object> formattedOutputsMap = RunVariables.executionFormattedOutputMap(taskRun, outputs);

                AssetsDeclaration assetsDeclaration = workerTask.getTask().getAssets();

                // Rendered independently so an unrenderable output doesn't discard the inputs of the same task.
                List<AssetIdentifier> declaredInputs = List.of();
                try {
                    declaredInputs = runContext.render(assetsDeclaration.getInputs()).asList(AssetIdentifier.class, formattedOutputsMap);
                } catch (IllegalVariableEvaluationException e) {
                    logger.warn("Unable to render the declared asset inputs of task '{}'", taskRun.getTaskId(), e);
                    emission = TaskRunWithOutput.AssetEmission.FAILED;
                }

                List<Asset> declaredOutputs = List.of();
                try {
                    declaredOutputs = runContext.render(assetsDeclaration.getOutputs()).asList(Asset.class, formattedOutputsMap);
                } catch (IllegalVariableEvaluationException e) {
                    logger.warn("Unable to render the declared asset outputs of task '{}'", taskRun.getTaskId(), e);
                    emission = TaskRunWithOutput.AssetEmission.FAILED;
                }

                if (!declaredInputs.isEmpty() || !declaredOutputs.isEmpty()) {
                    AssetEmitter declared = defaultRunContext.assets(new DefaultRunContext.Declared(state, startDate, Instant.now(), used));
                    declared.emit(new AssetEmit(withDefaultNamespace(declaredInputs, taskRun.getNamespace()), declaredOutputs));
                    failures.addAll(declared.failures());
                }
            }
            if (runContext instanceof DefaultRunContext defaultRunContext) {
                defaultRunContext.assetsIfCreated().ifPresent(emitter -> failures.addAll(emitter.failures()));
            }
        } catch (ConstraintViolationException e) {
            // validate() re-checks the whole task bean, so only an assets-scoped violation is the declaration's fault
            String assetViolations = violationsUnder("assets", e);
            if (assetViolations.isEmpty()) {
                logger.warn("Unable to render asset declaration for taskRun '{}'", taskRun, e);
                emission = TaskRunWithOutput.AssetEmission.FAILED;
            } else {
                logger.error("Invalid asset declaration for taskRun '{}': {}", taskRun.getId(), assetViolations);
                emission = TaskRunWithOutput.AssetEmission.DECLARATION_INVALID;
            }
        } catch (Exception e) {
            logger.warn("Unable to emit the assets of task '{}'", taskRun.getTaskId(), e);
            emission = TaskRunWithOutput.AssetEmission.FAILED;
        }

        if (!failures.isEmpty()) {
            logger.warn("Some assets of task '{}' were not written: {}", taskRun.getTaskId(), String.join(", ", failures));
            if (TaskRunWithOutput.AssetEmission.OK == emission) {
                emission = TaskRunWithOutput.AssetEmission.FAILED;
            }
        }

        return emission;
    }

    @SuppressWarnings("unchecked")
    private Asset renderStartAsset(Map<String, Object> rawOutput, List<String> skipped) throws IllegalVariableEvaluationException {
        String assetId = String.valueOf(rawOutput.get("id"));
        Map<String, Object> rendered = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rawOutput.entrySet()) {
            if ("metadata".equals(entry.getKey()) && entry.getValue() instanceof Map<?, ?> metadata) {
                Map<String, Object> renderedMetadata = new LinkedHashMap<>();
                for (Map.Entry<String, Object> metadataEntry : ((Map<String, Object>) metadata).entrySet()) {
                    try {
                        renderedMetadata.put(metadataEntry.getKey(), renderValue(metadataEntry.getValue()));
                    } catch (IllegalVariableEvaluationException e) {
                        skipped.add(assetId + ".metadata." + metadataEntry.getKey());
                    }
                }
                rendered.put(entry.getKey(), renderedMetadata);
            } else if (ASSET_IDENTITY_KEYS.contains(entry.getKey())) {
                rendered.put(entry.getKey(), renderValue(entry.getValue()));
            } else {
                try {
                    rendered.put(entry.getKey(), renderValue(entry.getValue()));
                } catch (IllegalVariableEvaluationException e) {
                    skipped.add(assetId + "." + entry.getKey());
                }
            }
        }
        return JacksonMapper.ofJson().convertValue(rendered, Asset.class);
    }

    private Object renderValue(Object value) throws IllegalVariableEvaluationException {
        if (value == null) {
            return null;
        }
        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put("value", value);
        return runContext.render(wrapper).get("value");
    }

    private static String violationsUnder(String propertyPathPrefix, ConstraintViolationException e) {
        return e.getConstraintViolations().stream()
            .filter(violation -> violation.getPropertyPath().toString().startsWith(propertyPathPrefix))
            .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
            .collect(Collectors.joining(", "));
    }

    /**
     * A declared asset input without a namespace belongs to the flow referencing it, and left null it is
     * filtered out of the view of every user whose asset permission is scoped to namespaces rather than
     * global. Outputs are untouched here: an omitted output namespace must reach {@link Asset#toUpdated}
     * as {@code null} so it keeps the stored asset's namespace instead of being silently rewritten to the
     * flow's own.
     */
    private static List<AssetIdentifier> withDefaultNamespace(List<AssetIdentifier> inputs, String namespace) {
        return inputs.stream()
            .map(input -> input.namespace() == null ? input.withNamespace(namespace) : input)
            .toList();
    }
}
