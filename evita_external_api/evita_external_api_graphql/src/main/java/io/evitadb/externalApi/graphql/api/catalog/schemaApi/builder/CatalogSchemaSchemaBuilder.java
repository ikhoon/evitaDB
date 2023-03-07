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

package io.evitadb.externalApi.graphql.api.catalog.schemaApi.builder;

import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import io.evitadb.api.requestResponse.schema.AttributeSchemaContract;
import io.evitadb.api.requestResponse.schema.CatalogSchemaContract;
import io.evitadb.externalApi.api.catalog.schemaApi.model.CatalogSchemaApiRootDescriptor;
import io.evitadb.externalApi.api.catalog.schemaApi.model.CatalogSchemaDescriptor;
import io.evitadb.externalApi.api.catalog.schemaApi.model.EntitySchemaDescriptor;
import io.evitadb.externalApi.api.catalog.schemaApi.model.EntitySchemasDescriptor;
import io.evitadb.externalApi.api.catalog.schemaApi.model.GlobalAttributeSchemaDescriptor;
import io.evitadb.externalApi.api.catalog.schemaApi.model.GlobalAttributeSchemasDescriptor;
import io.evitadb.externalApi.api.catalog.schemaApi.model.UpdateCatalogSchemaQueryHeaderDescriptor;
import io.evitadb.externalApi.api.catalog.schemaApi.model.mutation.LocalCatalogSchemaMutationAggregateDescriptor;
import io.evitadb.externalApi.api.catalog.schemaApi.model.mutation.attribute.CreateGlobalAttributeSchemaMutationDescriptor;
import io.evitadb.externalApi.api.catalog.schemaApi.model.mutation.attribute.SetAttributeSchemaGloballyUniqueMutationDescriptor;
import io.evitadb.externalApi.api.catalog.schemaApi.model.mutation.catalog.CreateCatalogSchemaMutationDescriptor;
import io.evitadb.externalApi.api.catalog.schemaApi.model.mutation.catalog.ModifyCatalogSchemaDescriptionMutationDescriptor;
import io.evitadb.externalApi.api.catalog.schemaApi.model.mutation.catalog.ModifyCatalogSchemaNameMutationDescriptor;
import io.evitadb.externalApi.api.catalog.schemaApi.model.mutation.catalog.ModifyEntitySchemaMutationDescriptor;
import io.evitadb.externalApi.api.catalog.schemaApi.model.mutation.catalog.RemoveCatalogSchemaMutationDescriptor;
import io.evitadb.externalApi.graphql.api.builder.BuiltFieldDescriptor;
import io.evitadb.externalApi.graphql.api.builder.PartialGraphQLSchemaBuilder;
import io.evitadb.externalApi.graphql.api.catalog.builder.CatalogGraphQLSchemaBuildingContext;
import io.evitadb.externalApi.graphql.api.catalog.schemaApi.resolver.dataFetcher.AllAssociatedDataSchemasDataFetcher;
import io.evitadb.externalApi.graphql.api.catalog.schemaApi.resolver.dataFetcher.AllAttributeSchemasDataFetcher;
import io.evitadb.externalApi.graphql.api.catalog.schemaApi.resolver.dataFetcher.AllEntitySchemasDataFetcher;
import io.evitadb.externalApi.graphql.api.catalog.schemaApi.resolver.dataFetcher.AllReferenceSchemasDataFetcher;
import io.evitadb.externalApi.graphql.api.catalog.schemaApi.resolver.dataFetcher.AttributeSchemaDataFetcher;
import io.evitadb.externalApi.graphql.api.catalog.schemaApi.resolver.dataFetcher.AttributeSchemasDataFetcher;
import io.evitadb.externalApi.graphql.api.catalog.schemaApi.resolver.dataFetcher.CatalogEntitySchemaDataFetcher;
import io.evitadb.externalApi.graphql.api.catalog.schemaApi.resolver.dataFetcher.CatalogEntitySchemasDataFetcher;
import io.evitadb.externalApi.graphql.api.catalog.schemaApi.resolver.dataFetcher.CatalogSchemaDataFetcher;
import io.evitadb.externalApi.graphql.api.catalog.schemaApi.resolver.mutatingDataFetcher.UpdateCatalogSchemaMutatingDataFetcher;

import javax.annotation.Nonnull;

import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLList.list;
import static graphql.schema.GraphQLNonNull.nonNull;
import static graphql.schema.GraphQLTypeReference.typeRef;
import static io.evitadb.externalApi.api.ExternalApiNamingConventions.FIELD_NAME_NAMING_CONVENTION;
import static io.evitadb.externalApi.graphql.api.catalog.schemaApi.builder.EntitySchemaSchemaBuilder.ATTRIBUTE_SCHEMA_UNION_NAME;

/**
 * Implementation of {@link PartialGraphQLSchemaBuilder} for building schema for fetching and updating {@link CatalogSchemaContract}.
 * Note: it depends on {@link EntitySchemaSchemaBuilder} because large portion of mutation objects are same as in {@link EntitySchemaSchemaBuilder}
 * thus they cannot be created again in this builder.
 *
 * @author Lukáš Hornych, FG Forrest a.s. (c) 2023
 */
public class CatalogSchemaSchemaBuilder extends PartialGraphQLSchemaBuilder<CatalogGraphQLSchemaBuildingContext> {

	public CatalogSchemaSchemaBuilder(@Nonnull CatalogGraphQLSchemaBuildingContext catalogGraphQLSchemaBuildingContext) {
		super(catalogGraphQLSchemaBuildingContext);
	}

	@Override
	public void build() {
		// build reusable objects
		context.registerType(buildEntitySchemaObject());
		context.registerType(buildCatalogSchemaObject());

		// catalog schema mutations
		context.registerType(CreateCatalogSchemaMutationDescriptor.THIS.to(inputObjectBuilderTransformer).build());
		context.registerType(ModifyEntitySchemaMutationDescriptor.THIS.to(inputObjectBuilderTransformer).build());
		context.registerType(ModifyCatalogSchemaDescriptionMutationDescriptor.THIS.to(inputObjectBuilderTransformer).build());
		context.registerType(ModifyCatalogSchemaNameMutationDescriptor.THIS.to(inputObjectBuilderTransformer).build());
		context.registerType(RemoveCatalogSchemaMutationDescriptor.THIS.to(inputObjectBuilderTransformer).build());

		// global attribute schema mutations
		context.registerType(CreateGlobalAttributeSchemaMutationDescriptor.THIS.to(inputObjectBuilderTransformer).build());
		context.registerType(SetAttributeSchemaGloballyUniqueMutationDescriptor.THIS.to(inputObjectBuilderTransformer).build());

		// other mutation objects should be already created by EntitySchemaSchemaBuilder
		context.registerType(LocalCatalogSchemaMutationAggregateDescriptor.THIS.to(inputObjectBuilderTransformer).build());

		// build catalog field
		context.registerQueryField(buildCatalogSchemaField());
		context.registerMutationField(buildUpdateCatalogSchemaField());
	}

	/*
		Catalog schema
	 */

	@Nonnull
	private BuiltFieldDescriptor buildCatalogSchemaField() {
		return new BuiltFieldDescriptor(
			CatalogSchemaApiRootDescriptor.GET_CATALOG_SCHEMA.to(staticEndpointBuilderTransformer).build(),
			new CatalogSchemaDataFetcher()
		);
	}

	@Nonnull
	private GraphQLObjectType buildCatalogSchemaObject() {
		final CatalogSchemaContract catalogSchema = context.getSchema();

		final GraphQLObjectType.Builder schemaObjectBuilder = CatalogSchemaDescriptor.THIS.to(objectBuilderTransformer);

		if (!catalogSchema.getAttributes().isEmpty()) {
			context.registerFieldToObject(
				CatalogSchemaDescriptor.THIS,
				schemaObjectBuilder,
				buildGlobalAttributeSchemasField()
			);
		}
		context.registerDataFetcher(
			CatalogSchemaDescriptor.THIS,
			CatalogSchemaDescriptor.ALL_ATTRIBUTES,
			new AllAttributeSchemasDataFetcher()
		);

		if (!context.getEntitySchemas().isEmpty()) {
			context.registerFieldToObject(
				CatalogSchemaDescriptor.THIS,
				schemaObjectBuilder,
				buildEntitySchemasField()
			);
		}
		context.registerDataFetcher(
			CatalogSchemaDescriptor.THIS,
			CatalogSchemaDescriptor.ALL_ENTITY_SCHEMAS,
			new AllEntitySchemasDataFetcher()
		);

		return schemaObjectBuilder.build();
	}

	/*
		Global attributes
	 */

	@Nonnull
	private BuiltFieldDescriptor buildGlobalAttributeSchemasField() {
		final GraphQLObjectType attributeSchemasObject = buildGlobalAttributeSchemasObject();

		final GraphQLFieldDefinition attributeSchemasField = CatalogSchemaDescriptor.ATTRIBUTES
			.to(fieldBuilderTransformer)
			.type(nonNull(attributeSchemasObject))
			.build();

		return new BuiltFieldDescriptor(
			attributeSchemasField,
			new AttributeSchemasDataFetcher()
		);
	}

	@Nonnull
	private GraphQLObjectType buildGlobalAttributeSchemasObject() {
		final GraphQLObjectType.Builder attributeSchemasObjectBuilder = GlobalAttributeSchemasDescriptor.THIS
			.to(objectBuilderTransformer);

		context.getSchema().getAttributes()
			.values()
			.forEach(attributeSchema ->
				context.registerFieldToObject(
					GlobalAttributeSchemasDescriptor.THIS,
					attributeSchemasObjectBuilder,
					buildGlobalAttributeSchemaField(attributeSchema)
				)
			);

		return attributeSchemasObjectBuilder.build();
	}

	@Nonnull
	private BuiltFieldDescriptor buildGlobalAttributeSchemaField(@Nonnull AttributeSchemaContract attributeSchema) {
		final GraphQLFieldDefinition attributeSchemaField = newFieldDefinition()
			.name(attributeSchema.getNameVariant(FIELD_NAME_NAMING_CONVENTION))
			.description(attributeSchema.getDescription())
			.deprecate(attributeSchema.getDeprecationNotice())
			.type(nonNull(typeRef(GlobalAttributeSchemaDescriptor.THIS.name())))
			.build();

		return new BuiltFieldDescriptor(
			attributeSchemaField,
			new AttributeSchemaDataFetcher(attributeSchema.getName())
		);
	}

	/*
		Entity schema
	 */

	@Nonnull
	private BuiltFieldDescriptor buildEntitySchemasField() {
		final GraphQLObjectType entitySchemasObject = buildEntitySchemasObject();

		final GraphQLFieldDefinition entitySchemasField = CatalogSchemaDescriptor.ENTITY_SCHEMAS
			.to(fieldBuilderTransformer)
			.type(nonNull(entitySchemasObject))
			.build();

		return new BuiltFieldDescriptor(
			entitySchemasField,
			new CatalogEntitySchemasDataFetcher()
		);
	}

	@Nonnull
	private GraphQLObjectType buildEntitySchemasObject() {
		final GraphQLObjectType.Builder entitySchemasObjectBuilder = EntitySchemasDescriptor.THIS
			.to(objectBuilderTransformer);

		context.getEntitySchemas().forEach(entitySchema -> {
			final GraphQLFieldDefinition entitySchemaField = newFieldDefinition()
				.name(entitySchema.getNameVariant(FIELD_NAME_NAMING_CONVENTION))
				.description(entitySchema.getDescription())
				.deprecate(entitySchema.getDeprecationNotice())
				.type(nonNull(typeRef(EntitySchemaDescriptor.THIS_SPECIFIC.name(entitySchema))))
				.build();
			final CatalogEntitySchemaDataFetcher dataFetcher = new CatalogEntitySchemaDataFetcher(entitySchema.getName());
			final BuiltFieldDescriptor fieldDescriptor = new BuiltFieldDescriptor(entitySchemaField, dataFetcher);

			context.registerFieldToObject(
				EntitySchemasDescriptor.THIS,
				entitySchemasObjectBuilder,
				fieldDescriptor
			);
		});

		return entitySchemasObjectBuilder.build();
	}

	@Nonnull
	private GraphQLObjectType buildEntitySchemaObject() {
		final GraphQLObjectType.Builder entitySchemaBuilder = EntitySchemaDescriptor.THIS_GENERIC
			.to(objectBuilderTransformer);

		entitySchemaBuilder.field(EntitySchemaDescriptor.ALL_ATTRIBUTES
			.to(fieldBuilderTransformer)
			.type(nonNull(list(nonNull(typeRef(ATTRIBUTE_SCHEMA_UNION_NAME))))));
		context.registerDataFetcher(
			EntitySchemaDescriptor.THIS_GENERIC,
			EntitySchemaDescriptor.ALL_ATTRIBUTES,
			new AllAttributeSchemasDataFetcher()
		);

		entitySchemaBuilder.field(EntitySchemaDescriptor.ALL_ASSOCIATED_DATA.to(fieldBuilderTransformer));
		context.registerDataFetcher(
			EntitySchemaDescriptor.THIS_GENERIC,
			EntitySchemaDescriptor.ALL_ASSOCIATED_DATA,
			new AllAssociatedDataSchemasDataFetcher()
		);

		entitySchemaBuilder.field(EntitySchemaDescriptor.ALL_REFERENCES.to(fieldBuilderTransformer));
		context.registerDataFetcher(
			EntitySchemaDescriptor.THIS_GENERIC,
			EntitySchemaDescriptor.ALL_REFERENCES,
			new AllReferenceSchemasDataFetcher()
		);

		return entitySchemaBuilder.build();
	}

	/*
		Mutations
	 */

	@Nonnull
	private BuiltFieldDescriptor buildUpdateCatalogSchemaField() {
		final GraphQLFieldDefinition catalogSchemaField = CatalogSchemaApiRootDescriptor.UPDATE_CATALOG_SCHEMA
			.to(staticEndpointBuilderTransformer)
			.argument(UpdateCatalogSchemaQueryHeaderDescriptor.MUTATIONS.to(argumentBuilderTransformer))
			.build();

		return new BuiltFieldDescriptor(
			catalogSchemaField,
			new UpdateCatalogSchemaMutatingDataFetcher()
		);
	}
}
