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
}
