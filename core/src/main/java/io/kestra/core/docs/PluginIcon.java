package io.kestra.core.docs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class PluginIcon {
    String name;
    String icon;
    Boolean flowable;
    Boolean monochrome;
    /**
     * Content hash of {@link #icon}, null when there is no icon. Lets callers cache-bust a
     * long-lived, immutable URL for the icon bytes (see PluginController's {@code icon.svg}
     * endpoint) — the URL only needs to change when this hash does.
     */
    String hash;
}
