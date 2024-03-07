/*
 * Copyright 2017-2024 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.kestra.webserver.cookies;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.DefaultServerCookieDecoder;
import io.micronaut.http.cookie.ServerCookieDecoder;

import java.util.List;

/**
 * Workaround for <a href="https://github.com/micronaut-projects/micronaut-core/issues/10435">multi-cookies header parsing issue</a>
 */
@Internal
public final class ServerMultipleCookiesDecoder implements ServerCookieDecoder {

    public static final DefaultServerCookieDecoder DEFAULT_SERVER_COOKIE_DECODER = new DefaultServerCookieDecoder();

    @Override
    @NonNull
    public List<Cookie> decode(@NonNull String header) {
        String cookie2Header = header;
        if (!header.toLowerCase().startsWith("set-cookie2:")) {
            cookie2Header = "Set-Cookie2:" + header.replace(";", ",");
        }

        return DEFAULT_SERVER_COOKIE_DECODER.decode(cookie2Header);
    }
}
