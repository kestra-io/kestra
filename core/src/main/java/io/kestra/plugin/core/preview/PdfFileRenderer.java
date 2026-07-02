package io.kestra.plugin.core.preview;

import io.kestra.core.preview.FilePreview;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "PDF file renderer",
    description = "Preview PDF files inside the Kestra UI."
)
public class PdfFileRenderer extends AbstractBase64FileRenderer {
    @Override
    protected FilePreview.Type getPreviewType() {
        return FilePreview.Type.PDF;
    }

    @Override
    public boolean supports(String extension) {
        return "pdf".equalsIgnoreCase(extension);
    }
}
