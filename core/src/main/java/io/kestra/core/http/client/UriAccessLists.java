package io.kestra.core.http.client;

import java.util.List;

public interface UriAccessLists {
    List<String> allowedList();

    List<String> deniedList();
}
