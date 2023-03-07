/*
 *
 *                         _ _        ____  ____
 *               _____   _(_) |_ __ _|  _ \| __ )
 *              / _ \ \ / / | __/ _` | | | |  _ \
 *             |  __/\ V /| | || (_| | |_| | |_) |
 *              \___| \_/ |_|\__\__,_|____/|____/
 *
 *   Copyright (c) 2023
 *
 *   Licensed under the Business Source License, Version 1.1 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   https://github.com/FgForrest/evitaDB/blob/main/LICENSE
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package io.evitadb.externalApi.rest.api.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.evitadb.api.requestResponse.schema.CatalogSchemaContract;
import io.evitadb.api.requestResponse.schema.EntitySchemaContract;
import io.evitadb.core.Evita;
import io.evitadb.externalApi.rest.api.catalog.ParamDescriptor;
import io.evitadb.externalApi.rest.exception.OpenApiSchemaBuildingError;
import io.evitadb.externalApi.rest.io.handler.CollectionRestHandlingContext;
import io.evitadb.externalApi.rest.io.handler.RestHandler;
import io.evitadb.utils.Assert;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static io.evitadb.externalApi.api.ExternalApiNamingConventions.URL_NAME_NAMING_CONVENTION;
import static io.evitadb.externalApi.api.catalog.dataApi.model.CatalogDataApiRootDescriptor.ENTITY_LOCALE_ENUM;
import static io.evitadb.externalApi.rest.api.dto.OpenApiEndpoint.PathBuilder.newPath;
import static io.evitadb.externalApi.rest.api.dto.OpenApiEndpointParameter.newPathParameter;
import static io.evitadb.externalApi.rest.api.dto.OpenApiTypeReference.typeRefTo;
import static io.swagger.v3.oas.models.PathItem.HttpMethod.*;

/**
 * TODO lho docs
 *
 * @author Lukáš Hornych, 2023
 */
public class OpenApiCollectionEndpoint extends OpenApiEndpoint<CollectionRestHandlingContext> {

	@Nonnull private final EntitySchemaContract entitySchema;
	protected final boolean localized;

	private OpenApiCollectionEndpoint(@Nonnull CatalogSchemaContract catalogSchema,
									  @Nonnull EntitySchemaContract entitySchema,
	                                  @Nonnull PathItem.HttpMethod method,
	                                  @Nonnull Path path,
									  boolean localized,
	                                  @Nonnull String description,
	                                  @Nullable String deprecationNotice,
	                                  @Nonnull List<OpenApiEndpointParameter> parameters,
	                                  @Nullable OpenApiSimpleType requestBody,
	                                  @Nonnull OpenApiSimpleType successResponse,
	                                  @Nonnull Function<CollectionRestHandlingContext, RestHandler<CollectionRestHandlingContext>> handlerBuilder) {
		super(catalogSchema, method, path, description, deprecationNotice, parameters, requestBody, successResponse, handlerBuilder);
		this.entitySchema = entitySchema;
		this.localized = localized;
	}

	@Nonnull
	public static Builder newCollectionEndpoint(@Nonnull CatalogSchemaContract catalogSchema,
	                                            @Nonnull EntitySchemaContract entitySchema) {
		return new Builder(catalogSchema, entitySchema);
	}

	@Nonnull
	@Override
	public RestHandler<CollectionRestHandlingContext> toHandler(@Nonnull ObjectMapper objectMapper,
	                                                            @Nonnull Evita evita,
	                                                            @Nonnull OpenAPI openApi) {
		final CollectionRestHandlingContext context = new CollectionRestHandlingContext(
			objectMapper,
			evita,
			catalogSchema,
			entitySchema,
			openApi,
			toOperation(), // todo lho i dont like that this will be created twice, once to register to openapi, second here
			localized
		);
		return handlerBuilder.apply(context);
	}


	public static class Builder {

		@Nonnull private final CatalogSchemaContract catalogSchema;
		@Nonnull private final EntitySchemaContract entitySchema;

		@Nullable private PathItem.HttpMethod method;
		@Nullable private Path path;
		private boolean localized;

		@Nullable private String description;
		@Nullable private String deprecationNotice;
		@Nonnull private final List<OpenApiEndpointParameter> parameters;

		@Nullable private OpenApiSimpleType requestBody;
		@Nullable private OpenApiSimpleType successResponse;

		@Nullable private Function<CollectionRestHandlingContext, RestHandler<CollectionRestHandlingContext>> handlerBuilder;

		private Builder(@Nonnull CatalogSchemaContract catalogSchema, @Nonnull EntitySchemaContract entitySchema) {
			this.catalogSchema = catalogSchema;
			this.entitySchema = entitySchema;
			this.parameters = new LinkedList<>();
		}

		@Nonnull
		public Builder method(@Nonnull PathItem.HttpMethod method) {
			this.method = method;
			return this;
		}

		@Nonnull
		public Builder path(@Nonnull UnaryOperator<PathBuilder> pathBuilderFunction) {
			return path(false, pathBuilderFunction);
		}

		@Nonnull
		public Builder path(boolean localized, @Nonnull UnaryOperator<PathBuilder> pathBuilderFunction) {
			// prepare new catalog path
			PathBuilder pathBuilder = newPath()
				.staticItem(catalogSchema.getNameVariant(URL_NAME_NAMING_CONVENTION));
			if (localized) {
				pathBuilder.paramItem(newPathParameter()
					.name(ParamDescriptor.REQUIRED_LOCALE.name())
					.description(ParamDescriptor.REQUIRED_LOCALE.description())
					.type(typeRefTo(ENTITY_LOCALE_ENUM.name(entitySchema)))
					.build());
			}
			pathBuilder.staticItem(entitySchema.getNameVariant(URL_NAME_NAMING_CONVENTION));

			pathBuilder = pathBuilderFunction.apply(pathBuilder);

			this.localized = localized;
			this.path = pathBuilder.getPath();
			this.parameters.addAll(pathBuilder.getPathParameters());
			return this;
		}

		@Nonnull
		public Builder description(@Nonnull String description) {
			this.description = description;
			return this;
		}

		@Nonnull
		public Builder deprecationNotice(@Nullable String deprecationNotice) {
			this.deprecationNotice = deprecationNotice;
			return this;
		}

		@Nonnull
		public Builder queryParameter(@Nonnull OpenApiEndpointParameter queryParameter) {
			Assert.isPremiseValid(
				queryParameter.getLocation().equals(OpenApiOperationParameterLocation.QUERY),
				() -> new OpenApiSchemaBuildingError("Only query parameters are supported here.")
			);
			this.parameters.add(queryParameter);
			return this;
		}

		@Nonnull
		public Builder queryParameters(@Nonnull List<OpenApiEndpointParameter> queryParameters) {
			queryParameters.forEach(queryParameter ->
				Assert.isPremiseValid(
					queryParameter.getLocation().equals(OpenApiOperationParameterLocation.QUERY),
					() -> new OpenApiSchemaBuildingError("Only query parameters are supported here.")
				)
			);
			this.parameters.addAll(queryParameters);
			return this;
		}

		@Nonnull
		public Builder requestBody(@Nonnull OpenApiSimpleType requestBodyType) {
			this.requestBody = requestBodyType;
			return this;
		}

		@Nonnull
		public Builder successResponse(@Nonnull OpenApiSimpleType successResponseType) {
			this.successResponse = successResponseType;
			return this;
		}

		@Nonnull
		public Builder handler(@Nonnull Function<CollectionRestHandlingContext, RestHandler<CollectionRestHandlingContext>> handlerBuilder) {
			this.handlerBuilder = handlerBuilder;
			return this;
		}

		@Nonnull
		public OpenApiCollectionEndpoint build() {
			Assert.isPremiseValid(
				path != null,
				() -> new OpenApiSchemaBuildingError("Missing endpoint path.")
			);
			Assert.isPremiseValid(
				method != null,
				() -> new OpenApiSchemaBuildingError("Endpoint `" + path + "` is missing method.")
			);
			Assert.isPremiseValid(
				description != null && !description.isEmpty(),
				() -> new OpenApiSchemaBuildingError("Endpoint `" + path + "` is missing description.")
			);

			if (Set.of(POST, PUT, PATCH).contains(method)) {
				Assert.isPremiseValid(
					requestBody != null,
					() -> new OpenApiSchemaBuildingError("Endpoint `" + path + "` is missing request body.")
				);
			} else if (Set.of(GET, HEAD, TRACE, OPTIONS).contains(method)) {
				Assert.isPremiseValid(
					requestBody == null,
					() -> new OpenApiSchemaBuildingError("Endpoint `" + path + "` doesn't support request body.")
				);
			}

			if (Set.of(GET, POST, PUT, PATCH).contains(method)) {
				Assert.isPremiseValid(
					successResponse != null,
					() -> new OpenApiSchemaBuildingError("Endpoint `" + path + "` is missing success response.")
				);
			}

			Assert.isPremiseValid(
				handlerBuilder != null,
				() -> new OpenApiSchemaBuildingError("Endpoint `" + path + "` is missing handler.")
			);

			return new OpenApiCollectionEndpoint(
				catalogSchema,
				entitySchema,
				method,
				path,
				localized,
				description,
				deprecationNotice,
				parameters,
				requestBody,
				successResponse,
				handlerBuilder
			);
		}
	}
}
