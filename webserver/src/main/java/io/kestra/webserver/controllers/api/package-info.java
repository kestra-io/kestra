/**
 * Contains all the api controllers, services, that provide the Kestra's API.
 * All {@link io.micronaut.http.annotation.Controller} in this package MUST have the base URI: `/api` or `/api/v1/`.
 */
@Configuration
@WebServerEnabled
package io.kestra.webserver.controllers.api;

import io.kestra.webserver.annotation.WebServerEnabled;
import io.micronaut.context.annotation.Configuration;