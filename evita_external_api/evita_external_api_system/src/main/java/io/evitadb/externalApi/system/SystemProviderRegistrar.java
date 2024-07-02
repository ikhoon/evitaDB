/*
 *
 *                         _ _        ____  ____
 *               _____   _(_) |_ __ _|  _ \| __ )
 *              / _ \ \ / / | __/ _` | | | |  _ \
 *             |  __/\ V /| | || (_| | |_| | |_) |
 *              \___| \_/ |_|\__\__,_|____/|____/
 *
 *   Copyright (c) 2023-2024
 *
 *   Licensed under the Business Source License, Version 1.1 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   https://github.com/FgForrest/evitaDB/blob/master/LICENSE
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package io.evitadb.externalApi.system;

import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpResponseBuilder;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.server.file.HttpFile;
import com.linecorp.armeria.common.HttpRequest;
import io.evitadb.api.requestResponse.system.SystemStatus;
import io.evitadb.core.Evita;
import io.evitadb.exception.GenericEvitaInternalError;
import io.evitadb.externalApi.api.system.ProbesProvider;
import io.evitadb.externalApi.api.system.ProbesProvider.Readiness;
import io.evitadb.externalApi.api.system.ProbesProvider.ReadinessState;
import io.evitadb.externalApi.api.system.model.HealthProblem;
import io.evitadb.externalApi.configuration.ApiOptions;
import io.evitadb.externalApi.configuration.CertificatePath;
import io.evitadb.externalApi.configuration.CertificateSettings;
import io.evitadb.externalApi.http.CorsFilterServiceDecorator;
import io.evitadb.externalApi.http.ExternalApiProvider;
import io.evitadb.externalApi.http.ExternalApiProviderRegistrar;
import io.evitadb.externalApi.http.ExternalApiServer;
import io.evitadb.externalApi.system.configuration.SystemConfig;
import io.evitadb.externalApi.utils.path.PathHandlingService;
import io.evitadb.utils.Assert;
import io.evitadb.utils.CertificateUtils;
import io.evitadb.utils.StringUtils;

import javax.annotation.Nonnull;
import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Registers System API provider to provide system API to clients.
 *
 * @author Tomáš Pozler, 2023
 */
public class SystemProviderRegistrar implements ExternalApiProviderRegistrar<SystemConfig> {
	private static final String ENDPOINT_SERVER_NAME = "server-name";
	private static final String ENDPOINT_SYSTEM_STATUS = "status";
	private static final String ENDPOINT_SYSTEM_LIVENESS = "liveness";
	private static final String ENDPOINT_SYSTEM_READINESS = "readiness";

	/**
	 * Prints the status of the APIs as a JSON string.
	 *
	 * @param builder   http response builder
	 * @param readiness the readiness of the APIs
	 */
	private static HttpResponse printApiStatus(
		@Nonnull HttpResponseBuilder builder,
		@Nonnull Readiness readiness
	) {
		return builder.content(MediaType.JSON, "{\n" +
			"\t\"status\": \"" + readiness.state().name() + "\",\n" +
			"\t\"apis\": {\n" +
			Arrays.stream(readiness.apiStates())
				.map(entry -> "\t\t\"" + entry.apiCode() + "\": \"" + (entry.isReady() ? "ready" : "not ready") + "\"")
				.collect(Collectors.joining(",\n")) + "\n" +
			"\t}\n" +
			"}").build();
	}

	/**
	 * Returns the enabled API endpoints.
	 *
	 * @param apiOptions the common settings shared among all the API endpoints
	 * @return array of codes of the enabled API endpoints
	 */
	@Nonnull
	private static String[] getEnabledApiEndpoints(@Nonnull ApiOptions apiOptions) {
		return apiOptions.endpoints()
			.entrySet()
			.stream()
			.filter(entry -> entry.getValue() != null)
			.filter(entry -> entry.getValue().isEnabled())
			.map(Entry::getKey)
			.toArray(String[]::new);
	}

	/**
	 * Renders the unavailable response (should not happen).
	 *
	 * @param exchange the HTTP server exchange
	 */
	private static HttpResponse renderUnavailable(@Nonnull ServiceRequestContext ctx, @Nonnull HttpRequest req) {
		return HttpResponse.of(HttpStatus.SERVICE_UNAVAILABLE, MediaType.JSON, "{\"status\": \"" + ReadinessState.SHUTDOWN.name() + "\"}");
	}

	/**
	 * Renders the status of the evitaDB server as a JSON string.
	 *
	 * @param evita             the evitaDB server
	 * @param externalApiServer the external API server
	 * @param exchange          the HTTP server exchange
	 * @param apiOptions        the common settings shared among all the API endpoints
	 * @param probes            the probes providers
	 * @param enabledEndPoints  the enabled API endpoints
	 */
	private static void renderStatus(
		@Nonnull Evita evita,
		@Nonnull ExternalApiServer externalApiServer,
		@Nonnull HttpServerExchange exchange,
		@Nonnull ApiOptions apiOptions,
		@Nonnull List<ProbesProvider> probes,
		@Nonnull String[] enabledEndPoints) {
		if (evita.isActive()) {
			exchange.setStatusCode(StatusCodes.OK);
			exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
			final Set<HealthProblem> healthProblems = probes
				.stream()
				.flatMap(it -> it.getHealthProblems(evita, externalApiServer, enabledEndPoints).stream())
				.collect(Collectors.toSet());
			final SystemStatus systemStatus = evita.getSystemStatus();
			exchange.getResponseSender().send(
				String.format("""
						{
						   "serverName": "%s",
						   "version": "%s",
						   "startedAt": "%s",
						   "uptime": %d,
						   "uptimeForHuman": "%s",
						   "catalogsCorrupted": %d,
						   "catalogsOk": %d,
						   "healthProblems": [%s],
						   "apis": [
						%s
						   ]
						}""",
					evita.getConfiguration().name(),
					systemStatus.version(),
					DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(systemStatus.startedAt()),
					systemStatus.uptime().toSeconds(),
					StringUtils.formatDuration(systemStatus.uptime()),
					systemStatus.catalogsCorrupted(),
					systemStatus.catalogsOk(),
					healthProblems.stream()
						.sorted()
						.map(it -> "\"" + it.name() + "\"")
						.collect(Collectors.joining(", ")),
					apiOptions.endpoints()
						.entrySet()
						.stream()
						.filter(entry -> Arrays.stream(enabledEndPoints).anyMatch(it -> it.equals(entry.getKey())))
						.map(
							entry -> "      {\n         \"" + entry.getKey() + "\": " +
								"[\n" + Arrays.stream(entry.getValue().getBaseUrls(apiOptions.exposedOn()))
								.map(it -> "            \"" + it + "\"")
								.collect(Collectors.joining(",\n")) +
								"\n         ]" +
								"\n      }"
						)
						.collect(Collectors.joining(",\n"))
				)
			);
		} else {
			renderUnavailable(exchange);
		}
	}

	/**
	 * Renders the readiness response.
	 *
	 * @param evita             the evitaDB server
	 * @param externalApiServer the external API server
	 * @param exchange          the HTTP server exchange
	 * @param probes            the probes providers
	 * @param enabledEndPoints  the enabled API endpoints
	 */
	private static HttpResponse renderReadinessResponse(
		@Nonnull Evita evita,
		@Nonnull ExternalApiServer externalApiServer,
		@Nonnull HttpRequest request,
		@Nonnull List<ProbesProvider> probes,
		@Nonnull String[] enabledEndPoints
	) {
		exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
		if (evita.isActive()) {
			final Optional<Readiness> readiness = probes
				.stream()
				.map(it -> it.getReadiness(evita, externalApiServer, enabledEndPoints))
				.findFirst();

			if (readiness.map(it -> it.state() == ReadinessState.READY).orElse(false)) {
				exchange.setStatusCode(StatusCodes.OK);
				printApiStatus(exchange, readiness.get());
			} else if (readiness.isPresent()) {
				exchange.setStatusCode(StatusCodes.SERVICE_UNAVAILABLE);
				printApiStatus(exchange, readiness.get());
			} else {
				exchange.setStatusCode(StatusCodes.SERVICE_UNAVAILABLE);
				exchange.getResponseSender().send("{\"status\": \"" + ReadinessState.UNKNOWN.name() + "\"}");
			}
		} else {
			renderUnavailable(exchange);
		}
	}

	/**
	 * Renders the liveness response.
	 *
	 * @param evita             the evitaDB server
	 * @param externalApiServer the external API server
	 * @param exchange          the HTTP server exchange
	 * @param probes            the probes providers
	 * @param enabledEndPoints  the enabled API endpoints
	 */
	private static void renderLivenessResponse(
		@Nonnull Evita evita,
		@Nonnull ExternalApiServer externalApiServer,
		@Nonnull HttpServerExchange exchange,
		@Nonnull List<ProbesProvider> probes,
		@Nonnull String[] enabledEndPoints
	) {
		if (evita.isActive()) {
			final Set<HealthProblem> healthProblems = probes
				.stream()
				.flatMap(it -> it.getHealthProblems(evita, externalApiServer, enabledEndPoints).stream())
				.collect(Collectors.toSet());

			if (healthProblems.isEmpty()) {
				return HttpResponse.of(HttpStatus.OK, MediaType.JSON, "{\"status\": \"healthy\"}");
			} else {
				return HttpResponse.of(
					HttpStatus.SERVICE_UNAVAILABLE,
					MediaType.JSON,
					"{\"status\": \"unhealthy\", \"problems\": [" +
						healthProblems.stream()
							.sorted()
							.map(it -> "\"" + it.name() + "\"")
							.collect(Collectors.joining(", ")) +
						"]}"
				);
			}
		} else {
			renderUnavailable(exchange);
		}
	}

	@Nonnull
	@Override
	public String getExternalApiCode() {
		return SystemProvider.CODE;
	}

	@Nonnull
	@Override
	public Class<SystemConfig> getConfigurationClass() {
		return SystemConfig.class;
	}

	@Nonnull
	@Override
	public ExternalApiProvider<SystemConfig> register(
		@Nonnull Evita evita,
		@Nonnull ExternalApiServer externalApiServer,
		@Nonnull ApiOptions apiOptions,
		@Nonnull SystemConfig systemConfig
	) {
		final PathHandlingService router = new PathHandlingService();
		router.addExactPath(
			"/" + ENDPOINT_SERVER_NAME,
			(ctx, req) -> HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT, evita.getConfiguration().name())
		);

		router.addExactPath(
			"/" + ENDPOINT_SYSTEM_STATUS,
			(ctx, req) -> HttpResponse.of(
				HttpStatus.OK,
				MediaType.JSON,
				renderStatus(
					evita.getConfiguration().name(),
					evita.getSystemStatus(),
					apiOptions
				)
			)
		);

		final String[] enabledEndPoints = getEnabledApiEndpoints(apiOptions);
		final List<ProbesProvider> probes = ServiceLoader.load(ProbesProvider.class)
			.stream()
			.map(Provider::get)
			.toList();

		router.addExactPath(
			"/" + ENDPOINT_SYSTEM_STATUS,
			exchange -> renderStatus(
				evita,
				externalApiServer,
				exchange,
				apiOptions,
				probes,
				enabledEndPoints
			)
		);

		router.addExactPath(
			"/" + ENDPOINT_SYSTEM_LIVENESS,
			(ctx, req) -> renderLivenessResponse(evita, externalApiServer, req, probes, enabledEndPoints)
		);

		router.addExactPath(
			"/" + ENDPOINT_SYSTEM_READINESS,
			exchange -> renderReadinessResponse(evita, externalApiServer, req, probes, enabledEndPoints)
		);

		final String fileName;
		final CertificateSettings certificateSettings = apiOptions.certificate();
		final boolean atLeastOnEndpointRequiresTls = apiOptions.endpoints()
			.values()
			.stream()
			.anyMatch(it -> it.isEnabled() && it.isTlsEnabled());
		final boolean atLeastOnEndpointRequiresMtls = apiOptions.endpoints()
			.values()
			.stream()
			.anyMatch(it -> {
				if (it.isMtlsEnabled()) {
					Assert.isPremiseValid(
						it.isTlsEnabled(), "mTLS cannot be enabled without enabled TLS!"
					);
					return true;
				} else {
					return false;
				}
			});

		if (atLeastOnEndpointRequiresTls) {
			final File file;
			if (certificateSettings.generateAndUseSelfSigned()) {
				file = apiOptions.certificate()
					.getFolderPath()
					.toFile();
				fileName = CertificateUtils.getGeneratedRootCaCertificateFileName();
			} else {
				final CertificatePath certificatePath = certificateSettings.custom();
				if (certificatePath == null || certificatePath.certificate() == null || certificatePath.privateKey() == null) {
					throw new GenericEvitaInternalError(
						"Certificate path is not properly set in the configuration file and `generateAndUseSelfSigned` is set to false."
					);
				}
				final String certificate = certificatePath.certificate();
				final int lastSeparatorIndex = certificatePath.certificate().lastIndexOf(File.separator);
				file = new File(certificate.substring(0, lastSeparatorIndex));
				fileName = certificate.substring(lastSeparatorIndex);

				router.addExactPath("/" + fileName, (ctx, req) -> {
					ctx.addAdditionalResponseHeader(HttpHeaderNames.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");
					return HttpFile.of(new File(file, fileName)).asService().serve(ctx, req);
				});

				router.addExactPath("/" + CertificateUtils.getGeneratedServerCertificateFileName(), (ctx, req) -> {
					ctx.addAdditionalResponseHeader(HttpHeaderNames.CONTENT_DISPOSITION, "attachment; filename=\"" + CertificateUtils.getGeneratedServerCertificateFileName() + "\"");
					return HttpFile.of(new File(file, CertificateUtils.getGeneratedServerCertificateFileName())).asService().serve(ctx, req);
				});

				router.addExactPath("/" + CertificateUtils.getGeneratedClientCertificateFileName(), (ctx, req) -> {
					ctx.addAdditionalResponseHeader(HttpHeaderNames.CONTENT_DISPOSITION, "attachment; filename=\"" + CertificateUtils.getGeneratedClientCertificateFileName() + "\"");
					return HttpFile.of(new File(file, CertificateUtils.getGeneratedClientCertificateFileName())).asService().serve(ctx, req);
				});

				router.addExactPath("/" + CertificateUtils.getGeneratedClientCertificatePrivateKeyFileName(), (ctx, req) -> {
					ctx.addAdditionalResponseHeader(HttpHeaderNames.CONTENT_DISPOSITION, "attachment; filename=\"" + CertificateUtils.getGeneratedClientCertificatePrivateKeyFileName() + "\"");
					return HttpFile.of(new File(file, CertificateUtils.getGeneratedClientCertificatePrivateKeyFileName())).asService().serve(ctx, req);
				});
			}

			final ResourceHandler fileSystemHandler;
			try (ResourceManager resourceManager = new FileResourceManager(file, 100)) {
				fileSystemHandler = new ResourceHandler(
					(exchange, path) -> {
						if (("/" + fileName).equals(path)) {
							return resourceManager.getResource(fileName);
						} else if (("/" + CertificateUtils.getGeneratedServerCertificateFileName()).equals(path) && certificateSettings.generateAndUseSelfSigned()) {
							return resourceManager.getResource(CertificateUtils.getGeneratedServerCertificateFileName());
						} else if (("/" + CertificateUtils.getGeneratedClientCertificateFileName()).equals(path) && certificateSettings.generateAndUseSelfSigned()) {
							return resourceManager.getResource(CertificateUtils.getGeneratedClientCertificateFileName());
						} else if (("/" + CertificateUtils.getGeneratedClientCertificatePrivateKeyFileName()).equals(path) && certificateSettings.generateAndUseSelfSigned()) {
							return resourceManager.getResource(CertificateUtils.getGeneratedClientCertificatePrivateKeyFileName());
						} else {
							return null;
						}
					}
				);
				router.addPrefixPath("/", fileSystemHandler);

				return new SystemProvider(
					systemConfig,
					router.decorate(
						new CorsFilterServiceDecorator(systemConfig.getAllowedOrigins()).createDecorator()
					),
					Arrays.stream(systemConfig.getBaseUrls(apiOptions.exposedOn()))
						.map(it -> it + ENDPOINT_SERVER_NAME)
						.toArray(String[]::new),
					fileName == null ?
						new String[0] : Arrays.stream(systemConfig.getBaseUrls(apiOptions.exposedOn()))
						.map(it -> it + fileName)
						.toArray(String[]::new),
					certificateSettings.generateAndUseSelfSigned() && atLeastOnEndpointRequiresTls ?
						Arrays.stream(systemConfig.getBaseUrls(apiOptions.exposedOn()))
							.map(it -> it + CertificateUtils.getGeneratedServerCertificateFileName())
							.toArray(String[]::new) :
						new String[0],
					certificateSettings.generateAndUseSelfSigned() && atLeastOnEndpointRequiresMtls ?
						Arrays.stream(systemConfig.getBaseUrls(apiOptions.exposedOn()))
							.map(it -> it + CertificateUtils.getGeneratedClientCertificateFileName())
							.toArray(String[]::new) :
						new String[0],
					certificateSettings.generateAndUseSelfSigned() && atLeastOnEndpointRequiresMtls ?
						Arrays.stream(systemConfig.getBaseUrls(apiOptions.exposedOn()))
							.map(it -> it + CertificateUtils.getGeneratedClientCertificatePrivateKeyFileName())
							.toArray(String[]::new) :
						new String[0]
				);
			} catch (IOException e) {
				throw new GenericEvitaInternalError(e.getMessage(), e);
			}
		}
	}
}
