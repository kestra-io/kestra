package org.floworc.core.repositories;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
public class ArrayListTotal<T> extends ArrayList<T> {
    private long total;

    public ArrayListTotal(List<T> list, long total) {
        super(list);
        this.total = total;
    }
}
