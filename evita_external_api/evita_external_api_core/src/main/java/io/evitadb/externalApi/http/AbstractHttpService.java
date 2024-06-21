/*
 *
 *                         _ _        ____  ____
 *               _____   _(_) |_ __ _|  _ \| __ )
 *              / _ \ \ / / | __/ _` | | | |  _ \
 *             |  __/\ V /| | || (_| | |_| | |_) |
 *              \___| \_/ |_|\__\__,_|____/|____/
 *
 *   Copyright (c) 2024
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

package io.evitadb.externalApi.http;

import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpResponseWriter;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.ServiceRequestContext;
import io.evitadb.externalApi.exception.ExternalApiInternalError;
import io.evitadb.externalApi.exception.ExternalApiInvalidUsageException;
import io.evitadb.externalApi.exception.HttpExchangeException;
import io.evitadb.utils.Assert;
import io.netty.channel.EventLoop;
import io.undertow.util.StatusCodes;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public abstract class AbstractHttpService<E extends EndpointRequest> implements HttpService {
	private static final int STREAM_FRAMES_TO_READ = 8192;
	private static final String CONTENT_TYPE_CHARSET = "; charset=UTF-8";

	@Nonnull
	@Override
	public HttpResponse serve(@Nonnull ServiceRequestContext ctx, @Nonnull HttpRequest req) throws Exception {
		validateRequest(req);

		final E exchange = createEndpointExchange(
			req,
			req.method().toString(),
			getRequestBodyContentType(req).orElse(null),
			getPreferredResponseContentType(req).orElse(null)
		);

		beforeRequestHandled(exchange);
		final EndpointResponse response = doHandleRequest(exchange);
		afterRequestHandled(exchange, response);

		if (response instanceof NotFoundEndpointResponse) {
			throw new HttpExchangeException(StatusCodes.NOT_FOUND, "Requested resource wasn't found.");
		} else if (response instanceof SuccessEndpointResponse successResponse) {
			final Object result = successResponse.getResult();
			if (result == null) {
				return HttpResponse.builder()
					.status(StatusCodes.NO_CONTENT)
					.build();
			} else {
				final HttpResponseWriter responseWriter = HttpResponse.streaming();
				ctx.addAdditionalResponseHeader(HttpHeaderNames.CONTENT_TYPE, getPreferredResponseContentType(req) + CONTENT_TYPE_CHARSET);
				responseWriter.write(ResponseHeaders.of(HttpStatus.OK));
				writeResponse(exchange, responseWriter, result);
				if (responseWriter.isOpen()) {
					responseWriter.close();
				}
				return responseWriter;
			}
		} else {
			throw createInternalError("Unsupported response `" + response.getClass().getName() + "`.");
		}
	}

	/**
	 * Creates new instance of endpoint exchange for given HTTP server exchange.
	 */
	@Nonnull
	protected abstract E createEndpointExchange(@Nonnull HttpRequest serverExchange,
	                                            @Nonnull String method,
	                                            @Nullable String requestBodyMediaType,
	                                            @Nullable String preferredResponseMediaType);

	/**
	 * Hook method called before actual endpoint handling logic is executed. Default implementation does nothing.
	 */
	protected void beforeRequestHandled(@Nonnull E exchange) {
		// default implementation does nothing
	}

	/**
	 * Hook method called after actual endpoint handling logic is executed. Default implementation does nothing.
	 */
	protected void afterRequestHandled(@Nonnull E exchange, @Nonnull EndpointResponse response) {
		// default implementation does nothing
	}

	/**
	 * Actual endpoint logic.
	 */
	@Nonnull
	protected abstract EndpointResponse doHandleRequest(@Nonnull E exchange);

	@Nonnull
	protected abstract <T extends ExternalApiInternalError> T createInternalError(@Nonnull String message);

	@Nonnull
	protected abstract <T extends ExternalApiInternalError> T createInternalError(@Nonnull String message, @Nonnull Throwable cause);

	@Nonnull
	protected abstract <T extends ExternalApiInvalidUsageException> T createInvalidUsageException(@Nonnull String message);

	/**
	 * Defines which HTTP methods can this particular endpoint process.
	 */
	@Nonnull
	public abstract Set<String> getSupportedHttpMethods();

	/**
	 * Defines which mime types are supported for request body.
	 * By default, no mime types are supported.
	 */
	@Nonnull
	public Set<String> getSupportedRequestContentTypes() {
		return Set.of();
	}

	/**
	 * Defines which mime types are supported for response body.
	 * By default, no mime types are supported.
	 * Note: order of mime types is important, it defines priority of mime types by which preferred response mime type is selected.
	 */
	@Nonnull
	public LinkedHashSet<String> getSupportedResponseContentTypes() {
		return new LinkedHashSet<>(0);
	}


	protected void validateRequest(@Nonnull HttpRequest exchange) {
		if (!hasSupportedHttpMethod(exchange)) {
			throw new HttpExchangeException(
				StatusCodes.METHOD_NOT_ALLOWED,
				"Supported methods are " + getSupportedHttpMethods().stream().map(it -> "`" + it + "`").collect(Collectors.joining(", ")) + "."
			);
		}
	}

	private boolean hasSupportedHttpMethod(@Nonnull HttpRequest request) {
		return getSupportedHttpMethods().contains(request.method().toString());
	}

	/**
	 * Validates that request body (if any) has supported media type and if so, returns it.
	 */
	@Nonnull
	private Optional<String> getRequestBodyContentType(@Nonnull HttpRequest request) {
		if (getSupportedRequestContentTypes().isEmpty()) {
			// we can ignore all content type headers because we don't accept any request body
			return Optional.empty();
		}

		final String bodyContentType = request.headers().contentType().toString();
		Assert.isTrue(
			bodyContentType != null &&
				getSupportedRequestContentTypes().stream().anyMatch(bodyContentType::startsWith),
			() -> new HttpExchangeException(
				StatusCodes.UNSUPPORTED_MEDIA_TYPE,
				"Supported request body media types are " + getSupportedRequestContentTypes().stream().map(it -> "`" + it + "`").collect(Collectors.joining(", ")) + "."
			)
		);

		return Optional.of(bodyContentType);
	}

	/**
	 * Validates that the request accepts at least one of the supported response media types and if so, picks the most
	 * preferred one by order of {@link #getSupportedResponseContentTypes().
	 */
	@Nonnull
	private Optional<String> getPreferredResponseContentType(@Nonnull HttpRequest request) {
		if (getSupportedResponseContentTypes().isEmpty()) {
			// we can ignore any accept headers because we will not return any body
			return Optional.empty();
		}

		final Set<String> acceptHeaders = parseAcceptHeaders(request);
		if (acceptHeaders == null) {
			// missing accept header means that client supports all media types
			return Optional.of(getSupportedResponseContentTypes().iterator().next());
		}

		for (String supportedMediaType : getSupportedResponseContentTypes()) {
			if (acceptHeaders.stream().anyMatch(it -> it.contains(supportedMediaType))) {
				return Optional.of(supportedMediaType);
			}
		}

		if (acceptHeaders.stream().anyMatch(it -> it.contains(MimeTypes.ALL))) {
			// no exact preferred media type found, but we can use this fallback at least
			return Optional.of(getSupportedResponseContentTypes().iterator().next());
		}

		throw new HttpExchangeException(
			StatusCodes.NOT_ACCEPTABLE,
			"Supported response body media types are " + getSupportedResponseContentTypes().stream().map(it -> "`" + it + "`").collect(Collectors.joining(", ")) + "."
		);
	}

	@Nullable
	private static Set<String> parseAcceptHeaders(@Nonnull HttpRequest request) {
		return request.headers().accept()
			.stream()
			.map(MediaType::toString)
			.collect(Collectors.toUnmodifiableSet());
	}

	/**
	 * Reads request body into raw string.
	 */
	@Nonnull
	protected String readRawRequestBody(@Nonnull E exchange) {
		Assert.isPremiseValid(
			!getSupportedRequestContentTypes().isEmpty(),
			() -> createInternalError("Handler doesn't support reading of request body.")
		);

		final String bodyContentType = exchange.httpRequest().contentType().toString();
		final Charset bodyCharset = Arrays.stream(bodyContentType.split(";"))
			.map(String::trim)
			.filter(part -> part.startsWith("charset"))
			.findFirst()
			.map(charsetPart -> {
				final String[] charsetParts = charsetPart.split("=");
				if (charsetParts.length != 2) {
					throw new HttpExchangeException(StatusCodes.UNSUPPORTED_MEDIA_TYPE, "Charset has invalid format");
				}
				return charsetParts[1].trim();
			})
			.map(charsetName -> {
				try {
					return Charset.forName(charsetName);
				} catch (IllegalCharsetNameException | UnsupportedCharsetException ex) {
					throw new HttpExchangeException(StatusCodes.UNSUPPORTED_MEDIA_TYPE, "Unsupported charset.");
				}
			})
			.orElse(StandardCharsets.UTF_8);

		final AtomicReference<String> resultsRef = new AtomicReference<>();
		exchange.httpRequest()
			.aggregate()
			.thenApply(r -> {
				try (HttpData data = r.content()) {
					try (InputStream inputStream = data.toInputStream()) {
						final byte[] buffer = new byte[AbstractHttpService.STREAM_FRAMES_TO_READ];
						final StringBuilder stringBuilder = new StringBuilder(64);
						int bytesRead;
						while ((bytesRead = inputStream.read(buffer)) != -1) {
							stringBuilder.append(new String(buffer, 0, bytesRead, bodyCharset));
						}
						resultsRef.set(stringBuilder.toString());
						return stringBuilder.toString();
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}
			});
		return resultsRef.get();
	}

	/**
	 * Tries to parse input request body JSON into data class.
	 */
	@Nonnull
	protected <T> T parseRequestBody(@Nonnull E exchange, @Nonnull Class<T> dataClass) {
		throw createInternalError("Cannot parse request body because handler doesn't support it.");
	}

	/**
	 * Serializes a result object into the preferred media type.
	 *
	 * @param exchange API request data
	 * @param responseWriter response writer to write the response to
	 * @param result result data from handler to serialize to the response
	 */
	protected void writeResponse(@Nonnull E exchange, @Nonnull HttpResponseWriter responseWriter, @Nonnull Object result) {
		throw createInternalError("Cannot serialize response body because handler doesn't support it.");
	}

	protected void processInputStreamInChunks(@Nonnull InputStream inputStream, @Nonnull HttpResponseWriter responseWriter) throws IOException {
		byte[] buffer = new byte[AbstractHttpService.STREAM_FRAMES_TO_READ];
		int bytesRead;

		while ((bytesRead = inputStream.read(buffer)) != -1) {
			// If the bytesRead is less than chunkSize, only process the read bytes
			if (bytesRead < AbstractHttpService.STREAM_FRAMES_TO_READ) {
				buffer = Arrays.copyOf(buffer, bytesRead);
			}
			if (!responseWriter.tryWrite(HttpData.wrap(buffer))) {
				responseWriter.close();
			}
		}
	}
}
