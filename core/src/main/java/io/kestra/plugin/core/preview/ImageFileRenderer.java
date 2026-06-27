package io.kestra.plugin.core.preview;

import io.kestra.core.preview.FilePreview;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Set;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Image file renderer",
    description = "Preview image files inside the Kestra UI, supported extensions: png, jpg, jpeg, svg, gif, bmp, webp."
)
public class ImageFileRenderer extends AbstractBase64FileRenderer {
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("png", "jpg", "jpeg", "svg", "gif", "bmp", "webp");

    @Override
    public boolean supports(String extension) {
        return SUPPORTED_EXTENSIONS.contains(extension.toLowerCase());
    }

    @Override
    protected FilePreview.Type getPreviewType() {
        return FilePreview.Type.IMAGE;
    }
}
