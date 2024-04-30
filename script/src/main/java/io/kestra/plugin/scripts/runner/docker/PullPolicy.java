package io.kestra.plugin.scripts.runner.docker;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    title = "The image pull policy for a container image and the tag of the image, which affect when Docker attempts to pull (download) the specified image."
)
public enum PullPolicy {
    IF_NOT_PRESENT,
    ALWAYS,
    NEVER
}
