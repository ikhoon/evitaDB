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

package io.evitadb.core;

import com.esotericsoftware.kryo.util.IntMap;
import io.evitadb.api.CatalogState;
import io.evitadb.api.EntityCollectionContract;
import io.evitadb.api.EvitaSessionContract;
import io.evitadb.api.exception.ConcurrentSchemaUpdateException;
import io.evitadb.api.exception.EntityAlreadyRemovedException;
import io.evitadb.api.exception.InvalidMutationException;
import io.evitadb.api.exception.InvalidSchemaMutationException;
import io.evitadb.api.exception.SchemaAlteringException;
import io.evitadb.api.exception.SchemaNotFoundException;
import io.evitadb.api.exception.TransactionException;
import io.evitadb.api.observability.trace.TracingContext;
import io.evitadb.api.proxy.SealedEntityProxy;
import io.evitadb.api.query.Query;
import io.evitadb.api.query.require.EntityFetch;
import io.evitadb.api.requestResponse.EvitaEntityReferenceResponse;
import io.evitadb.api.requestResponse.EvitaRequest;
import io.evitadb.api.requestResponse.EvitaRequest.RequirementContext;
import io.evitadb.api.requestResponse.EvitaResponse;
import io.evitadb.api.requestResponse.data.DeletedHierarchy;
import io.evitadb.api.requestResponse.data.EntityClassifier;
import io.evitadb.api.requestResponse.data.EntityClassifierWithParent;
import io.evitadb.api.requestResponse.data.EntityContract;
import io.evitadb.api.requestResponse.data.EntityEditor.EntityBuilder;
import io.evitadb.api.requestResponse.data.SealedEntity;
import io.evitadb.api.requestResponse.data.mutation.EntityMutation;
import io.evitadb.api.requestResponse.data.mutation.EntityMutation.EntityExistence;
import io.evitadb.api.requestResponse.data.mutation.EntityRemoveMutation;
import io.evitadb.api.requestResponse.data.mutation.EntityUpsertMutation;
import io.evitadb.api.requestResponse.data.mutation.LocalMutation;
import io.evitadb.api.requestResponse.data.mutation.LocalMutationExecutor;
import io.evitadb.api.requestResponse.data.structure.BinaryEntity;
import io.evitadb.api.requestResponse.data.structure.Entity;
import io.evitadb.api.requestResponse.data.structure.EntityReference;
import io.evitadb.api.requestResponse.data.structure.InitialEntityBuilder;
import io.evitadb.api.requestResponse.data.structure.ReferenceFetcher;
import io.evitadb.api.requestResponse.data.structure.predicate.AssociatedDataValueSerializablePredicate;
import io.evitadb.api.requestResponse.data.structure.predicate.AttributeValueSerializablePredicate;
import io.evitadb.api.requestResponse.data.structure.predicate.HierarchySerializablePredicate;
import io.evitadb.api.requestResponse.data.structure.predicate.LocaleSerializablePredicate;
import io.evitadb.api.requestResponse.data.structure.predicate.PriceContractSerializablePredicate;
import io.evitadb.api.requestResponse.data.structure.predicate.ReferenceContractSerializablePredicate;
import io.evitadb.api.requestResponse.extraResult.QueryTelemetry;
import io.evitadb.api.requestResponse.extraResult.QueryTelemetry.QueryPhase;
import io.evitadb.api.requestResponse.schema.Cardinality;
import io.evitadb.api.requestResponse.schema.CatalogSchemaContract;
import io.evitadb.api.requestResponse.schema.EntitySchemaContract;
import io.evitadb.api.requestResponse.schema.EntitySchemaDecorator;
import io.evitadb.api.requestResponse.schema.NamedSchemaContract;
import io.evitadb.api.requestResponse.schema.ReferenceSchemaContract;
import io.evitadb.api.requestResponse.schema.SealedCatalogSchema;
import io.evitadb.api.requestResponse.schema.SealedEntitySchema;
import io.evitadb.api.requestResponse.schema.dto.EntitySchema;
import io.evitadb.api.requestResponse.schema.mutation.EntitySchemaMutation;
import io.evitadb.api.requestResponse.schema.mutation.catalog.ModifyEntitySchemaMutation;
import io.evitadb.api.requestResponse.schema.mutation.entity.SetEntitySchemaWithHierarchyMutation;
import io.evitadb.core.buffer.DataStoreChanges;
import io.evitadb.core.buffer.DataStoreMemoryBuffer;
import io.evitadb.core.buffer.TransactionalDataStoreMemoryBuffer;
import io.evitadb.core.buffer.WarmUpDataStoreMemoryBuffer;
import io.evitadb.core.cache.CacheSupervisor;
import io.evitadb.core.query.QueryPlan;
import io.evitadb.core.query.QueryPlanner;
import io.evitadb.core.query.QueryPlanningContext;
import io.evitadb.core.query.ReferencedEntityFetcher;
import io.evitadb.core.query.algebra.Formula;
import io.evitadb.core.query.response.ServerBinaryEntityDecorator;
import io.evitadb.core.query.response.ServerEntityDecorator;
import io.evitadb.core.sequence.SequenceService;
import io.evitadb.core.sequence.SequenceType;
import io.evitadb.core.transaction.memory.TransactionalLayerMaintainer;
import io.evitadb.core.transaction.memory.TransactionalLayerProducer;
import io.evitadb.core.transaction.memory.TransactionalObjectVersion;
import io.evitadb.core.transaction.stage.mutation.VerifiedEntityRemoveMutation;
import io.evitadb.core.transaction.stage.mutation.VerifiedEntityUpsertMutation;
import io.evitadb.exception.EvitaInvalidUsageException;
import io.evitadb.exception.GenericEvitaInternalError;
import io.evitadb.index.EntityIndex;
import io.evitadb.index.EntityIndexKey;
import io.evitadb.index.EntityIndexType;
import io.evitadb.index.GlobalEntityIndex;
import io.evitadb.index.IndexMaintainer;
import io.evitadb.index.ReducedEntityIndex;
import io.evitadb.index.ReferencedTypeEntityIndex;
import io.evitadb.index.map.TransactionalMap;
import io.evitadb.index.mutation.ContainerizedLocalMutationExecutor;
import io.evitadb.index.mutation.EntityIndexLocalMutationExecutor;
import io.evitadb.index.reference.ReferenceChanges;
import io.evitadb.index.reference.TransactionalReference;
import io.evitadb.store.entity.model.schema.EntitySchemaStoragePart;
import io.evitadb.store.entity.serializer.EntitySchemaContext;
import io.evitadb.store.spi.CatalogPersistenceService;
import io.evitadb.store.spi.EntityCollectionPersistenceService;
import io.evitadb.store.spi.EntityCollectionPersistenceService.BinaryEntityWithFetchCount;
import io.evitadb.store.spi.EntityCollectionPersistenceService.EntityWithFetchCount;
import io.evitadb.store.spi.HeaderInfoSupplier;
import io.evitadb.store.spi.StoragePartPersistenceService;
import io.evitadb.store.spi.model.EntityCollectionHeader;
import io.evitadb.utils.Assert;
import lombok.Getter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.evitadb.api.query.QueryConstraints.*;
import static io.evitadb.core.Transaction.getTransactionalLayerMaintainer;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;

/**
 * Entity collection maintains all entities of same {@link Entity#getType()}. Entity collection could be imagined
 * as single table in RDBMS environment or document type in case of Elasticsearch or Mongo DB no sql databases.
 *
 * EntityCollection is set of records of the same type. In the relational world it would represent a table (or a single
 * main table with several other tables containing records referring to that main table). Entity collection maintains
 * all entities of the same type (i.e. same {@link EntitySchema}).
 *
 * @author Jan Novotný (novotny@fg.cz), FG Forrest a.s. (c) 2021
 */
public final class EntityCollection implements
	TransactionalLayerProducer<DataStoreChanges<EntityIndexKey, EntityIndex>, EntityCollection>,
	EntityCollectionContract,
	CatalogRelatedDataStructure<EntityCollection> {

	@Getter private final long id = TransactionalObjectVersion.SEQUENCE.nextId();
	/**
	 * Contains a unique identifier of the entity type that is assigned on entity collection creation and never changes.
	 */
	@Getter private final String entityType;
	/**
	 * Contains a unique identifier of the entity type that is assigned on entity collection creation and never changes.
	 * The primary key can be used interchangeably to {@link EntitySchema#getName() String entity type}.
	 */
	@Getter private final int entityTypePrimaryKey;
	/**
	 * Performance optimization flag that keeps information whether the collection was empty when it was created.
	 */
	private final boolean emptyOnStart;
	/**
	 * EntityIndex factory implementation.
	 */
	private final EntityIndexMaintainer entityIndexCreator = new EntityIndexMaintainer();
	/**
	 * Contains entity schema in the form it was initialized during creation.
	 */
	private final EntitySchema initialSchema;
	/**
	 * Contains sequence that allows automatic assigning monotonic primary keys to the entities.
	 */
	private final AtomicInteger pkSequence;
	/**
	 * Contains sequence that allows assigning monotonic primary keys to the entity indexes.
	 */
	private final AtomicInteger indexPkSequence;
	/**
	 * Service allowing to recreate I/O collection service on-demand.
	 */
	private final CatalogPersistenceService catalogPersistenceService;
	/**
	 * Collection of search indexes prepared to handle queries.
	 */
	private final TransactionalMap<EntityIndexKey, EntityIndex> indexes;
	/**
	 * True if collection was already terminated. No other termination will be allowed.
	 */
	private final AtomicBoolean terminated = new AtomicBoolean(false);
	/**
	 * This instance is used to cover changes in transactional memory and persistent storage reference.
	 *
	 * @see TransactionalDataStoreMemoryBuffer documentation
	 */
	@Getter private final DataStoreMemoryBuffer<EntityIndexKey, EntityIndex> dataStoreBuffer;
	/**
	 * Formula supervisor is an entry point to the Evita cache. The idea is that each {@link Formula} can be identified
	 * by its {@link Formula#getHash()} method and when the supervisor identifies that certain
	 * formula is frequently used in query formulas it moves its memoized results to the cache. The non-computed formula
	 * of the same hash will be exchanged in next query that contains it with the cached formula that already contains
	 * memoized result.
	 */
	private final CacheSupervisor cacheSupervisor;
	/**
	 * Provides the tracing context for tracking the execution flow in the application.
	 **/
	private final TracingContext tracingContext;
	/**
	 * Inner class that provides information for the {@link EntityCollectionHeader} when it's created in the persistence
	 * layer.
	 */
	private final HeaderInfoSupplier headerInfoSupplier = new EntityCollectionHeaderInfoSupplier();
	/**
	 * Service containing I/O related methods.
	 */
	private final EntityCollectionPersistenceService persistenceService;
	/**
	 * This field contains reference to the CURRENT {@link Catalog} instance allowing to access {@link EntityCollection}
	 * for any of entity types that are known to the catalog this collection is part of. Reference to other collections
	 * is used to access their schema or their indexes from this collection.
	 *
	 * The reference pointer is used because when transaction is committed and new catalog is created to atomically swap
	 * changes and left old readers finish with old catalog, the entity collection copy is created, and we need to init
	 * the reference to this function lazily when new catalog is instantiated (existence of the new collection precedes
	 * the creation of the catalog copy).
	 */
	private Catalog catalog;
	/**
	 * Contains schema of the entity type that is used for formal verification of the data consistency and indexing
	 * prescription.
	 */
	private TransactionalReference<EntitySchemaDecorator> schema;

	/**
	 * Retrieves the primary key of the given entity or throws an unified exception.
	 */
	private static int getPrimaryKey(@Nonnull Serializable entity) {
		if (entity instanceof EntityClassifier entityClassifier) {
			return Objects.requireNonNull(entityClassifier.getPrimaryKey());
		} else if (entity instanceof SealedEntityProxy sealedEntityProxy) {
			return Objects.requireNonNull(sealedEntityProxy.entity().getPrimaryKey());
		} else {
			throw new EvitaInvalidUsageException(
				"Unsupported entity type `" + entity.getClass() + "`! The class doesn't implement EntityClassifier nor represents a SealedEntityProxy!",
				"Unsupported entity type!"
			);
		}
	}

	/**
	 * Method processes all local mutations of passed `entityMutation` using passed `changeCollector`
	 * and `entityIndexUpdater`.
	 */
	private static void processMutations(
		@Nonnull Collection<? extends LocalMutation<?, ?>> localMutations,
		@Nonnull LocalMutationExecutor... mutationApplicators
	) {
		try {

			do {
				for (LocalMutation<?, ?> localMutation : localMutations) {
					for (LocalMutationExecutor mutationApplicator : mutationApplicators) {
						mutationApplicator.applyMutation(localMutation);
					}
				}

				localMutations = Arrays.stream(mutationApplicators)
					.flatMap(it -> it.popImplicitMutations().stream())
					.toList();
			} while (!localMutations.isEmpty());

		} catch (RuntimeException ex) {
			// rollback all changes in memory if anything failed
			// the exception might be caught by a caller and he could continue with the transaction ... in this case
			// we need to clean partially applied changes in his isolated indexes so that he doesn't see them
			// each operation must behave atomically - either all local mutations are applied or none of them - it's
			// something like a transaction within a transaction
			for (LocalMutationExecutor mutationApplicator : mutationApplicators) {
				try {
					mutationApplicator.rollback();
				} catch (RuntimeException rollbackEx) {
					ex.addSuppressed(rollbackEx);
				}
			}
			throw ex;
		}

		// we do not address the situation where only one applicator fails on commit and the others succeed
		// this is unlikely situation and should cause entire transaction to be rolled back
		try {
			for (LocalMutationExecutor mutationApplicator : mutationApplicators) {
				mutationApplicator.commit();
			}
		} catch (RuntimeException ex) {
			throw new TransactionException("Failed to commit local mutations!", ex);
		}
	}

	public EntityCollection(
		@Nonnull String catalogName,
		long catalogVersion,
		@Nonnull CatalogState catalogState,
		int entityTypePrimaryKey,
		@Nonnull String entityType,
		@Nonnull CatalogPersistenceService catalogPersistenceService,
		@Nonnull CacheSupervisor cacheSupervisor,
		@Nonnull SequenceService sequenceService,
		@Nonnull TracingContext tracingContext
	) {
		this.tracingContext = tracingContext;
		this.entityType = entityType;
		this.entityTypePrimaryKey = entityTypePrimaryKey;
		this.catalogPersistenceService = catalogPersistenceService;
		final EntityCollectionPersistenceService entityCollectionPersistenceService = catalogPersistenceService.getOrCreateEntityCollectionPersistenceService(
			catalogVersion, entityType, entityTypePrimaryKey
		);
		this.persistenceService = entityCollectionPersistenceService;
		this.cacheSupervisor = cacheSupervisor;

		final EntityCollectionHeader entityHeader = entityCollectionPersistenceService.getEntityCollectionHeader();
		this.pkSequence = sequenceService.getOrCreateSequence(
			catalogName, SequenceType.ENTITY, entityType, entityHeader.lastPrimaryKey()
		);
		this.indexPkSequence = sequenceService.getOrCreateSequence(
			catalogName, SequenceType.INDEX, entityType, entityHeader.lastEntityIndexPrimaryKey()
		);

		// initialize container buffer
		final StoragePartPersistenceService storagePartPersistenceService = this.persistenceService.getStoragePartPersistenceService();
		this.dataStoreBuffer = catalogState == CatalogState.WARMING_UP ?
			new WarmUpDataStoreMemoryBuffer<>(storagePartPersistenceService) :
			new TransactionalDataStoreMemoryBuffer<>(this, storagePartPersistenceService);
		// initialize schema - still in constructor
		this.initialSchema = ofNullable(storagePartPersistenceService.getStoragePart(catalogVersion, 1, EntitySchemaStoragePart.class))
			.map(EntitySchemaStoragePart::entitySchema)
			.orElseGet(() -> {
				if (this.persistenceService.isNew()) {
					final EntitySchema newEntitySchema = EntitySchema._internalBuild(entityType);
					this.dataStoreBuffer.update(catalogVersion, new EntitySchemaStoragePart(newEntitySchema));
					return newEntitySchema;
				} else {
					throw new SchemaNotFoundException(catalog.getName(), entityHeader.entityType());
				}
			});
		// init entity indexes
		if (entityHeader.globalEntityIndexId() == null) {
			Assert.isPremiseValid(
				entityHeader.usedEntityIndexIds().isEmpty(),
				"Unexpected situation - global index doesn't exist but there are " +
					entityHeader.usedEntityIndexIds().size() + " reduced indexes!"
			);
			this.indexes = new TransactionalMap<>(
				new HashMap<>(),
				it -> (EntityIndex) it
			);
		} else {
			this.indexes = loadIndexes(catalogVersion, entityHeader);
		}
		// sanity check whether we deserialized the file offset index we expect to
		Assert.isTrue(
			entityHeader.entityType().equals(this.initialSchema.getName()),
			() -> "Deserialized schema name differs from expected entity type - expected " + entityHeader.entityType() + " got " + this.initialSchema.getName()
		);
		this.emptyOnStart = this.persistenceService.isEmpty(catalogVersion, dataStoreBuffer);
	}

	private EntityCollection(
		long catalogVersion,
		@Nonnull CatalogState catalogState,
		int entityTypePrimaryKey,
		@Nonnull EntitySchema entitySchema,
		@Nonnull AtomicInteger pkSequence,
		@Nonnull AtomicInteger indexPkSequence,
		@Nonnull CatalogPersistenceService catalogPersistenceService,
		@Nonnull EntityCollectionPersistenceService persistenceService,
		@Nonnull Map<EntityIndexKey, EntityIndex> indexes,
		@Nonnull CacheSupervisor cacheSupervisor,
		@Nonnull TracingContext tracingContext
	) {
		this.tracingContext = tracingContext;
		this.entityType = entitySchema.getName();
		this.entityTypePrimaryKey = entityTypePrimaryKey;
		this.initialSchema = entitySchema;
		this.pkSequence = pkSequence;
		this.catalogPersistenceService = catalogPersistenceService;
		this.persistenceService = persistenceService;
		this.indexPkSequence = indexPkSequence;
		this.dataStoreBuffer = catalogState == CatalogState.WARMING_UP ?
			new WarmUpDataStoreMemoryBuffer<>(persistenceService.getStoragePartPersistenceService()) :
			new TransactionalDataStoreMemoryBuffer<>(this, persistenceService.getStoragePartPersistenceService());
		this.indexes = new TransactionalMap<>(indexes, it -> (EntityIndex) it);
		this.cacheSupervisor = cacheSupervisor;
		this.emptyOnStart = this.persistenceService.isEmpty(catalogVersion, dataStoreBuffer);
	}

	@Override
	@Nonnull
	public <S extends Serializable, T extends EvitaResponse<S>> T getEntities(@Nonnull EvitaRequest evitaRequest, @Nonnull EvitaSessionContract session) {
		final QueryPlanningContext queryContext = createQueryContext(evitaRequest, session);
		final QueryPlan queryPlan = QueryPlanner.planQuery(queryContext);

		return tracingContext.executeWithinBlockIfParentContextAvailable(
			"query - " + queryPlan.getDescription(),
			(Supplier<T>) queryPlan::execute,
			queryPlan::getSpanAttributes
		);
	}

	@Override
	@Nonnull
	public Optional<BinaryEntity> getBinaryEntity(int primaryKey, @Nonnull EvitaRequest evitaRequest, @Nonnull EvitaSessionContract session) {
		final long catalogVersion = catalog.getVersion();
		final Optional<BinaryEntity> entity = cacheSupervisor.analyse(
				session,
				primaryKey,
				getSchema().getName(),
				evitaRequest.getEntityRequirement(),
				() -> {
					final BinaryEntityWithFetchCount binaryEntityWithFetchCount = this.persistenceService.readBinaryEntity(
						catalogVersion,
						primaryKey,
						evitaRequest,
						session,
						entityType -> entityType.equals(getEntityType()) ?
							this : catalog.getCollectionForEntityOrThrowException(entityType),
						dataStoreBuffer
					);
					return binaryEntityWithFetchCount == null ?
						null :
						new ServerBinaryEntityDecorator(
							binaryEntityWithFetchCount.entity(),
							binaryEntityWithFetchCount.ioFetchCount(),
							binaryEntityWithFetchCount.ioFetchedBytes()
						);
				},
				binaryEntity -> {
					final BinaryEntityWithFetchCount binaryEntityWithFetchCount = this.persistenceService.enrichEntity(
						catalogVersion,
						getInternalSchema(),
						binaryEntity,
						evitaRequest,
						dataStoreBuffer
					);
					return new ServerBinaryEntityDecorator(
						binaryEntityWithFetchCount.entity(),
						binaryEntity.getIoFetchCount() + binaryEntityWithFetchCount.ioFetchCount(),
						binaryEntity.getIoFetchedBytes() + binaryEntityWithFetchCount.ioFetchedBytes()
					);
				}
			)
			.map(it -> it);
		return entity.map(it -> limitEntity(it, evitaRequest.getEntityRequirement()));
	}

	@Nonnull
	@Override
	public List<BinaryEntity> getBinaryEntities(@Nonnull int[] primaryKeys, @Nonnull EvitaRequest evitaRequest, @Nonnull EvitaSessionContract session) {
		return Arrays.stream(primaryKeys)
			.mapToObj(it -> getBinaryEntity(it, evitaRequest, session))
			.filter(Optional::isPresent)
			.map(Optional::get)
			.toList();
	}

	@Override
	@Nonnull
	public Optional<SealedEntity> getEntity(int primaryKey, @Nonnull EvitaRequest evitaRequest, @Nonnull EvitaSessionContract session) {
		final ReferenceFetcher referenceFetcher = createReferenceFetcher(evitaRequest, session);
		return getEntity(primaryKey, evitaRequest, session, referenceFetcher);
	}

	@Nonnull
	@Override
	public List<SealedEntity> getEntities(@Nonnull int[] primaryKeys, @Nonnull EvitaRequest evitaRequest, @Nonnull EvitaSessionContract session) {
		final ReferenceFetcher referenceFetcher = createReferenceFetcher(evitaRequest, session);
		return getEntities(primaryKeys, evitaRequest, session, referenceFetcher);
	}

	@Override
	@Nonnull
	public ServerEntityDecorator enrichEntity(@Nonnull EntityContract entity, @Nonnull EvitaRequest evitaRequest, @Nonnull EvitaSessionContract session) {
		final Map<String, RequirementContext> referenceEntityFetch = evitaRequest.getReferenceEntityFetch();
		final ReferenceFetcher referenceFetcher;
		final QueryPlanningContext queryContext = createQueryContext(evitaRequest, session);
		referenceFetcher = referenceEntityFetch.isEmpty() &&
			!evitaRequest.isRequiresEntityReferences() &&
			!evitaRequest.isRequiresParent() ?
			ReferenceFetcher.NO_IMPLEMENTATION :
			new ReferencedEntityFetcher(
				evitaRequest.getHierarchyContent(),
				referenceEntityFetch,
				evitaRequest.getDefaultReferenceRequirement(),
				queryContext.createExecutionContext(),
				entity
			);

		return applyReferenceFetcher(
			enrichEntity(entity, evitaRequest),
			referenceFetcher
		);
	}

	@Override
	@Nonnull
	public SealedEntity limitEntity(@Nonnull EntityContract entity, @Nonnull EvitaRequest evitaRequest, @Nonnull EvitaSessionContract session) {
		final ServerEntityDecorator widerEntity = (ServerEntityDecorator) entity;
		final LocaleSerializablePredicate newLocalePredicate = new LocaleSerializablePredicate(evitaRequest, widerEntity.getLocalePredicate());
		final HierarchySerializablePredicate newHierarchyPredicate = new HierarchySerializablePredicate(evitaRequest, widerEntity.getHierarchyPredicate());
		final AttributeValueSerializablePredicate newAttributePredicate = new AttributeValueSerializablePredicate(evitaRequest, widerEntity.getAttributePredicate());
		final AssociatedDataValueSerializablePredicate newAssociatedDataPredicate = new AssociatedDataValueSerializablePredicate(evitaRequest, widerEntity.getAssociatedDataPredicate());
		final ReferenceContractSerializablePredicate newReferenceContractPredicate = new ReferenceContractSerializablePredicate(evitaRequest, widerEntity.getReferencePredicate());
		final PriceContractSerializablePredicate newPricePredicate = new PriceContractSerializablePredicate(evitaRequest, widerEntity.getPricePredicate());
		return ServerEntityDecorator.decorate(
			widerEntity.getDelegate(),
			// use original schema
			getInternalSchema(),
			// show / hide parent entity
			widerEntity.parentAvailable() && evitaRequest.isRequiresParent() ?
				widerEntity.getParentEntity().orElse(null) : null,
			// show / hide locales the entity is fetched in
			newLocalePredicate,
			// show / hide parent information
			newHierarchyPredicate,
			// show / hide attributes information
			newAttributePredicate,
			// show / hide associated data information
			newAssociatedDataPredicate,
			// show / hide references information
			newReferenceContractPredicate,
			// show / hide price information
			newPricePredicate,
			// propagate original date time
			widerEntity.getAlignedNow(),
			// propagate original I/O fetch count
			widerEntity.getIoFetchCount(),
			// propagate original I/O fetched bytes
			widerEntity.getIoFetchedBytes()
		);
	}

	@Nonnull
	@Override
	public EntityBuilder createNewEntity() {
		return new InitialEntityBuilder(getSchema(), null);
	}

	@Nonnull
	@Override
	public EntityBuilder createNewEntity(int primaryKey) {
		return new InitialEntityBuilder(getSchema(), primaryKey);
	}

	@Override
	@Nonnull
	public EntityReference upsertEntity(@Nonnull EntityMutation entityMutation) throws InvalidMutationException {
		final int primaryKey = upsertEntityInternal(entityMutation);
		return new EntityReference(
			entityMutation.getEntityType(),
			primaryKey
		);
	}

	@Override
	@Nonnull
	public SealedEntity upsertAndFetchEntity(@Nonnull EntityMutation entityMutation, @Nonnull EvitaRequest evitaRequest, @Nonnull EvitaSessionContract session) {
		final int primaryKey = upsertEntityInternal(entityMutation);
		final EntityWithFetchCount internalEntity = getEntityById(primaryKey, evitaRequest);
		Assert.isPremiseValid(internalEntity != null, "Entity that has been just upserted is unexpectedly not found!");
		final ReferenceFetcher referenceFetcher = createReferenceFetcher(evitaRequest, session);
		return applyReferenceFetcher(
			wrapToDecorator(evitaRequest, internalEntity, false),
			referenceFetcher
		);
	}

	@Override
	public boolean deleteEntity(int primaryKey) {
		// fetch the entire entity from the data store
		final EntityWithFetchCount entityToRemove = getFullEntityById(primaryKey);
		if (entityToRemove == null) {
			return false;
		}

		internalDeleteEntity(entityToRemove.entity());
		return true;
	}

	@Override
	@Nonnull
	public <T extends Serializable> Optional<T> deleteEntity(@Nonnull EvitaRequest evitaRequest, @Nonnull EvitaSessionContract session) {
		final int[] primaryKeys = evitaRequest.getPrimaryKeys();
		Assert.isTrue(primaryKeys.length == 1, "Expected exactly one primary key to delete!");
		// fetch entire entity from the data store
		final EntityWithFetchCount entityToRemove = getFullEntityById(primaryKeys[0]);
		if (entityToRemove == null) {
			return empty();
		}

		internalDeleteEntity(entityToRemove.entity());
		final ReferenceFetcher referenceFetcher = createReferenceFetcher(evitaRequest, session);
		//noinspection unchecked
		return of(
			(T) applyReferenceFetcher(
				wrapToDecorator(evitaRequest, entityToRemove, false),
				referenceFetcher
			)
		);
	}

	@Override
	public int deleteEntityAndItsHierarchy(int primaryKey, @Nonnull EvitaSessionContract session) {
		return deleteEntityAndItsHierarchy(
			new EvitaRequest(
				Query.query(
					collection(getSchema().getName()),
					filterBy(entityPrimaryKeyInSet(primaryKey)),
					require(entityFetchAll())
				),
				OffsetDateTime.now(),
				EntityReference.class,
				null,
				EvitaRequest.CONVERSION_NOT_SUPPORTED
			),
			session
		).deletedEntities();
	}

	@Override
	public <T extends Serializable> DeletedHierarchy<T> deleteEntityAndItsHierarchy(@Nonnull EvitaRequest evitaRequest, @Nonnull EvitaSessionContract session) {
		final EntityIndex globalIndex = getIndexByKeyIfExists(new EntityIndexKey(EntityIndexType.GLOBAL));
		if (globalIndex != null) {
			final int[] primaryKeys = evitaRequest.getPrimaryKeys();
			Assert.isTrue(primaryKeys.length == 1, "Expected exactly one primary key to delete!");
			final int[] entityHierarchy = globalIndex.listHierarchyNodesFromParentIncludingItself(primaryKeys[0]).getArray();
			final List<EntityWithFetchCount> entitiesToRemove = Arrays.stream(entityHierarchy)
				.mapToObj(this::getFullEntityById)
				.filter(Objects::nonNull)
				.toList();
			final IntMap<EntityWithFetchCount> entitiesToRemoveMap = new IntMap<>(entitiesToRemove.size());
			for (EntityWithFetchCount entityWithFetchCount : entitiesToRemove) {
				entitiesToRemoveMap.put(entityWithFetchCount.entity().getPrimaryKey(), entityWithFetchCount);
			}

			final ReferenceFetcher referenceFetcher = createReferenceFetcher(evitaRequest, session);
			final List<Entity> removedEntities = referenceFetcher.initReferenceIndex(
				entitiesToRemove.stream().map(EntityWithFetchCount::entity).toList(),
				this
			);
			for (EntityWithFetchCount entityToRemove : entitiesToRemove) {
				internalDeleteEntity(entityToRemove.entity());
			}
			//noinspection unchecked
			return new DeletedHierarchy<>(
				removedEntities.size(),
				removedEntities.stream()
					.findFirst()
					.map(it -> (T) applyReferenceFetcherInternal(
							wrapToDecorator(evitaRequest, entitiesToRemoveMap.get(it.getPrimaryKey()), false),
							referenceFetcher
						)
					)
					.orElse(null)
			);
		}
		return new DeletedHierarchy<>(0, null);
	}

	@Override
	public int deleteEntities(@Nonnull EvitaRequest evitaRequest, @Nonnull EvitaSessionContract session) {
		final QueryPlanningContext queryContext = createQueryContext(evitaRequest, session);
		final QueryPlan queryPlan = QueryPlanner.planQuery(queryContext);

		final EvitaEntityReferenceResponse result = tracingContext.executeWithinBlockIfParentContextAvailable(
			"delete - " + queryPlan.getDescription(),
			(Supplier<EvitaEntityReferenceResponse>) queryPlan::execute,
			queryPlan::getSpanAttributes
		);

		return result
			.getRecordData()
			.stream()
			.mapToInt(EntityReference::getPrimaryKey)
			.map(it -> this.deleteEntity(it) ? 1 : 0)
			.sum();
	}

	@Override
	@Nonnull
	public SealedEntity[] deleteEntitiesAndReturnThem(@Nonnull EvitaRequest evitaRequest, @Nonnull EvitaSessionContract session) {
		final QueryPlanningContext queryContext = createQueryContext(evitaRequest, session);
		final QueryPlan queryPlan = QueryPlanner.planQuery(queryContext);

		final EvitaResponse<? extends Serializable> result = tracingContext.executeWithinBlockIfParentContextAvailable(
			"delete - " + queryPlan.getDescription(),
			() -> queryPlan.execute(),
			queryPlan::getSpanAttributes
		);

		final List<EntityWithFetchCount> entitiesToRemove = result.getRecordData()
			.stream()
			.map(it -> getFullEntityById(getPrimaryKey(it)))
			.filter(Objects::nonNull)
			.toList();
		final IntMap<EntityWithFetchCount> entitiesToRemoveMap = new IntMap<>(entitiesToRemove.size());
		for (EntityWithFetchCount entityWithFetchCount : entitiesToRemove) {
			entitiesToRemoveMap.put(entityWithFetchCount.entity().getPrimaryKey(), entityWithFetchCount);
		}

		final ReferenceFetcher referenceFetcher = createReferenceFetcher(evitaRequest, session);
		final List<Entity> removedEntities = referenceFetcher.initReferenceIndex(
			entitiesToRemove.stream().map(EntityWithFetchCount::entity).toList(),
			this
		);
		for (EntityWithFetchCount entityToRemove : entitiesToRemove) {
			internalDeleteEntity(entityToRemove.entity());
		}

		return removedEntities.stream()
			.map(it -> applyReferenceFetcherInternal(
				wrapToDecorator(evitaRequest, entitiesToRemoveMap.get(it.getPrimaryKey()), false),
				referenceFetcher
			))
			.toArray(SealedEntity[]::new);

	}

	@Override
	public boolean isEmpty() {
		return this.persistenceService.isEmpty(catalog.getVersion(), dataStoreBuffer);
	}

	@Override
	public int size() {
		return this.persistenceService.countEntities(catalog.getVersion(), dataStoreBuffer);
	}

	@Override
	@Nonnull
	public SealedEntitySchema getSchema() {
		return schema.get();
	}

	@Override
	public void applyMutation(@Nonnull EntityMutation entityMutation) throws InvalidMutationException {
		if (entityMutation instanceof EntityUpsertMutation upsertMutation) {
			upsertEntityInternal(upsertMutation);
		} else if (entityMutation instanceof EntityRemoveMutation removeMutation) {
			applyMutations(
				entityMutation,
				Objects.requireNonNull(getFullEntityById(removeMutation.getEntityPrimaryKey()).entity()),
				!(removeMutation instanceof VerifiedEntityRemoveMutation)
			);
		} else {
			throw new InvalidMutationException(
				"Unexpected mutation type: " + entityMutation.getClass().getName(),
				"Unexpected mutation type."
			);
		}
	}

	@Nonnull
	@Override
	public SealedEntitySchema updateSchema(@Nonnull CatalogSchemaContract catalogSchema, @Nonnull EntitySchemaMutation... schemaMutation) throws SchemaAlteringException {
		// internal schema is expected to be produced on the server side
		final EntitySchema originalSchema = getInternalSchema();
		try {
			EntitySchemaContract updatedSchema = originalSchema;
			for (EntitySchemaMutation theMutation : schemaMutation) {
				updatedSchema = theMutation.mutate(catalogSchema, updatedSchema);
				/* TOBEDONE #409 JNO - this should be diverted to separate class and handle all necessary DDL operations */
				if (theMutation instanceof SetEntitySchemaWithHierarchyMutation setHierarchy) {
					if (setHierarchy.isWithHierarchy()) {
						getGlobalIndexIfExists()
							.ifPresent(it -> it.initRootNodes(it.getAllPrimaryKeys()));
					}
				}
			}

			final EntitySchemaContract nextSchema = updatedSchema;
			Assert.isPremiseValid(updatedSchema != null, "Entity collection cannot be dropped by updating schema!");
			Assert.isPremiseValid(updatedSchema instanceof EntitySchema, "Mutation is expected to produce EntitySchema instance!");
			if (updatedSchema.version() > originalSchema.version()) {
				/* TOBEDONE JNO (#501) - apply this just before commit happens in case validations are enabled */
				// assertAllReferencedEntitiesExist(newSchema);
				// assertReferences(newSchema);
				final EntitySchema updatedInternalSchema = (EntitySchema) updatedSchema;
				final EntitySchemaDecorator originalSchemaBeforeExchange = this.schema.compareAndExchange(
					this.schema.get(),
					new EntitySchemaDecorator(() -> this.catalog.getSchema(), updatedInternalSchema)
				);
				Assert.isTrue(
					originalSchemaBeforeExchange.version() == originalSchema.version(),
					() -> new ConcurrentSchemaUpdateException(originalSchema, nextSchema)
				);
			}
		} catch (RuntimeException ex) {
			// revert all changes in the schema (for current transaction) if anything failed
			final EntitySchemaDecorator decorator = new EntitySchemaDecorator(() -> this.catalog.getSchema(), originalSchema);
			this.schema.set(decorator);
			throw ex;
		} finally {
			// finally, store the updated catalog schema to disk
			final EntitySchema updatedInternalSchema = getInternalSchema();
			this.dataStoreBuffer.update(this.catalog.getVersion(), new EntitySchemaStoragePart(updatedInternalSchema));
		}

		final SealedEntitySchema schemaResult = getSchema();
		this.catalog.entitySchemaUpdated(schemaResult);
		return schemaResult;
	}

	@Override
	public long getVersion() {
		return this.persistenceService.getEntityCollectionHeader().version();
	}

	@Override
	public void terminate() {
		Assert.isTrue(
			this.terminated.compareAndSet(false, true),
			"Collection was already terminated!"
		);
		Assert.isPremiseValid(
			!Transaction.isTransactionAvailable(),
			"Entity collection cannot be terminated within transaction!"
		);
		this.persistenceService.close();
	}

	@Nonnull
	public Optional<SealedEntity> getEntity(int primaryKey, @Nonnull EvitaRequest evitaRequest, @Nonnull EvitaSessionContract session, @Nonnull ReferenceFetcher referenceFetcher) {
		// retrieve current version of entity
		return getEntityDecorator(primaryKey, evitaRequest, session)
			.map(it -> limitEntity(it, evitaRequest, session))
			.map(it -> applyReferenceFetcher(it, referenceFetcher));
	}

	@Nonnull
	public List<SealedEntity> getEntities(@Nonnull int[] primaryKeys, @Nonnull EvitaRequest evitaRequest, @Nonnull EvitaSessionContract session, @Nonnull ReferenceFetcher referenceFetcher) {
		// retrieve current version of entity
		final List<ServerEntityDecorator> entityDecorators = Arrays.stream(primaryKeys)
			.mapToObj(it -> getEntityDecorator(it, evitaRequest, session))
			.filter(Optional::isPresent)
			.map(Optional::get)
			.toList();
		return applyReferenceFetcher(
			entityDecorators.stream().map(it -> limitEntity(it, evitaRequest, session)).toList(),
			referenceFetcher
		);
	}

	@Nonnull
	public Optional<ServerEntityDecorator> getEntityDecorator(int primaryKey, @Nonnull EvitaRequest evitaRequest, @Nonnull EvitaSessionContract session) {
		return cacheSupervisor.analyse(
			session,
			primaryKey,
			getSchema().getName(),
			evitaRequest.getAlignedNow(),
			evitaRequest.getEntityRequirement(),
			() -> {
				final EntityWithFetchCount internalEntity = getEntityById(primaryKey, evitaRequest);
				if (internalEntity == null) {
					return null;
				} else if (
					!ofNullable(evitaRequest.getRequiredOrImplicitLocale())
						.map(it -> internalEntity.entity().getLocales().contains(it))
						.orElse(true)
				) {
					return null;
				} else {
					return wrapToDecorator(evitaRequest, internalEntity, null);
				}
			},
			theEntity -> enrichEntity(theEntity, evitaRequest)
		);
	}

	@Nonnull
	public ServerEntityDecorator enrichEntity(
		@Nonnull EntityContract sealedEntity,
		@Nonnull EvitaRequest evitaRequest
	) throws EntityAlreadyRemovedException {
		final ServerEntityDecorator partiallyLoadedEntity = (ServerEntityDecorator) sealedEntity;
		// return decorator that hides information not requested by original query
		final LocaleSerializablePredicate newLocalePredicate = partiallyLoadedEntity.createLocalePredicateRicherCopyWith(evitaRequest);
		final HierarchySerializablePredicate newHierarchyPredicate = partiallyLoadedEntity.createHierarchyPredicateRicherCopyWith(evitaRequest);
		final AttributeValueSerializablePredicate newAttributePredicate = partiallyLoadedEntity.createAttributePredicateRicherCopyWith(evitaRequest);
		final AssociatedDataValueSerializablePredicate newAssociatedDataPredicate = partiallyLoadedEntity.createAssociatedDataPredicateRicherCopyWith(evitaRequest);
		final ReferenceContractSerializablePredicate newReferenceContractPredicate = partiallyLoadedEntity.createReferencePredicateRicherCopyWith(evitaRequest);
		final PriceContractSerializablePredicate newPriceContractPredicate = partiallyLoadedEntity.createPricePredicateRicherCopyWith(evitaRequest);
		final EntitySchema internalSchema = getInternalSchema();

		final EntityWithFetchCount entityWithFetchCount = this.persistenceService.enrichEntity(
			this.catalog.getVersion(),
			internalSchema,
			// use all data from existing entity
			partiallyLoadedEntity,
			newHierarchyPredicate,
			newAttributePredicate,
			newAssociatedDataPredicate,
			newReferenceContractPredicate,
			newPriceContractPredicate,
			dataStoreBuffer
		);
		return ServerEntityDecorator.decorate(
			// load all missing data according to current evita request
			entityWithFetchCount.entity(),
			// use original schema
			internalSchema,
			// fetch parents if requested
			null,
			// show / hide locales the entity is fetched in
			newLocalePredicate,
			// show / hide parent information
			newHierarchyPredicate,
			// show / hide attributes information
			newAttributePredicate,
			// show / hide associated data information
			newAssociatedDataPredicate,
			// show / hide references information
			newReferenceContractPredicate,
			// show / hide price information
			newPriceContractPredicate,
			// propagate original date time
			partiallyLoadedEntity.getAlignedNow(),
			// propagate information about I/O fetch count
			entityWithFetchCount.ioFetchCount(),
			// propagate information about I/O fetched bytes
			entityWithFetchCount.ioFetchedBytes(),
			// recursive entity loader
			ReferenceFetcher.NO_IMPLEMENTATION
		);
	}

	@Nonnull
	public ServerEntityDecorator applyReferenceFetcher(
		@Nonnull SealedEntity sealedEntity,
		@Nonnull ReferenceFetcher referenceFetcher
	) throws EntityAlreadyRemovedException {
		if (referenceFetcher == ReferenceFetcher.NO_IMPLEMENTATION) {
			return (ServerEntityDecorator) sealedEntity;
		} else {
			referenceFetcher.initReferenceIndex(sealedEntity, this);
			return applyReferenceFetcherInternal((ServerEntityDecorator) sealedEntity, referenceFetcher);
		}
	}

	@Nonnull
	public List<SealedEntity> applyReferenceFetcher(
		@Nonnull List<SealedEntity> sealedEntities,
		@Nonnull ReferenceFetcher referenceFetcher
	) throws EntityAlreadyRemovedException {
		if (referenceFetcher == ReferenceFetcher.NO_IMPLEMENTATION) {
			return sealedEntities;
		} else {
			referenceFetcher.initReferenceIndex(sealedEntities, this);
			return sealedEntities.stream()
				.map(it -> applyReferenceFetcherInternal((ServerEntityDecorator) it, referenceFetcher))
				.map(SealedEntity.class::cast)
				.toList();
		}
	}

	/**
	 * Method checks whether the entity is {@link ServerEntityDecorator} and verifies the decorator wraps an entity with
	 * fetched reference storage part. If it does not, new entity decorator instance is created wrapping the same entity
	 * with the reference container fetched. New instance will share same predicates to that the methods of
	 * the decorator will produce the same output as the original entity decorator in the input of this method.
	 *
	 * The caller will be able to unwrap the decorator using {@link ServerEntityDecorator#getDelegate()} and access
	 * reference data in the wrapped entity instance. This is necessary for proper operation
	 * of {@link ReferencedEntityFetcher} implementation.
	 */
	@Nonnull
	public <T extends SealedEntity> T ensureReferencesFetched(@Nonnull T entity)
		throws EntityAlreadyRemovedException {
		if (entity instanceof ServerEntityDecorator partiallyLoadedEntity) {
			if (partiallyLoadedEntity.getReferencePredicate().isRequiresEntityReferences()) {
				// if the references are already available, return the input decorator without change
				return entity;
			} else {
				// if they were not fetched, re-wrap current decorator around entity with fetched references
				// no predicates are changed in the output decorator, only inner entity is more rich
				final EntityWithFetchCount entityWithFetchCount = this.persistenceService.enrichEntity(
					this.catalog.getVersion(),
					getInternalSchema(),
					// use all data from existing entity
					partiallyLoadedEntity,
					partiallyLoadedEntity.getHierarchyPredicate(),
					partiallyLoadedEntity.getAttributePredicate(),
					partiallyLoadedEntity.getAssociatedDataPredicate(),
					new ReferenceContractSerializablePredicate(true),
					partiallyLoadedEntity.getPricePredicate(),
					this.dataStoreBuffer
				);
				//noinspection unchecked
				return (T) ServerEntityDecorator.decorate(
					// load references if missing
					entityWithFetchCount.entity(),
					// use original schema
					getInternalSchema(),
					// fetch parents if requested
					partiallyLoadedEntity.parentAvailable() ?
						partiallyLoadedEntity.getParentEntity().orElse(null) : null,
					// show / hide locales the entity is fetched in
					partiallyLoadedEntity.getLocalePredicate(),
					// show / hide parent the entity is fetched with
					partiallyLoadedEntity.getHierarchyPredicate(),
					// show / hide attributes information
					partiallyLoadedEntity.getAttributePredicate(),
					// show / hide associated data information
					partiallyLoadedEntity.getAssociatedDataPredicate(),
					// show / hide references information
					partiallyLoadedEntity.getReferencePredicate(),
					// show / hide price information
					partiallyLoadedEntity.getPricePredicate(),
					// propagate original date time
					partiallyLoadedEntity.getAlignedNow(),
					// propagate information about I/O fetch count
					entityWithFetchCount.ioFetchCount(),
					// propagate information about I/O fetched bytes
					entityWithFetchCount.ioFetchedBytes(),
					// recursive entity loader
					ReferenceFetcher.NO_IMPLEMENTATION
				);
			}
		} else {
			// cannot execute enrichment for non-decorator entity
			// we cannot extract the information, whether the entity has no references whatsoever, or they just were not fetched
			return entity;
		}
	}

	/**
	 * Returns internally held {@link EntitySchema}.
	 */
	public EntitySchema getInternalSchema() {
		return this.schema == null ? this.initialSchema : this.schema.get().getDelegate();
	}

	/**
	 * Flush operation persists immediately all information kept in non-transactional buffers to the disk.
	 * {@link CatalogPersistenceService} is fully synced with the disk file and will not contain any non-persisted data. Flush operation
	 * is ignored when there are no changes present in {@link CatalogPersistenceService}.
	 */
	@Nonnull
	public EntityCollectionHeader flush() {
		this.persistenceService.flushTrappedUpdates(0L, this.dataStoreBuffer.getTrappedIndexChanges());
		return this.catalogPersistenceService.flush(
				0L,
				this.headerInfoSupplier,
				this.persistenceService.getEntityCollectionHeader(),
				this.dataStoreBuffer
			)
			.map(EntityCollectionPersistenceService::getEntityCollectionHeader)
			.orElseGet(this::getEntityCollectionHeader);
	}

	/**
	 * Returns entity index by its key. If such index doesn't exist, NULL is returned.
	 */
	@Nullable
	public EntityIndex getIndexByKeyIfExists(EntityIndexKey entityIndexKey) {
		return this.dataStoreBuffer.getIndexIfExists(entityIndexKey, this.indexes::get);
	}

	/**
	 * Method returns {@link GlobalEntityIndex} or throws an exception if it hasn't yet exist.
	 */
	@Nonnull
	public GlobalEntityIndex getGlobalIndex() {
		final EntityIndex globalIndex = getIndexByKeyIfExists(new EntityIndexKey(EntityIndexType.GLOBAL));
		Assert.isPremiseValid(globalIndex instanceof GlobalEntityIndex, "Global index not found in entity collection of `" + getSchema().getName() + "`.");
		return (GlobalEntityIndex) globalIndex;
	}

	/**
	 * Method returns {@link GlobalEntityIndex} or throws an exception if it hasn't yet exist.
	 */
	@Nonnull
	public Optional<GlobalEntityIndex> getGlobalIndexIfExists() {
		final Optional<EntityIndex> globalIndex = ofNullable(getIndexByKeyIfExists(new EntityIndexKey(EntityIndexType.GLOBAL)));
		return globalIndex.map(it -> {
				Assert.isPremiseValid(it instanceof GlobalEntityIndex, "Global index not found in entity collection of `" + getSchema().getName() + "`.");
				return ofNullable((GlobalEntityIndex) it);
			})
			.orElse(empty());
	}

	/**
	 * Method creates {@link QueryPlanningContext} that is used for read operations.
	 */
	@Nonnull
	public QueryPlanningContext createQueryContext(
		@Nonnull QueryPlanningContext queryContext,
		@Nonnull EvitaRequest evitaRequest,
		@Nonnull EvitaSessionContract session
	) {
		return new QueryPlanningContext(
			queryContext,
			this.catalog,
			this,
			session, evitaRequest,
			queryContext.getCurrentStep(),
			this.indexes,
			this.cacheSupervisor
		);
	}

	/**
	 * Method creates {@link QueryPlanningContext} that is used for read operations.
	 */
	@Nonnull
	public QueryPlanningContext createQueryContext(@Nonnull EvitaRequest evitaRequest, @Nonnull EvitaSessionContract session) {
		return new QueryPlanningContext(
			this.catalog,
			this,
			session, evitaRequest,
			evitaRequest.isQueryTelemetryRequested() ? new QueryTelemetry(QueryPhase.OVERALL) : null,
			this.indexes,
			this.cacheSupervisor
		);
	}

	/*
		TransactionalLayerProducer implementation
	 */

	@Override
	public DataStoreChanges<EntityIndexKey, EntityIndex> createLayer() {
		return new DataStoreChanges<>(
			Transaction.createTransactionalPersistenceService(
				this.persistenceService.getStoragePartPersistenceService()
			)
		);
	}

	@Override
	public void removeLayer(@Nonnull TransactionalLayerMaintainer transactionalLayer) {
		transactionalLayer.removeTransactionalMemoryLayerIfExists(this);
		this.schema.removeLayer(transactionalLayer);
		this.indexes.removeLayer(transactionalLayer);
	}

	@Nonnull
	@Override
	public EntityCollection createCopyWithMergedTransactionalMemory(@Nullable DataStoreChanges<EntityIndexKey, EntityIndex> layer, @Nonnull TransactionalLayerMaintainer transactionalLayer) {
		final long catalogVersion = this.catalog.getVersion();
		final DataStoreChanges<EntityIndexKey, EntityIndex> transactionalChanges = transactionalLayer.getTransactionalMemoryLayerIfExists(this);
		if (transactionalChanges != null) {
			// when we register all storage parts for persisting we can now release transactional memory
			transactionalLayer.removeTransactionalMemoryLayer(this);
			return new EntityCollection(
				catalogVersion,
				CatalogState.ALIVE, this.entityTypePrimaryKey,
				transactionalLayer.getStateCopyWithCommittedChanges(this.schema)
					.map(EntitySchemaDecorator::getDelegate)
					.orElse(null),
				this.pkSequence,
				this.indexPkSequence,
				this.catalogPersistenceService,
				this.catalogPersistenceService.getOrCreateEntityCollectionPersistenceService(
					catalogVersion, this.entityType, this.entityTypePrimaryKey
				),
				transactionalLayer.getStateCopyWithCommittedChanges(this.indexes),
				this.cacheSupervisor,
				this.tracingContext
			);
		} else {
			final ReferenceChanges<EntitySchemaDecorator> schemaChanges = transactionalLayer.getTransactionalMemoryLayerIfExists(this.schema);
			if (schemaChanges != null) {
				Assert.isPremiseValid(
					schemaChanges.get().version() == getSchema().version(),
					"Schema was unexpectedly modified!"
				);
				transactionalLayer.removeTransactionalMemoryLayerIfExists(this.schema);
			}
			Assert.isPremiseValid(
				transactionalLayer.getTransactionalMemoryLayerIfExists(this.indexes) == null,
				"Indexes are unexpectedly modified!"
			);
			// no changes were present - we return shallow copy
			return createCopyForNewCatalogAttachment(CatalogState.ALIVE);
		}
	}

	/**
	 * Creates a new copy of the EntityCollection object with a new persistence service.
	 *
	 * @param newPersistenceService the new persistence service to be set
	 * @return a new EntityCollection object with the updated persistence service
	 */
	@Nonnull
	public EntityCollection createCopyWithNewPersistenceService(long catalogVersion, @Nonnull CatalogState catalogState, @Nonnull EntityCollectionPersistenceService newPersistenceService) {
		final EntityCollection entityCollection = new EntityCollection(
			catalogVersion,
			catalogState,
			this.entityTypePrimaryKey,
			this.getInternalSchema(),
			this.pkSequence,
			this.indexPkSequence,
			this.catalogPersistenceService,
			newPersistenceService,
			this.indexes,
			this.cacheSupervisor,
			this.tracingContext
		);
		// the catalog remains the same here
		entityCollection.catalog = this.catalog;
		entityCollection.schema = this.schema;
		return entityCollection;
	}

	@Override
	public void attachToCatalog(@Nullable String entityType, @Nonnull Catalog catalog) {
		this.catalog = catalog;
		this.schema = new TransactionalReference<>(
			new EntitySchemaDecorator(catalog::getSchema, this.initialSchema)
		);
		for (EntityIndex entityIndex : indexes.values()) {
			if (entityIndex instanceof CatalogRelatedDataStructure<?> catalogRelatedEntityIndex) {
				catalogRelatedEntityIndex.attachToCatalog(this.entityType, this.catalog);
			}
		}
	}

	/**
	 * Creates a new copy of the Entity collection with the same state as the current one.
	 *
	 * @return a new EntityCollection object with the same state as the current one
	 */
	@Override
	@Nonnull
	public EntityCollection createCopyForNewCatalogAttachment(@Nonnull CatalogState catalogState) {
		//noinspection unchecked
		return new EntityCollection(
			this.catalog.getVersion(),
			catalogState,
			this.entityTypePrimaryKey,
			this.getInternalSchema(),
			this.pkSequence,
			this.indexPkSequence,
			this.catalogPersistenceService,
			this.persistenceService,
			this.indexes.entrySet()
				.stream()
				.collect(
					Collectors.toMap(
						Map.Entry::getKey,
						it -> it.getValue() instanceof CatalogRelatedDataStructure ?
							((CatalogRelatedDataStructure<? extends EntityIndex>) it.getValue()).createCopyForNewCatalogAttachment(catalogState) :
							it.getValue()
					)
				),
			this.cacheSupervisor,
			this.tracingContext
		);
	}

	/**
	 * Retrieves the entity collection header from the persistence service.
	 *
	 * @return the entity collection header
	 */
	EntityCollectionHeader getEntityCollectionHeader() {
		return this.persistenceService.getEntityCollectionHeader();
	}

	/**
	 * This method writes all changed storage parts into the persistent storage of this {@link EntityCollection} and
	 * then returns updated {@link EntityCollectionHeader}.
	 */
	@Nonnull
	EntityCollectionHeader flush(long catalogVersion) {
		this.persistenceService.flushTrappedUpdates(catalogVersion, this.dataStoreBuffer.getTrappedIndexChanges());
		return this.catalogPersistenceService.flush(
				catalogVersion,
				this.headerInfoSupplier,
				this.persistenceService.getEntityCollectionHeader(),
				this.dataStoreBuffer
			)
			.map(EntityCollectionPersistenceService::getEntityCollectionHeader)
			.orElseGet(this::getEntityCollectionHeader);
	}

	/*
		PRIVATE METHODS
	 */

	/**
	 * Generates new UNIQUE primary key for the entity. Calling this
	 *
	 * @return new unique primary key
	 */
	private int getNextPrimaryKey() {
		// atomic integer takes care of concurrent access and producing unique monotonic sequence of numbers
		return pkSequence.incrementAndGet();
	}

	/**
	 * Method loads all indexes mentioned in {@link EntityCollectionHeader#globalEntityIndexId()} and
	 * {@link EntityCollectionHeader#usedEntityIndexIds()} into a transactional map indexed by their
	 * {@link EntityIndex#getIndexKey()}.
	 */
	@Nonnull
	private TransactionalMap<EntityIndexKey, EntityIndex> loadIndexes(long catalogVersion, @Nonnull EntityCollectionHeader entityHeader) {
		// we need to load global index first, this is the only one index containing all data
		final GlobalEntityIndex globalIndex = (GlobalEntityIndex) this.persistenceService.readEntityIndex(
			catalogVersion,
			entityHeader.globalEntityIndexId(),
			this.initialSchema
		);
		Assert.isPremiseValid(
			globalIndex != null,
			() -> "Global index must never be null for entity type `" + this.initialSchema.getName() + "`!"
		);
		return new TransactionalMap<>(
			// now join global index with all other reduced indexes into single key-value index
			Stream.concat(
					Stream.of(globalIndex),
					entityHeader.usedEntityIndexIds()
						.stream()
						.map(eid -> this.persistenceService.readEntityIndex(catalogVersion, eid, this.initialSchema))
				)
				.collect(
					Collectors.toMap(
						EntityIndex::getIndexKey,
						Function.identity()
					)
				),
			it -> (EntityIndex) it
		);
	}

	/**
	 * Method fetches the full contents of the entity by its primary key from the I/O storage (taking advantage of
	 * modified parts in the {@link TransactionalDataStoreMemoryBuffer}.
	 */
	@Nullable
	private EntityWithFetchCount getFullEntityById(int primaryKey) {
		final EvitaRequest evitaRequest = new EvitaRequest(
			Query.query(
				collection(getSchema().getName()),
				require(entityFetchAll())
			),
			OffsetDateTime.now(),
			Entity.class,
			null,
			EvitaRequest.CONVERSION_NOT_SUPPORTED
		);
		return getEntityById(primaryKey, evitaRequest);
	}

	/**
	 * Method fetches the entity by its primary key from the I/O storage (taking advantage of modified parts in the
	 * {@link TransactionalDataStoreMemoryBuffer}).
	 */
	@Nullable
	private EntityWithFetchCount getEntityById(int primaryKey, @Nonnull EvitaRequest evitaRequest) {
		return this.persistenceService.readEntity(
			catalog.getVersion(),
			primaryKey,
			evitaRequest,
			getInternalSchema(),
			dataStoreBuffer
		);
	}

	/**
	 * Creates a new {@link ReferenceFetcher} that is able to deeply load single entity.
	 */
	@Nonnull
	private ReferenceFetcher createReferenceFetcher(
		@Nonnull EvitaRequest evitaRequest,
		@Nonnull EvitaSessionContract session
	) {
		final Map<String, RequirementContext> referenceEntityFetch = evitaRequest.getReferenceEntityFetch();
		final QueryPlanningContext queryContext = createQueryContext(evitaRequest, session);
		return referenceEntityFetch.isEmpty() &&
			!evitaRequest.isRequiresEntityReferences() &&
			!evitaRequest.isRequiresParent() ?
			ReferenceFetcher.NO_IMPLEMENTATION :
			new ReferencedEntityFetcher(
				evitaRequest.getHierarchyContent(),
				referenceEntityFetch,
				evitaRequest.getDefaultReferenceRequirement(),
				queryContext.createExecutionContext()
			);
	}

	/**
	 * Injects referenced entity bodies into the main entity.
	 *
	 * @param sealedEntity     main entity to be enriched
	 * @param referenceFetcher reference fetcher to be used for accessing referenced entities
	 * @return enriched entity
	 */
	@Nonnull
	private ServerEntityDecorator applyReferenceFetcherInternal(
		@Nonnull ServerEntityDecorator sealedEntity,
		@Nonnull ReferenceFetcher referenceFetcher
	) {
		// fetch parents if requested
		final EntityClassifierWithParent parentEntity;
		final EntitySchema internalSchema = getInternalSchema();
		if (internalSchema.isWithHierarchy() && sealedEntity.getHierarchyPredicate().isRequiresHierarchy()) {
			if (sealedEntity.getParentEntityWithoutCheckingPredicate().map(it -> it instanceof SealedEntity).orElse(false)) {
				parentEntity = sealedEntity.getParentEntityWithoutCheckingPredicate().get();
			} else {
				final OptionalInt theParent = sealedEntity.getDelegate().getParent();
				parentEntity = theParent.isPresent() ?
					ofNullable(referenceFetcher.getParentEntityFetcher())
						.map(it -> it.apply(theParent.getAsInt()))
						.orElse(null) : null;
			}
		} else {
			parentEntity = null;
		}

		return new ServerEntityDecorator(sealedEntity, parentEntity, referenceFetcher);
	}

	/**
	 * Wraps full entity into an {@link ServerEntityDecorator} that fulfills the requirements passed in input `evitaRequest`.
	 */
	@Nonnull
	private ServerEntityDecorator wrapToDecorator(
		@Nonnull EvitaRequest evitaRequest,
		@Nonnull EntityWithFetchCount fullEntityWithCount,
		@Nullable Boolean contextAvailable
	) {
		return ServerEntityDecorator.decorate(
			fullEntityWithCount.entity(),
			getInternalSchema(),
			null,
			new LocaleSerializablePredicate(evitaRequest),
			new HierarchySerializablePredicate(evitaRequest),
			new AttributeValueSerializablePredicate(evitaRequest),
			new AssociatedDataValueSerializablePredicate(evitaRequest),
			new ReferenceContractSerializablePredicate(evitaRequest),
			new PriceContractSerializablePredicate(evitaRequest, contextAvailable),
			evitaRequest.getAlignedNow(),
			fullEntityWithCount.ioFetchCount(),
			fullEntityWithCount.ioFetchedBytes(),
			ReferenceFetcher.NO_IMPLEMENTATION
		);
	}

	/**
	 * Creates or updates entity and returns its primary key.
	 */
	private int upsertEntityInternal(@Nonnull EntityMutation entityMutation) {
		// verify mutation against schema
		// it was already executed when mutation was created, but there are two reasons to do it again
		// - we don't trust clients - in future it may be some external JS application
		// - schema may have changed between entity was provided to the client and the moment upsert was called
		final SealedCatalogSchema catalogSchema = this.catalog.getSchema();
		entityMutation.verifyOrEvolveSchema(catalogSchema, getSchema(), emptyOnStart && isEmpty())
			.ifPresent(
				it -> {
					// we need to call apply mutation on the catalog level in order to insert the mutations to the WAL
					this.catalog.applyMutation(
						new ModifyEntitySchemaMutation(getEntityType(), it)
					);
				}
			);

		// check the existence of the primary key and report error when unexpectedly (not) provided
		final SealedEntitySchema currentSchema = getSchema();
		final boolean verifiedMutation = entityMutation instanceof VerifiedEntityUpsertMutation;
		final EntityMutation entityMutationToUpsert = verifiedMutation ?
			entityMutation : verifyPrimaryKeyAssignment(entityMutation, currentSchema);

		applyMutations(
			entityMutationToUpsert,
			null,
			!verifiedMutation
		);

		return entityMutationToUpsert.getEntityPrimaryKey();
	}

	/**
	 * Method checks whether entity mutation primary key is present or not, and whether it should be present or not
	 * according to the schema settings.
	 *
	 * @return primary key assigned in the mutation
	 * @throws InvalidMutationException when the expectations are not met
	 */
	@Nonnull
	private EntityMutation verifyPrimaryKeyAssignment(
		@Nonnull EntityMutation entityMutation,
		@Nonnull SealedEntitySchema currentSchema
	) throws InvalidMutationException {
		if (currentSchema.isWithGeneratedPrimaryKey()) {
			if (entityMutation instanceof EntityUpsertMutation && entityMutation.getEntityPrimaryKey() == null) {
				entityMutation = new EntityUpsertMutation(
					entityMutation.getEntityType(),
					getNextPrimaryKey(),
					entityMutation.expects(),
					entityMutation.getLocalMutations()
				);
			} else if (entityMutation.expects() == EntityExistence.MUST_NOT_EXIST) {
				throw new InvalidMutationException(
					"Entity of type `" + currentSchema.getName() +
						"` is expected to have primary key automatically generated by Evita!"
				);
			} else if (entityMutation.expects() == EntityExistence.MAY_EXIST) {
				Assert.isTrue(
					getGlobalIndex().isPrimaryKeyKnown(entityMutation.getEntityPrimaryKey()),
					() -> new InvalidMutationException(
						"Entity of type `" + currentSchema.getName() +
							"` is expected to have primary key automatically generated by Evita!"
					)
				);
			}
		} else {
			Assert.isTrue(
				entityMutation.getEntityPrimaryKey() != null,
				() -> new InvalidMutationException(
					"Entity of type " + currentSchema.getName() +
						" is expected to have primary key provided by external systems!"
				)
			);
		}
		return entityMutation;
	}

	/**
	 * Deletes passed entity both from indexes and the storage.
	 */
	private void internalDeleteEntity(@Nonnull Entity entityToRemove) {
		// construct set of removal mutations
		final int entityToRemovePrimaryKey = Objects.requireNonNull(entityToRemove.getPrimaryKey());
		final EntityRemoveMutation entityMutation = new EntityRemoveMutation(getEntityType(), entityToRemovePrimaryKey);

		applyMutations(
			entityMutation,
			entityToRemove,
			true
		);
	}

	/**
	 * Method applies all `localMutations` on entity with passed `entityPrimaryKey`.
	 *
	 * @param entityMutation entity mutation to apply
	 * @param entity         the entity to apply mutation on (needed only for entity removal)
	 */
	private void applyMutations(
		@Nonnull EntityMutation entityMutation,
		@Nullable Entity entity,
		boolean undoOnError
	) {
		// prepare collectors
		final EntityRemoveMutation entityRemoveMutation = entityMutation instanceof EntityRemoveMutation erm ? erm : null;

		final ContainerizedLocalMutationExecutor changeCollector = new ContainerizedLocalMutationExecutor(
			this.dataStoreBuffer,
			this.catalog.getVersion(),
			entityMutation.getEntityPrimaryKey(),
			entityMutation.expects(),
			this::getInternalSchema,
			entityRemoveMutation != null
		);

		final EntityIndexLocalMutationExecutor entityIndexUpdater = new EntityIndexLocalMutationExecutor(
			changeCollector,
			entityMutation.getEntityPrimaryKey(),
			this.entityIndexCreator,
			this.catalog.getCatalogIndexMaintainer(),
			this::getInternalSchema,
			entityType -> this.catalog.getCollectionForEntityOrThrowException(entityType).getInternalSchema(),
			undoOnError
		);

		final Collection<? extends LocalMutation<?, ?>> localMutations = entityRemoveMutation != null ?
			entityRemoveMutation.computeLocalMutationsForEntityRemoval(entity) : entityMutation.getLocalMutations();

		// apply mutations leading to clearing storage containers
		EntitySchemaContext.executeWithSchemaContext(
			getInternalSchema(),
			() -> processMutations(
				localMutations,
				entityIndexUpdater, changeCollector
			)
		);

		if (entityRemoveMutation != null) {
			// remove the entity itself from the indexes
			entityIndexUpdater.removeEntity(entityMutation.getEntityPrimaryKey());
		}

		// register the mutation to the write ahead log
		Transaction.getTransaction().ifPresent(it -> it.registerMutation(entityMutation));
	}

	/**
	 * Method will check whether the entity contains only the content required by `fetchRequirements` and if it contains
	 * more data than requested, new instance of {@link BinaryEntity} is created referencing only the requested set
	 * of data containers.
	 */
	@Nonnull
	private BinaryEntity limitEntity(@Nonnull BinaryEntity entity, @Nonnull EntityFetch fetchRequirements) {
		/* TOBEDONE https://gitlab.fg.cz/hv/evita/-/issues/137 */
		return entity;
	}

	/**
	 * Method verifies that all referenced entities in updated schema are actually present in catalog.
	 */
	private void assertAllReferencedEntitiesExist(@Nonnull EntitySchema newSchema) {
		Stream.concat(
				newSchema.getReferences().values().stream().filter(ReferenceSchemaContract::isReferencedEntityTypeManaged).map(ReferenceSchemaContract::getReferencedEntityType),
				newSchema.getReferences().values().stream().filter(ReferenceSchemaContract::isReferencedGroupTypeManaged).map(ReferenceSchemaContract::getReferencedGroupType)
			)
			.distinct()
			.forEach(it -> {
				Assert.isTrue(
					catalog.getCollectionForEntity(it).isPresent(),
					() -> new InvalidMutationException(
						"Entity schema `" + newSchema.getName() + "` references entity `" + it + "`," +
							" but such entity is not known in catalog `" + this.catalog.getName() + "`."
					)
				);
			});
	}

	/**
	 * Method verifies that no 0..1:N reference has sortable and non-nullable attributes.
	 */
	private void assertReferences(@Nonnull EntitySchema newSchema) {
		for (ReferenceSchemaContract referenceSchema : newSchema.getReferences().values()) {
			final Cardinality cardinality = referenceSchema.getCardinality();
			if (cardinality == Cardinality.ONE_OR_MORE || cardinality == Cardinality.ZERO_OR_MORE) {
				final String[] invalidAttributes = referenceSchema.getAttributes()
					.values()
					.stream()
					.filter(it -> it.isSortable() && !it.isNullable())
					.map(NamedSchemaContract::getName)
					.toArray(String[]::new);
				if (invalidAttributes.length > 0) {
					throw new InvalidSchemaMutationException(
						"The attribute(s) " + Arrays.stream(invalidAttributes).map(it -> "`" + it + "`").collect(Collectors.joining(", ")) +
							" in entity `" + newSchema.getName() +
							"` schema for reference with name `" + referenceSchema.getName() + "` cannot be both sortable and non-nullable if " +
							"reference cardinality is set to " + cardinality + "! The sorting wouldn't make sense."
					);
				}
			}
		}
	}

	/**
	 * This implementation just manipulates with the set of EntityIndex in entity collection.
	 */
	private class EntityIndexMaintainer implements IndexMaintainer<EntityIndexKey, EntityIndex> {

		/**
		 * Returns entity index by its key. If such index doesn't exist, it is automatically created.
		 */
		@Nonnull
		@Override
		public EntityIndex getOrCreateIndex(@Nonnull EntityIndexKey entityIndexKey) {
			return EntityCollection.this.dataStoreBuffer.getOrCreateIndexForModification(
				entityIndexKey,
				eik ->
					// if storage container buffer doesn't have index in "dirty" memory - retrieve index from collection
					EntityCollection.this.indexes.computeIfAbsent(
						eik,
						eikAgain -> {
							final EntityIndex entityIndex;
							// if index doesn't exist even there create new one
							if (eikAgain.getType() == EntityIndexType.GLOBAL) {
								entityIndex = new GlobalEntityIndex(indexPkSequence.incrementAndGet(), entityType, eikAgain);
							} else {
								final EntityIndex globalIndex = getIndexIfExists(new EntityIndexKey(EntityIndexType.GLOBAL));
								Assert.isPremiseValid(
									globalIndex instanceof GlobalEntityIndex,
									"When reduced index is created global one must already exist!"
								);
								if (eikAgain.getType() == EntityIndexType.REFERENCED_ENTITY_TYPE) {
									entityIndex = new ReferencedTypeEntityIndex(
										indexPkSequence.incrementAndGet(), entityType, eikAgain
									);
								} else {
									entityIndex = new ReducedEntityIndex(
										indexPkSequence.incrementAndGet(), entityType, eikAgain
									);
								}
							}

							if (entityIndex instanceof CatalogRelatedDataStructure<?> lateInitializationIndex) {
								lateInitializationIndex.attachToCatalog(EntityCollection.this.entityType, EntityCollection.this.catalog);
							}

							return entityIndex;
						}
					)
			);
		}

		/**
		 * Returns existing index for passed `entityIndexKey` or returns null.
		 */
		@Nullable
		@Override
		public EntityIndex getIndexIfExists(@Nonnull EntityIndexKey entityIndexKey) {
			return EntityCollection.this.getIndexByKeyIfExists(entityIndexKey);
		}

		/**
		 * Removes entity index by its key. If such index doesn't exist, exception is thrown.
		 *
		 * @throws IllegalArgumentException when entity index doesn't exist
		 */
		@Override
		public void removeIndex(@Nonnull EntityIndexKey entityIndexKey) {
			final EntityIndex removedIndex = EntityCollection.this.dataStoreBuffer.removeIndex(
				entityIndexKey, EntityCollection.this.indexes::remove
			);
			if (removedIndex == null) {
				throw new GenericEvitaInternalError("Entity index for key " + entityIndexKey + " doesn't exists!");
			} else {
				ofNullable(getTransactionalLayerMaintainer())
					.ifPresent(removedIndex::removeTransactionalMemoryOfReferencedProducers);
			}
		}

	}

	/**
	 * A private class that implements the HeaderInfoSupplier interface.
	 * It provides information about the header of an EntityCollection.
	 */
	private class EntityCollectionHeaderInfoSupplier implements HeaderInfoSupplier {

		@Override
		public int getLastAssignedPrimaryKey() {
			return EntityCollection.this.pkSequence.get();
		}

		@Override
		public int getLastAssignedIndexKey() {
			return EntityCollection.this.indexPkSequence.get();
		}

		@Nullable
		@Override
		public OptionalInt getGlobalIndexKey() {
			return ofNullable(EntityCollection.this.indexes.get(new EntityIndexKey(EntityIndexType.GLOBAL)))
				.map(it -> OptionalInt.of(it.getPrimaryKey()))
				.orElseGet(OptionalInt::empty);
		}

		@Nonnull
		@Override
		public List<Integer> getIndexKeys() {
			return EntityCollection.this.indexes
				.values()
				.stream()
				.filter(it -> it.getIndexKey().getType() != EntityIndexType.GLOBAL)
				.map(EntityIndex::getPrimaryKey)
				.collect(Collectors.toList());
		}
	}
}
