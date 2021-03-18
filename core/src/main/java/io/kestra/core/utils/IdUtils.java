package io.kestra.core.utils;

import com.devskiller.friendly_id.FriendlyId;

abstract public class IdUtils {
    public static String create() {
        return FriendlyId.createFriendlyId();
    }
}
