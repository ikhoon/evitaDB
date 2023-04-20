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

package io.evitadb.externalApi.graphql.api.catalog.dataApi.resolver.dataFetcher;

import graphql.schema.SelectedField;
import io.evitadb.api.query.filter.FilterBy;
import io.evitadb.api.query.order.OrderBy;
import io.evitadb.api.query.require.AssociatedDataContent;
import io.evitadb.api.query.require.AttributeContent;
import io.evitadb.api.query.require.DataInLocales;
import io.evitadb.api.query.require.EntityContentRequire;
import io.evitadb.api.query.require.EntityFetch;
import io.evitadb.api.query.require.EntityGroupFetch;
import io.evitadb.api.query.require.PriceContent;
import io.evitadb.api.query.require.ReferenceContent;
import io.evitadb.api.requestResponse.schema.AssociatedDataSchemaContract;
import io.evitadb.api.requestResponse.schema.AttributeSchemaContract;
import io.evitadb.api.requestResponse.schema.EntitySchemaContract;
import io.evitadb.api.requestResponse.schema.ReferenceSchemaContract;
import io.evitadb.externalApi.api.catalog.dataApi.constraint.ReferenceDataLocator;
import io.evitadb.externalApi.api.catalog.dataApi.model.EntityDescriptor;
import io.evitadb.externalApi.api.catalog.dataApi.model.ReferenceDescriptor;
import io.evitadb.externalApi.graphql.api.catalog.dataApi.model.EntityHeaderDescriptor.AssociatedDataFieldHeaderDescriptor;
import io.evitadb.externalApi.graphql.api.catalog.dataApi.model.EntityHeaderDescriptor.AttributesFieldHeaderDescriptor;
import io.evitadb.externalApi.graphql.api.catalog.dataApi.model.EntityHeaderDescriptor.PriceFieldHeaderDescriptor;
import io.evitadb.externalApi.graphql.api.catalog.dataApi.model.EntityHeaderDescriptor.PriceForSaleFieldHeaderDescriptor;
import io.evitadb.externalApi.graphql.api.catalog.dataApi.model.EntityHeaderDescriptor.PricesFieldHeaderDescriptor;
import io.evitadb.externalApi.graphql.api.catalog.dataApi.model.EntityHeaderDescriptor.ReferenceFieldHeaderDescriptor;
import io.evitadb.externalApi.graphql.api.catalog.dataApi.resolver.constraint.FilterConstraintResolver;
import io.evitadb.externalApi.graphql.api.catalog.dataApi.resolver.constraint.OrderConstraintResolver;
import io.evitadb.externalApi.graphql.exception.GraphQLInvalidArgumentException;
import io.evitadb.utils.Assert;
import lombok.RequiredArgsConstructor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.evitadb.api.query.QueryConstraints.*;
import static io.evitadb.externalApi.api.ExternalApiNamingConventions.PROPERTY_NAME_NAMING_CONVENTION;
import static io.evitadb.utils.CollectionUtils.createHashSet;

/**
 * Resolves {@link EntityFetch} based on which entity fields client specified.
 *
 * @author Lukáš Hornych, FG Forrest a.s. (c) 2022
 */
@RequiredArgsConstructor
public class EntityFetchRequireResolver {

	@Nonnull private final Function<String, EntitySchemaContract> entitySchemaFetcher;
	@Nonnull private final FilterConstraintResolver filterConstraintResolver;
	@Nonnull private final OrderConstraintResolver orderConstraintResolver;

	@Nonnull
	public Optional<EntityFetch> resolveEntityFetch(@Nonnull SelectionSetWrapper selectionSetWrapper,
	                                                @Nullable Locale desiredLocale,
	                                                @Nullable EntitySchemaContract currentEntitySchema) {
		return resolveContentRequirements(
			selectionSetWrapper,
			desiredLocale,
			currentEntitySchema
		)
			.map(it -> entityFetch(it.toArray(EntityContentRequire[]::new)));
	}

	@Nonnull
	public Optional<EntityGroupFetch> resolveGroupFetch(@Nonnull SelectionSetWrapper selectionSetWrapper,
	                                                    @Nullable Locale desiredLocale,
	                                                    @Nullable EntitySchemaContract currentEntitySchema) {
		return resolveContentRequirements(
			selectionSetWrapper,
			desiredLocale,
			currentEntitySchema
		)
			.map(it -> entityGroupFetch(it.toArray(EntityContentRequire[]::new)));
	}

	@Nonnull
	private Optional<List<EntityContentRequire>> resolveContentRequirements(@Nonnull SelectionSetWrapper selectionSetWrapper,
	                                                                        @Nullable Locale desiredLocale,
	                                                                        @Nullable EntitySchemaContract currentEntitySchema) {
		// no entity schema, no available data to fetch
		if (currentEntitySchema == null) {
			return Optional.empty();
		}

		if (!needsEntityBody(selectionSetWrapper, currentEntitySchema)) {
			return Optional.empty();
		}

		final List<EntityContentRequire> entityContentRequires = new LinkedList<>();
		resolveAttributeContentRequirement(selectionSetWrapper, currentEntitySchema).ifPresent(entityContentRequires::add);
		resolveAssociatedDataContentRequirement(selectionSetWrapper, currentEntitySchema).ifPresent(entityContentRequires::add);
		resolvePriceContentRequirement(selectionSetWrapper).ifPresent(entityContentRequires::add);
		entityContentRequires.addAll(resolveReferenceContentRequirement(selectionSetWrapper, desiredLocale, currentEntitySchema));
		resolveDataInLocalesRequirement(selectionSetWrapper, desiredLocale, currentEntitySchema).ifPresent(entityContentRequires::add);

		return Optional.of(entityContentRequires);
	}

	private boolean needsEntityBody(@Nonnull SelectionSetWrapper selectionSetWrapper, @Nonnull EntitySchemaContract currentEntitySchema) {
		return selectionSetWrapper.contains(EntityDescriptor.HIERARCHICAL_PLACEMENT.name()) ||
			selectionSetWrapper.contains(EntityDescriptor.LOCALES.name()) ||
			needsAttributes(selectionSetWrapper) ||
			needsAssociatedData(selectionSetWrapper) ||
			needsPrices(selectionSetWrapper) ||
			needsReferences(selectionSetWrapper, currentEntitySchema);
	}

	private boolean needsAttributes(@Nonnull SelectionSetWrapper selectionSetWrapper) {
		return selectionSetWrapper.contains(EntityDescriptor.ATTRIBUTES.name());
	}

	private boolean needsAssociatedData(@Nonnull SelectionSetWrapper selectionSetWrapper) {
		return selectionSetWrapper.contains(EntityDescriptor.ASSOCIATED_DATA.name());
	}

	private boolean needsPrices(@Nonnull SelectionSetWrapper selectionSetWrapper) {
		return selectionSetWrapper.contains(EntityDescriptor.PRICE.name() + "*");
	}

	private boolean needsReferences(@Nonnull SelectionSetWrapper selectionSetWrapper, @Nonnull EntitySchemaContract currentEntitySchema) {
		return currentEntitySchema.getReferences()
			.values()
			.stream()
			.map(it -> it.getNameVariant(PROPERTY_NAME_NAMING_CONVENTION))
			.anyMatch(selectionSetWrapper::contains);
	}

	@Nonnull
	private Optional<AttributeContent> resolveAttributeContentRequirement(@Nonnull SelectionSetWrapper selectionSetWrapper,
	                                                                      @Nonnull EntitySchemaContract currentEntitySchema) {
		if (!needsAttributes(selectionSetWrapper)) {
			return Optional.empty();
		}

		final String[] neededAttributes = selectionSetWrapper.getFields(EntityDescriptor.ATTRIBUTES.name())
			.stream()
			.flatMap(f -> SelectionSetWrapper.from(f.getSelectionSet()).getFields("*").stream())
			.map(f -> currentEntitySchema.getAttributeByName(f.getName(), PROPERTY_NAME_NAMING_CONVENTION))
			.filter(Optional::isPresent)
			.map(Optional::get)
			.map(AttributeSchemaContract::getName)
			.collect(Collectors.toUnmodifiableSet())
			.toArray(String[]::new);

		if (neededAttributes.length == 0) {
			return Optional.empty();
		}
		return Optional.of(attributeContent(neededAttributes));
	}

	@Nonnull
	private Optional<AssociatedDataContent> resolveAssociatedDataContentRequirement(@Nonnull SelectionSetWrapper selectionSetWrapper,
	                                                                                @Nonnull EntitySchemaContract currentEntitySchema) {
		if (!needsAssociatedData(selectionSetWrapper)) {
			return Optional.empty();
		}

		final String[] neededAssociatedData = selectionSetWrapper.getFields(EntityDescriptor.ASSOCIATED_DATA.name())
			.stream()
			.flatMap(f -> SelectionSetWrapper.from(f.getSelectionSet()).getFields("*").stream())
			.map(f -> currentEntitySchema.getAssociatedDataByName(f.getName(), PROPERTY_NAME_NAMING_CONVENTION))
			.filter(Optional::isPresent)
			.map(Optional::get)
			.map(AssociatedDataSchemaContract::getName)
			.collect(Collectors.toUnmodifiableSet())
			.toArray(String[]::new);

		if (neededAssociatedData.length == 0) {
			return Optional.empty();
		}
		return Optional.of(associatedDataContent(neededAssociatedData));
	}

	@Nonnull
	private Optional<PriceContent> resolvePriceContentRequirement(@Nonnull SelectionSetWrapper selectionSetWrapper) {
		if (!needsPrices(selectionSetWrapper)) {
			return Optional.empty();
		}

		if (selectionSetWrapper.getFields(EntityDescriptor.PRICES.name())
			.stream()
			.anyMatch(f -> f.getArguments().get(PricesFieldHeaderDescriptor.PRICE_LISTS.name()) == null)) {
			return Optional.of(priceContentAll());
		} else {
			final Set<String> neededPriceLists = createHashSet(10);

			// check price for sale fields
			neededPriceLists.addAll(
				selectionSetWrapper.getFields(EntityDescriptor.PRICE_FOR_SALE.name())
					.stream()
					.map(f -> (String) f.getArguments().get(PriceForSaleFieldHeaderDescriptor.PRICE_LIST.name()))
					.filter(Objects::nonNull)
					.collect(Collectors.toSet())
			);

			// check price fields
			neededPriceLists.addAll(
				selectionSetWrapper.getFields(EntityDescriptor.PRICE.name())
					.stream()
					.map(f -> (String) f.getArguments().get(PriceFieldHeaderDescriptor.PRICE_LIST.name()))
					.collect(Collectors.toSet())
			);

			// check prices fields
			//noinspection unchecked
			neededPriceLists.addAll(
				selectionSetWrapper.getFields(EntityDescriptor.PRICES.name())
					.stream()
					.flatMap(f -> ((List<String>) f.getArguments().get(PricesFieldHeaderDescriptor.PRICE_LISTS.name())).stream())
					.collect(Collectors.toSet())
			);

			return Optional.of(priceContent(neededPriceLists.toArray(String[]::new)));
		}
	}


	@Nonnull
	private List<ReferenceContent> resolveReferenceContentRequirement(@Nonnull SelectionSetWrapper selectionSetWrapper,
	                                                                  @Nullable Locale desiredLocale,
	                                                                  @Nonnull EntitySchemaContract currentEntitySchema) {
		if (!needsReferences(selectionSetWrapper, currentEntitySchema)) {
			return List.of();
		}

		return currentEntitySchema.getReferences()
			.values()
			.stream()
			.map(it -> new FieldsForReferenceHolder(
				it,
				selectionSetWrapper.getFields(it.getNameVariant(PROPERTY_NAME_NAMING_CONVENTION))
			))
			.filter(it -> !it.fields().isEmpty())
			.map(it -> new RequirementForReferenceHolder(
				it.referenceSchema(),
				resolveReferenceContentFilter(currentEntitySchema, it).orElse(null),
				resolveReferenceContentOrder(currentEntitySchema, it).orElse(null),
				resolveReferenceEntityRequirement(desiredLocale, it).orElse(null),
				resolveReferenceGroupRequirement(desiredLocale, it).orElse(null)
			))
			.map(it -> referenceContent(
				it.referenceSchema().getName(),
				it.filterBy(),
				it.orderBy(),
				it.entityRequirement(),
				it.groupRequirement()
			))
			.toList();
	}

	@Nonnull
	private Optional<FilterBy> resolveReferenceContentFilter(@Nonnull EntitySchemaContract currentEntitySchema,
	                                                         @Nonnull FieldsForReferenceHolder fieldsForReferenceHolder) {
		final List<SelectedField> fields = fieldsForReferenceHolder.fields();
		final boolean someFieldHasFilter = fields.stream()
			.anyMatch(it -> it.getArguments().containsKey(ReferenceFieldHeaderDescriptor.FILTER_BY.name()));
		if (!someFieldHasFilter) {
			return Optional.empty();
		}
		Assert.isTrue(
			fields.size() <= 1,
			() -> new GraphQLInvalidArgumentException("Reference filtering is currently supported only if there is only one reference of particular name requested.")
		);

		return Optional.ofNullable(
			(FilterBy) filterConstraintResolver.resolve(
				new ReferenceDataLocator(currentEntitySchema.getName(), fieldsForReferenceHolder.referenceSchema().getName()),
				ReferenceFieldHeaderDescriptor.FILTER_BY.name(),
				fields.get(0).getArguments().get(ReferenceFieldHeaderDescriptor.FILTER_BY.name())
			)
		);
	}

	@Nonnull
	private Optional<OrderBy> resolveReferenceContentOrder(@Nonnull EntitySchemaContract currentEntitySchema,
	                                                       @Nonnull FieldsForReferenceHolder fieldsForReferenceHolder) {
		final List<SelectedField> fields = fieldsForReferenceHolder.fields();
		final boolean someFieldHasFilter = fields.stream()
			.anyMatch(it -> it.getArguments().containsKey(ReferenceFieldHeaderDescriptor.ORDER_BY.name()));
		if (!someFieldHasFilter) {
			return Optional.empty();
		}
		Assert.isTrue(
			fields.size() <= 1,
			() -> new GraphQLInvalidArgumentException("Reference ordering is currently supported only if there is only one reference of particular name requested.")
		);

		return Optional.ofNullable(
			(OrderBy) orderConstraintResolver.resolve(
				new ReferenceDataLocator(currentEntitySchema.getName(), fieldsForReferenceHolder.referenceSchema().getName()),
				ReferenceFieldHeaderDescriptor.ORDER_BY.name(),
				fieldsForReferenceHolder.fields().get(0).getArguments().get(ReferenceFieldHeaderDescriptor.ORDER_BY.name())
			)
		);
	}

	@Nonnull
	private Optional<EntityFetch> resolveReferenceEntityRequirement(@Nullable Locale desiredLocale,
	                                                                @Nonnull FieldsForReferenceHolder fieldsForReference) {
		final SelectionSetWrapper referencedEntitySelectionSet = SelectionSetWrapper.from(
			fieldsForReference.fields()
				.stream()
				.flatMap(it2 -> it2.getSelectionSet().getFields(ReferenceDescriptor.REFERENCED_ENTITY.name()).stream())
				.map(SelectedField::getSelectionSet)
				.toList()
		);

		final EntitySchemaContract referencedEntitySchema = fieldsForReference.referenceSchema().isReferencedEntityTypeManaged()
			? entitySchemaFetcher.apply(fieldsForReference.referenceSchema().getReferencedEntityType())
			: null;

		final Optional<EntityFetch> referencedEntityRequirement = resolveEntityFetch(referencedEntitySelectionSet, desiredLocale, referencedEntitySchema);

		if (referencedEntityRequirement.isEmpty() && !referencedEntitySelectionSet.isEmpty()) {
			return Optional.of(entityFetch()); // if referenced entity was requested we want at least its body everytime
		}
		return referencedEntityRequirement;
	}

	@Nonnull
	private Optional<EntityGroupFetch> resolveReferenceGroupRequirement(@Nullable Locale desiredLocale,
	                                                          @Nonnull FieldsForReferenceHolder fieldsForReference) {
		final SelectionSetWrapper referencedGroupSelectionSet = SelectionSetWrapper.from(
			fieldsForReference.fields()
				.stream()
				.flatMap(it2 -> it2.getSelectionSet().getFields(ReferenceDescriptor.GROUP_ENTITY.name()).stream())
				.map(SelectedField::getSelectionSet)
				.toList()
		);

		final EntitySchemaContract referencedEntitySchema = fieldsForReference.referenceSchema().isReferencedGroupTypeManaged() ?
			entitySchemaFetcher.apply(fieldsForReference.referenceSchema().getReferencedGroupType()) :
			null;

		return resolveGroupFetch(referencedGroupSelectionSet, desiredLocale, referencedEntitySchema);
	}

	@Nonnull
	private Optional<DataInLocales> resolveDataInLocalesRequirement(@Nonnull SelectionSetWrapper selectionSetWrapper,
	                                                                @Nullable Locale desiredLocale,
	                                                                @Nonnull EntitySchemaContract currentEntitySchema) {
		if (!needsAttributes(selectionSetWrapper) && !needsAssociatedData(selectionSetWrapper)) {
			return Optional.empty();
		}

		final Set<Locale> neededLocales = createHashSet(currentEntitySchema.getLocales().size());
		if (desiredLocale != null) {
			neededLocales.add(desiredLocale);
		}
		neededLocales.addAll(
			selectionSetWrapper.getFields(EntityDescriptor.ATTRIBUTES.name())
				.stream()
				.map(f -> (Locale) f.getArguments().get(AttributesFieldHeaderDescriptor.LOCALE.name()))
				.filter(Objects::nonNull)
				.collect(Collectors.toSet())
		);
		neededLocales.addAll(
			selectionSetWrapper.getFields(EntityDescriptor.ASSOCIATED_DATA.name())
				.stream()
				.map(f -> (Locale) f.getArguments().get(AssociatedDataFieldHeaderDescriptor.LOCALE.name()))
				.filter(Objects::nonNull)
				.collect(Collectors.toSet())
		);

		if (neededLocales.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(dataInLocales(neededLocales.toArray(Locale[]::new)));
	}

	private record FieldsForReferenceHolder(@Nonnull ReferenceSchemaContract referenceSchema,
	                                        @Nonnull List<SelectedField> fields) {
	}

	private record RequirementForReferenceHolder(@Nonnull ReferenceSchemaContract referenceSchema,
	                                             @Nullable FilterBy filterBy,
	                                             @Nullable OrderBy orderBy,
	                                             @Nullable EntityFetch entityRequirement,
	                                             @Nullable EntityGroupFetch groupRequirement) {
	}
}
