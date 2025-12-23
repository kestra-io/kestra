package io.kestra.core.runners.test;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.assets.Asset;
import io.kestra.core.models.tasks.*;
import io.kestra.core.runners.RunContext;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Plugin
public class AssetEmitter extends Task implements RunnableTask<VoidOutput> {
    @NotNull
    @PluginProperty
    private Asset assetToEmit;


    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        runContext.assets().upsert(assetToEmit);
        return null;
    }
}
