package io.kestra.core.utils;

import java.util.ArrayList;
import java.util.List;

public class ListUtils {
    public static <T> List<T> emptyOnNull(List<T> list) {
        return list == null ? new ArrayList<>() : list;
    }

    public static <T> boolean isEmpty(List<T> list) {
        return list == null || list.isEmpty();
    }

    /**
     * Concat two lists into a single list.
     * If list1 is null, list2 is returned.
     * If list2 is null, list1 is returned.
     */
    public static <T> List<T> concat(List<T> list1, List<T> list2) {
        if (list1 == null) {
            return list2;
        }

        if (list2 == null) {
            return list1;
        }

        List<T> newList = new ArrayList<>(list1.size() + list2.size());
        newList.addAll(list1);
        newList.addAll(list2);
        return newList;
    }

    /**
     * Concat multiple lists into a single list.
     */
    @SafeVarargs
    public static <T> List<T> concat(List<T> list1, List<T> list2, List<T>... lists) {
        List<T> newList = new ArrayList<>();
        if (!isEmpty(list1)) {
            newList.addAll(list1);
        }
        if (!isEmpty(list2)) {
            newList.addAll(list2);
        }

        if (lists != null) {
            for (List<T> list : lists) {
                if (!isEmpty(list)) {
                    newList.addAll(list);
                }
            }
        }

        return newList;
    }
}
