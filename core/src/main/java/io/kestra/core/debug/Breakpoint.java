package io.kestra.core.debug;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public class Breakpoint {
    @NotNull
    private String id;

    @Nullable
    private String value;

    public static Breakpoint of(String breakpoint) {
        if (breakpoint.indexOf('.') > 0) {
            return new Breakpoint(breakpoint.substring(0, breakpoint.indexOf('.')), breakpoint.substring(breakpoint.indexOf('.') + 1));
        } else {
            return new Breakpoint(breakpoint, null);
        }
    }
}
