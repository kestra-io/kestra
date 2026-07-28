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
    String hash;

    public PluginIcon(String name, String icon, Boolean flowable) {
        this(name, icon, flowable, false, null);
    }
}
