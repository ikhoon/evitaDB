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

import com.carrotsearch.hppc.ObjectObjectIdentityHashMap;
import com.carrotsearch.hppc.cursors.ObjectObjectCursor;
import io.evitadb.api.CatalogContract;
import io.evitadb.api.CatalogState;
import io.evitadb.api.EntityCollectionContract;
import io.evitadb.api.EvitaSessionContract;
import io.evitadb.api.TransactionContract.CommitBehavior;
import io.evitadb.api.configuration.EvitaConfiguration;
import io.evitadb.api.configuration.StorageOptions;
import io.evitadb.api.exception.CollectionNotFoundException;
import io.evitadb.api.exception.ConcurrentSchemaUpdateException;
import io.evitadb.api.exception.InvalidMutationException;
import io.evitadb.api.exception.InvalidSchemaMutationException;
import io.evitadb.api.exception.SchemaAlteringException;
import io.evitadb.api.exception.SchemaNotFoundException;
import io.evitadb.api.exception.TemporalDataNotAvailableException;
import io.evitadb.api.exception.TransactionException;
import io.evitadb.api.file.FileForFetch;
import io.evitadb.api.observability.trace.TracingContext;
import io.evitadb.api.proxy.ProxyFactory;
import io.evitadb.api.requestResponse.EvitaRequest;
import io.evitadb.api.requestResponse.EvitaResponse;
import io.evitadb.api.requestResponse.data.mutation.EntityMutation;
import io.evitadb.api.requestResponse.extraResult.QueryTelemetry;
import io.evitadb.api.requestResponse.extraResult.QueryTelemetry.QueryPhase;
import io.evitadb.api.requestResponse.mutation.Mutation;
import io.evitadb.api.requestResponse.schema.CatalogEvolutionMode;
import io.evitadb.api.requestResponse.schema.CatalogSchemaContract;
import io.evitadb.api.requestResponse.schema.CatalogSchemaDecorator;
import io.evitadb.api.requestResponse.schema.EntitySchemaContract;
import io.evitadb.api.requestResponse.schema.SealedCatalogSchema;
import io.evitadb.api.requestResponse.schema.SealedEntitySchema;
import io.evitadb.api.requestResponse.schema.dto.CatalogSchema;
import io.evitadb.api.requestResponse.schema.dto.EntitySchema;
import io.evitadb.api.requestResponse.schema.dto.EntitySchemaProvider;
import io.evitadb.api.requestResponse.schema.mutation.CatalogSchemaMutation;
import io.evitadb.api.requestResponse.schema.mutation.CatalogSchemaMutation.CatalogSchemaWithImpactOnEntitySchemas;
import io.evitadb.api.requestResponse.schema.mutation.LocalCatalogSchemaMutation;
import io.evitadb.api.requestResponse.schema.mutation.catalog.CreateEntitySchemaMutation;
import io.evitadb.api.requestResponse.schema.mutation.catalog.ModifyEntitySchemaMutation;
import io.evitadb.api.requestResponse.schema.mutation.catalog.ModifyEntitySchemaNameMutation;
import io.evitadb.api.requestResponse.schema.mutation.catalog.RemoveEntitySchemaMutation;
import io.evitadb.api.requestResponse.system.CatalogVersion;
import io.evitadb.api.requestResponse.system.CatalogVersionDescriptor;
import io.evitadb.api.requestResponse.system.TimeFlow;
import io.evitadb.api.requestResponse.transaction.TransactionMutation;
import io.evitadb.api.task.ServerTask;
import io.evitadb.core.async.ObservableExecutorService;
import io.evitadb.core.async.Scheduler;
import io.evitadb.core.buffer.DataStoreChanges;
import io.evitadb.core.buffer.DataStoreMemoryBuffer;
import io.evitadb.core.buffer.TransactionalDataStoreMemoryBuffer;
import io.evitadb.core.buffer.WarmUpDataStoreMemoryBuffer;
import io.evitadb.core.cache.CacheSupervisor;
import io.evitadb.core.exception.StorageImplementationNotFoundException;
import io.evitadb.core.file.ExportFileService;
import io.evitadb.core.metric.event.transaction.CatalogGoesLiveEvent;
import io.evitadb.core.query.QueryPlan;
import io.evitadb.core.query.QueryPlanner;
import io.evitadb.core.query.QueryPlanningContext;
import io.evitadb.core.query.algebra.Formula;
import io.evitadb.core.sequence.SequenceService;
import io.evitadb.core.sequence.SequenceType;
import io.evitadb.core.transaction.TransactionManager;
import io.evitadb.core.transaction.memory.TransactionalLayerMaintainer;
import io.evitadb.core.transaction.memory.TransactionalLayerProducer;
import io.evitadb.core.transaction.memory.TransactionalObjectVersion;
import io.evitadb.dataType.PaginatedList;
import io.evitadb.exception.GenericEvitaInternalError;
import io.evitadb.index.CatalogIndex;
import io.evitadb.index.CatalogIndexKey;
import io.evitadb.index.EntityIndex;
import io.evitadb.index.EntityIndexKey;
import io.evitadb.index.IndexMaintainer;
import io.evitadb.index.map.MapChanges;
import io.evitadb.index.map.TransactionalMap;
import io.evitadb.index.reference.TransactionalReference;
import io.evitadb.store.entity.model.schema.CatalogSchemaStoragePart;
import io.evitadb.store.spi.CatalogPersistenceService;
import io.evitadb.store.spi.CatalogPersistenceServiceFactory;
import io.evitadb.store.spi.CatalogStoragePartPersistenceService;
import io.evitadb.store.spi.EntityCollectionPersistenceService;
import io.evitadb.store.spi.IsolatedWalPersistenceService;
import io.evitadb.store.spi.OffHeapWithFileBackupReference;
import io.evitadb.store.spi.StoragePartPersistenceService;
import io.evitadb.store.spi.model.CatalogHeader;
import io.evitadb.store.spi.model.EntityCollectionHeader;
import io.evitadb.store.spi.model.reference.CollectionFileReference;
import io.evitadb.utils.ArrayUtils;
import io.evitadb.utils.Assert;
import io.evitadb.utils.CollectionUtils;
import io.evitadb.utils.ReflectionLookup;
import io.evitadb.utils.StringUtils;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.io.InputStream;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.evitadb.core.Transaction.isTransactionAvailable;
import static io.evitadb.utils.CollectionUtils.MAX_POWER_OF_TWO;
import static io.evitadb.utils.CollectionUtils.createHashMap;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;

/**
 * {@inheritDoc}
 *
 * @author Jan Novotný (novotny@fg.cz), FG Forrest a.s. (c) 2021
 */
@Slf4j
@ThreadSafe
public final class Catalog implements CatalogContract, CatalogVersionBeyondTheHorizonListener, TransactionalLayerProducer<DataStoreChanges<CatalogIndexKey, CatalogIndex>, Catalog> {
	@Getter private final long id = TransactionalObjectVersion.SEQUENCE.nextId();
	/**
	 * Contains information about version of the catalog which corresponds to transaction commit sequence number.
	 */
	private final TransactionalReference<Long> versionId;
	/**
	 * CatalogIndex factory implementation.
	 */
	@Getter(AccessLevel.PACKAGE)
	private final IndexMaintainer<CatalogIndexKey, CatalogIndex> catalogIndexMaintainer = new CatalogIndexMaintainerImpl();
	/**
	 * Memory store for catalogs.
	 */
	private final TransactionalMap<String, EntityCollection> entityCollections;
	/**
	 * Contains index of {@link EntityCollection} indexed by their primary keys.
	 */
	private final TransactionalMap<Integer, EntityCollection> entityCollectionsByPrimaryKey;
	/**
	 * Contains index of {@link EntitySchemaContract} indexed by their {@link EntitySchemaContract#getName()}.
	 */
	private final TransactionalMap<String, EntitySchemaContract> entitySchemaIndex;
	/**
	 * Service containing I/O related methods.
	 */
	private final CatalogPersistenceService persistenceService;
	/**
	 * This instance is used to cover changes in transactional memory and persistent storage reference.
	 *
	 * @see TransactionalDataStoreMemoryBuffer documentation
	 */
	private final DataStoreMemoryBuffer<CatalogIndexKey, CatalogIndex> dataStoreBuffer;
	/**
	 * This field contains flag with TRUE value if catalog is being switched to {@link CatalogState#ALIVE} state.
	 */
	private final AtomicBoolean goingLive = new AtomicBoolean();
	/**
	 * Formula supervisor is an entry point to the Evita cache. The idea is that each {@link Formula} can be identified by
	 * its {@link Formula#getHash()} method and when the supervisor identifies that certain formula
	 * is frequently used in query formulas it moves its memoized results to the cache. The non-computed formula
	 * of the same hash will be exchanged in next query that contains it with the cached formula that already contains
	 * memoized result.
	 */
	private final CacheSupervisor cacheSupervisor;
	/**
	 * Contains sequence that allows automatic assigning monotonic primary keys to the entity collections.
	 */
	private final AtomicInteger entityTypeSequence;
	/**
	 * Contains catalog configuration.
	 */
	private final TransactionalReference<CatalogSchemaDecorator> schema;
	/**
	 * Contains unique catalog id that doesn't change with catalog schema changes - such as renaming.
	 * The id is assigned to the catalog when it is created and never changes.
	 */
	@Nonnull @Getter
	private final UUID catalogId;
	/**
	 * Indicates state in which Catalog operates.
	 *
	 * @see CatalogState
	 */
	private final CatalogState state;
	/**
	 * Contains reference to the main catalog index that allows fast lookups for entities across all types.
	 */
	private final CatalogIndex catalogIndex;
	/**
	 * Isolated sequence service for this catalog.
	 */
	private final SequenceService sequenceService = new SequenceService();
	/**
	 * Contains reference to the proxy factory that is used to create proxies for the entities.
	 */
	@Getter private final ProxyFactory proxyFactory;
	/**
	 * Reference to the current {@link EvitaConfiguration} settings.
	 */
	private final EvitaConfiguration evitaConfiguration;
	/**
	 * Reference to the shared transactional executor service that provides carrier threads for transaction processing.
	 */
	private final ObservableExecutorService transactionalExecutor;
	/**
	 * Reference to the shared executor service that provides carrier threads for transaction processing.
	 */
	private final Scheduler scheduler;
	/**
	 * Callback function that allows to propagate reference to the new catalog version to the {@link Evita}
	 * instance that is referring to the current version of the catalog.
	 */
	private final Consumer<Catalog> newCatalogVersionConsumer;
	/**
	 * Provides access to the entity schema in the catalog.
	 */
	@Getter private final CatalogEntitySchemaAccessor entitySchemaAccessor = new CatalogEntitySchemaAccessor();
	/**
	 * Provides the tracing context for tracking the execution flow in the application.
	 **/
	private final TracingContext tracingContext;
	/**
	 * Transaction manager used for processing the transactions.
	 */
	private final TransactionManager transactionManager;
	/**
	 * Last persisted schema version of the catalog.
	 */
	private long lastPersistedSchemaVersion;

	/**
	 * Verifies whether the catalog name could be used for a new catalog.
	 *
	 * @param catalogName        the name of the catalog
	 * @param storageOptions     the storage options
	 * @param totalBytesExpected the total bytes expected to be read from the input stream
	 * @param inputStream        the input stream with the catalog data
	 * @return future that will be completed with path where the content of the catalog was restored
	 */
	public static ServerTask<?, Void> createRestoreCatalogTask(
		@Nonnull String catalogName,
		@Nonnull StorageOptions storageOptions,
		long totalBytesExpected,
		@Nonnull InputStream inputStream
	) {
		return ServiceLoader.load(CatalogPersistenceServiceFactory.class)
			.findFirst()
			.map(it -> it.restoreCatalogTo(catalogName, storageOptions, totalBytesExpected, inputStream))
			.orElseThrow(() -> new IllegalStateException("IO service is unexpectedly not available!"));
	}

	public Catalog(
		@Nonnull CatalogSchemaContract catalogSchema,
		@Nonnull CacheSupervisor cacheSupervisor,
		@Nonnull EvitaConfiguration evitaConfiguration,
		@Nonnull ReflectionLookup reflectionLookup,
		@Nonnull Scheduler scheduler,
		@Nonnull ExportFileService exportFileService,
		@Nonnull ObservableExecutorService transactionalExecutor,
		@Nonnull Consumer<Catalog> newCatalogVersionConsumer,
		@Nonnull TracingContext tracingContext
	) {
		final String catalogName = catalogSchema.getName();
		final long catalogVersion = 0L;

		this.tracingContext = tracingContext;
		final CatalogSchema internalCatalogSchema = CatalogSchema._internalBuild(
			catalogName, catalogSchema.getNameVariants(), catalogSchema.getCatalogEvolutionMode(),
			getEntitySchemaAccessor()
		);
		this.schema = new TransactionalReference<>(new CatalogSchemaDecorator(internalCatalogSchema));
		this.persistenceService = ServiceLoader.load(CatalogPersistenceServiceFactory.class)
			.findFirst()
			.map(
				it -> it.createNew(
					this, this.getSchema().getName(),
					evitaConfiguration.storage(),
					evitaConfiguration.transaction(),
					scheduler,
					exportFileService
				)
			)
			.orElseThrow(StorageImplementationNotFoundException::new);

		this.catalogId = UUID.randomUUID();
		final CatalogStoragePartPersistenceService storagePartPersistenceService = this.persistenceService.getStoragePartPersistenceService(catalogVersion);
		storagePartPersistenceService.putStoragePart(catalogVersion, new CatalogSchemaStoragePart(getInternalSchema()));

		// initialize container buffer
		this.state = CatalogState.WARMING_UP;
		this.versionId = new TransactionalReference<>(catalogVersion);
		this.dataStoreBuffer = new WarmUpDataStoreMemoryBuffer<>(storagePartPersistenceService);
		this.cacheSupervisor = cacheSupervisor;
		this.entityCollections = new TransactionalMap<>(createHashMap(0), EntityCollection.class, Function.identity());
		this.entityCollectionsByPrimaryKey = new TransactionalMap<>(createHashMap(0), EntityCollection.class, Function.identity());
		this.entitySchemaIndex = new TransactionalMap<>(createHashMap(0));
		this.entityTypeSequence = sequenceService.getOrCreateSequence(
			catalogName, SequenceType.ENTITY_COLLECTION, 0
		);
		this.catalogIndex = new CatalogIndex();
		this.catalogIndex.attachToCatalog(null, this);
		this.proxyFactory = ProxyFactory.createInstance(reflectionLookup);
		this.evitaConfiguration = evitaConfiguration;
		this.scheduler = scheduler;
		this.transactionalExecutor = transactionalExecutor;
		this.newCatalogVersionConsumer = newCatalogVersionConsumer;
		this.lastPersistedSchemaVersion = this.schema.get().version();
		this.transactionManager = new TransactionManager(
			this, evitaConfiguration, scheduler, transactionalExecutor, newCatalogVersionConsumer
		);

		this.persistenceService.storeHeader(
			this.catalogId, CatalogState.WARMING_UP, catalogVersion, 0, null,
			Collections.emptyList(),
			this.dataStoreBuffer
		);
	}

	public Catalog(
		@Nonnull String catalogName,
		@Nonnull CacheSupervisor cacheSupervisor,
		@Nonnull EvitaConfiguration evitaConfiguration,
		@Nonnull ReflectionLookup reflectionLookup,
		@Nonnull Scheduler scheduler,
		@Nonnull ExportFileService exportFileService,
		@Nonnull ObservableExecutorService transactionalExecutor,
		@Nonnull Consumer<Catalog> newCatalogVersionConsumer,
		@Nonnull TracingContext tracingContext
	) {
		this.tracingContext = tracingContext;
		this.persistenceService = ServiceLoader.load(CatalogPersistenceServiceFactory.class)
			.findFirst()
			.map(
				it -> it.load(
					this, catalogName,
					evitaConfiguration.storage(),
					evitaConfiguration.transaction(),
					scheduler,
					exportFileService
				)
			)
			.orElseThrow(() -> new IllegalStateException("IO service is unexpectedly not available!"));
		final CatalogHeader catalogHeader = this.persistenceService.getCatalogHeader(
			this.persistenceService.getLastCatalogVersion()
		);
		final long catalogVersion = catalogHeader.version();
		this.catalogId = catalogHeader.catalogId();
		this.versionId = new TransactionalReference<>(catalogVersion);
		this.state = catalogHeader.catalogState();
		// initialize container buffer
		final StoragePartPersistenceService storagePartPersistenceService = this.persistenceService.getStoragePartPersistenceService(catalogVersion);
		// initialize schema - still in constructor
		final CatalogSchema catalogSchema = CatalogSchemaStoragePart.deserializeWithCatalog(
			this,
			() -> ofNullable(storagePartPersistenceService.getStoragePart(catalogVersion, 1, CatalogSchemaStoragePart.class))
				.map(CatalogSchemaStoragePart::catalogSchema)
				.orElseThrow(() -> new SchemaNotFoundException(catalogHeader.catalogName()))
		);
		this.schema = new TransactionalReference<>(new CatalogSchemaDecorator(catalogSchema));
		this.catalogIndex = this.persistenceService.readCatalogIndex(this);
		this.catalogIndex.attachToCatalog(null, this);
		this.cacheSupervisor = cacheSupervisor;
		this.dataStoreBuffer = catalogHeader.catalogState() == CatalogState.WARMING_UP ?
			new WarmUpDataStoreMemoryBuffer<>(storagePartPersistenceService) :
			new TransactionalDataStoreMemoryBuffer<>(this, storagePartPersistenceService);

		final Collection<CollectionFileReference> entityCollectionHeaders = catalogHeader.getEntityTypeFileIndexes();
		final Map<String, EntityCollection> collections = createHashMap(entityCollectionHeaders.size());
		final Map<Integer, EntityCollection> collectionIndex = createHashMap(entityCollectionHeaders.size());
		for (CollectionFileReference entityTypeFileIndex : entityCollectionHeaders) {
			final String entityType = entityTypeFileIndex.entityType();
			final EntityCollectionHeader entityCollectionHeader = this.persistenceService.getEntityCollectionHeader(
				catalogVersion, entityTypeFileIndex.entityTypePrimaryKey()
			);
			final int entityTypePrimaryKey = entityCollectionHeader.entityTypePrimaryKey();
			final EntityCollection collection = new EntityCollection(
				catalogName,
				catalogVersion,
				catalogHeader.catalogState(), entityTypePrimaryKey,
				entityType,
				persistenceService,
				cacheSupervisor,
				sequenceService,
				tracingContext
			);
			collections.put(entityType, collection);
			collectionIndex.put(MAX_POWER_OF_TWO, collection);
		}
		this.entityCollections = new TransactionalMap<>(collections, EntityCollection.class, Function.identity());
		this.entityTypeSequence = sequenceService.getOrCreateSequence(
			catalogName, SequenceType.ENTITY_COLLECTION, catalogHeader.lastEntityCollectionPrimaryKey()
		);
		this.entityCollectionsByPrimaryKey = new TransactionalMap<>(
			entityCollections.values()
				.stream()
				.collect(
					Collectors.toMap(
						EntityCollection::getEntityTypePrimaryKey,
						Function.identity()
					)
				),
			EntityCollection.class, Function.identity()
		);

		for (EntityCollection entityCollection : collections.values()) {
			entityCollection.attachToCatalog(null, this);
		}

		this.entitySchemaIndex = new TransactionalMap<>(
			entityCollections.values()
				.stream()
				.collect(
					Collectors.toMap(
						EntityCollection::getEntityType,
						EntityCollection::getSchema
					)
				)
		);
		this.proxyFactory = ProxyFactory.createInstance(reflectionLookup);
		this.evitaConfiguration = evitaConfiguration;
		this.scheduler = scheduler;
		this.transactionalExecutor = transactionalExecutor;
		this.newCatalogVersionConsumer = newCatalogVersionConsumer;
		this.lastPersistedSchemaVersion = this.schema.get().version();
		this.transactionManager = new TransactionManager(
			this, evitaConfiguration, scheduler, transactionalExecutor, newCatalogVersionConsumer
		);
	}

	Catalog(
		long versionId,
		@Nonnull CatalogState catalogState,
		@Nonnull CatalogIndex catalogIndex,
		@Nonnull Collection<EntityCollection> entityCollections,
		@Nonnull Catalog previousCatalogVersion
	) {
		this(
			versionId,
			catalogState,
			catalogIndex,
			entityCollections,
			previousCatalogVersion.persistenceService,
			previousCatalogVersion,
			previousCatalogVersion.tracingContext
		);
	}

	Catalog(
		long catalogVersion,
		@Nonnull CatalogState catalogState,
		@Nonnull CatalogIndex catalogIndex,
		@Nonnull Collection<EntityCollection> entityCollections,
		@Nonnull CatalogPersistenceService persistenceService,
		@Nonnull Catalog previousCatalogVersion,
		@Nonnull TracingContext tracingContext
	) {
		this.catalogId = previousCatalogVersion.catalogId;
		this.tracingContext = tracingContext;
		this.versionId = new TransactionalReference<>(catalogVersion);
		this.state = catalogState;
		this.catalogIndex = catalogIndex;
		this.persistenceService = persistenceService;
		this.cacheSupervisor = previousCatalogVersion.cacheSupervisor;
		this.entityTypeSequence = previousCatalogVersion.entityTypeSequence;
		this.proxyFactory = previousCatalogVersion.proxyFactory;
		this.evitaConfiguration = previousCatalogVersion.evitaConfiguration;
		this.scheduler = previousCatalogVersion.scheduler;
		this.transactionalExecutor = previousCatalogVersion.transactionalExecutor;
		this.newCatalogVersionConsumer = previousCatalogVersion.newCatalogVersionConsumer;
		this.transactionManager = previousCatalogVersion.transactionManager;

		catalogIndex.attachToCatalog(null, this);
		final StoragePartPersistenceService storagePartPersistenceService = persistenceService.getStoragePartPersistenceService(catalogVersion);
		final CatalogSchema catalogSchema = CatalogSchemaStoragePart.deserializeWithCatalog(
			this,
			() -> ofNullable(storagePartPersistenceService.getStoragePart(catalogVersion, 1, CatalogSchemaStoragePart.class))
				.map(CatalogSchemaStoragePart::catalogSchema)
				.orElseThrow(() -> new SchemaNotFoundException(this.persistenceService.getCatalogHeader(catalogVersion).catalogName()))
		);
		this.schema = new TransactionalReference<>(new CatalogSchemaDecorator(catalogSchema));
		this.dataStoreBuffer = catalogState == CatalogState.WARMING_UP ?
			new WarmUpDataStoreMemoryBuffer<>(storagePartPersistenceService) :
			new TransactionalDataStoreMemoryBuffer<>(this, storagePartPersistenceService);
		// we need to switch references working with catalog (inter index relations) to new catalog
		// the collections are not yet used anywhere - we're still safe here
		final Map<String, EntityCollection> newEntityCollections = CollectionUtils.createHashMap(entityCollections.size());
		final Map<Integer, EntityCollection> newEntityCollectionsIndex = CollectionUtils.createHashMap(entityCollections.size());
		final Map<String, EntitySchemaContract> newEntitySchemaIndex = CollectionUtils.createHashMap(entityCollections.size());
		for (EntityCollection entityCollection : entityCollections) {
			newEntityCollections.put(entityCollection.getEntityType(), entityCollection);
			newEntityCollectionsIndex.put(entityCollection.getEntityTypePrimaryKey(), entityCollection);
		}
		this.entityCollections = new TransactionalMap<>(newEntityCollections, EntityCollection.class, Function.identity());
		this.entityCollectionsByPrimaryKey = new TransactionalMap<>(newEntityCollectionsIndex, EntityCollection.class, Function.identity());
		this.entitySchemaIndex = new TransactionalMap<>(newEntitySchemaIndex);
		this.lastPersistedSchemaVersion = previousCatalogVersion.lastPersistedSchemaVersion;
		// finally attach every collection to this instance of the catalog
		for (EntityCollection entityCollection : entityCollections) {
			entityCollection.attachToCatalog(null, this);
			// when the collection is attached to the catalog, we can access its schema and put it into the schema index
			newEntitySchemaIndex.put(entityCollection.getEntityType(), entityCollection.getSchema());
		}
	}

	@Override
	@Nonnull
	public SealedCatalogSchema getSchema() {
		return schema.get();
	}

	@Nonnull
	@Override
	public CatalogSchemaContract updateSchema(@Nonnull LocalCatalogSchemaMutation... schemaMutation) throws SchemaAlteringException {
		// internal schema is expected to be produced on the server side
		final CatalogSchema originalSchema = getInternalSchema();
		try {
			final Optional<Transaction> transactionRef = Transaction.getTransaction();
			ModifyEntitySchemaMutation[] modifyEntitySchemaMutations = null;
			CatalogSchema currentSchema = originalSchema;
			CatalogSchemaContract updatedSchema = originalSchema;
			for (CatalogSchemaMutation theMutation : schemaMutation) {
				transactionRef.ifPresent(it -> it.registerMutation(theMutation));
				// if the mutation implements entity schema mutation apply it on the appropriate schema
				if (theMutation instanceof ModifyEntitySchemaMutation modifyEntitySchemaMutation) {
					modifyEntitySchema(modifyEntitySchemaMutation, updatedSchema);
				} else if (theMutation instanceof RemoveEntitySchemaMutation removeEntitySchemaMutation) {
					updatedSchema = removeEntitySchema(removeEntitySchemaMutation, transactionRef.orElse(null), updatedSchema);
				} else if (theMutation instanceof CreateEntitySchemaMutation createEntitySchemaMutation) {
					updatedSchema = createEntitySchema(createEntitySchemaMutation, updatedSchema);
				} else if (theMutation instanceof ModifyEntitySchemaNameMutation renameEntitySchemaMutation) {
					updatedSchema = modifyEntitySchemaName(renameEntitySchemaMutation, transactionRef.orElse(null), updatedSchema);
				} else {
					final CatalogSchemaWithImpactOnEntitySchemas schemaWithImpactOnEntitySchemas = modifyCatalogSchema(theMutation, updatedSchema);
					updatedSchema = schemaWithImpactOnEntitySchemas.updatedCatalogSchema();
					modifyEntitySchemaMutations = modifyEntitySchemaMutations == null || ArrayUtils.isEmpty(schemaWithImpactOnEntitySchemas.entitySchemaMutations()) ?
						schemaWithImpactOnEntitySchemas.entitySchemaMutations() :
						ArrayUtils.mergeArrays(modifyEntitySchemaMutations, schemaWithImpactOnEntitySchemas.entitySchemaMutations());
				}

				// exchange the current catalog schema so that additional entity schema mutations can take advantage of
				// previous catalog mutations when validated
				currentSchema = updateCatalogSchema(updatedSchema, currentSchema);
			}
			// alter affected entity schemas
			if (modifyEntitySchemaMutations != null) {
				updateSchema(modifyEntitySchemaMutations);
			}
		} catch (RuntimeException ex) {
			// revert all changes in the schema (for current transaction) if anything failed
			this.schema.set(new CatalogSchemaDecorator(originalSchema));
			throw ex;
		} finally {
			// finally, store the updated catalog schema to disk
			final CatalogSchema currentSchema = getInternalSchema();
			if (currentSchema.version() > originalSchema.version()) {
				this.dataStoreBuffer.update(getVersion(), new CatalogSchemaStoragePart(currentSchema));
			}
		}
		return getSchema();
	}

	@Override
	@Nonnull
	public CatalogState getCatalogState() {
		return this.state;
	}

	@Override
	@Nonnull
	public String getName() {
		return schema.get().getName();
	}

	@Override
	public long getVersion() {
		return this.versionId.get();
	}

	/**
	 * This method is part of the internal API and allows to move forward the catalog version sequence number in
	 * transactional context.
	 *
	 * @param catalogVersion the new catalog version
	 */
	public void setVersion(long catalogVersion) {
		Assert.isTrue(isTransactionAvailable(), "This method is expected to be called in transactional context only.");
		this.versionId.set(catalogVersion);
	}

	@Override
	public boolean supportsTransaction() {
		return state == CatalogState.ALIVE;
	}

	@Override
	@Nonnull
	public Set<String> getEntityTypes() {
		return entityCollections.keySet();
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
	public void applyMutation(@Nonnull Mutation mutation) throws InvalidMutationException {
		if (mutation instanceof LocalCatalogSchemaMutation schemaMutation) {
			// apply schema mutation to the catalog
			updateSchema(schemaMutation);
		} else if (mutation instanceof EntityMutation entityMutation) {
			getCollectionForEntityOrThrowException(entityMutation.getEntityType())
				.applyMutation(entityMutation);
		} else {
			throw new InvalidMutationException(
				"Unexpected mutation type: " + mutation.getClass().getName(),
				"Unexpected mutation type."
			);
		}
	}

	@Nonnull
	@Override
	public EntityCollectionContract createCollectionForEntity(@Nonnull String entityType, @Nonnull EvitaSessionContract session) {
		if (entityCollections.containsKey(entityType)) {
			return entityCollections.get(entityType);
		} else {
			updateSchema(new CreateEntitySchemaMutation(entityType));
			return Objects.requireNonNull(entityCollections.get(entityType));
		}
	}

	@Override
	@Nonnull
	public Optional<EntityCollectionContract> getCollectionForEntity(@Nonnull String entityType) {
		return ofNullable(entityCollections.get(entityType));
	}

	@Override
	@Nonnull
	public EntityCollection getCollectionForEntityOrThrowException(@Nonnull String entityType) throws CollectionNotFoundException {
		return ofNullable(entityCollections.get(entityType))
			.orElseThrow(() -> new CollectionNotFoundException(entityType));
	}

	@Override
	@Nonnull
	public EntityCollection getCollectionForEntityPrimaryKeyOrThrowException(int entityTypePrimaryKey) throws CollectionNotFoundException {
		return ofNullable(this.entityCollectionsByPrimaryKey.get(entityTypePrimaryKey))
			.orElseThrow(() -> new CollectionNotFoundException(entityTypePrimaryKey));
	}

	@Override
	@Nonnull
	public EntityCollection getOrCreateCollectionForEntity(@Nonnull String entityType, @Nonnull EvitaSessionContract session) {
		return getOrCreateCollectionForEntityInternal(entityType);
	}

	@Override
	public boolean deleteCollectionOfEntity(@Nonnull String entityType, @Nonnull EvitaSessionContract session) {
		final SealedCatalogSchema originalSchema = getSchema();
		final CatalogSchemaContract updatedSchema = updateSchema(new RemoveEntitySchemaMutation(entityType));
		return updatedSchema.version() > originalSchema.version();
	}

	@Override
	public boolean renameCollectionOfEntity(@Nonnull String entityType, @Nonnull String newName, @Nonnull EvitaSessionContract session) {
		final SealedCatalogSchema originalSchema = getSchema();
		final CatalogSchemaContract updatedSchema = updateSchema(
			new ModifyEntitySchemaNameMutation(entityType, newName, false)
		);
		return updatedSchema.version() > originalSchema.version();
	}

	@Override
	public boolean replaceCollectionOfEntity(@Nonnull String entityTypeToBeReplaced, @Nonnull String entityTypeToBeReplacedWith, @Nonnull EvitaSessionContract session) {
		final SealedCatalogSchema originalSchema = getSchema();
		final CatalogSchemaContract updatedSchema = updateSchema(new ModifyEntitySchemaNameMutation(entityTypeToBeReplacedWith, entityTypeToBeReplaced, true));
		return updatedSchema.version() > originalSchema.version();
	}

	@Override
	public void delete() {
		persistenceService.delete();
	}

	@Nonnull
	@Override
	public CatalogContract replace(@Nonnull CatalogSchemaContract updatedSchema, @Nonnull CatalogContract catalogToBeReplaced) {
		final long catalogVersion = versionId.get();
		this.entityCollections.values().forEach(EntityCollection::terminate);
		final CatalogPersistenceService newIoService = persistenceService.replaceWith(
			catalogVersion,
			updatedSchema.getName(),
			updatedSchema.getNameVariants(),
			CatalogSchema._internalBuild(updatedSchema),
			this.dataStoreBuffer
		);
		final long catalogVersionAfterRename = newIoService.getLastCatalogVersion();
		final CatalogState catalogState = getCatalogState();
		final List<EntityCollection> newCollections = this.entityCollections
			.values()
			.stream()
			.map(
				it -> new EntityCollection(
					updatedSchema.getName(),
					catalogVersionAfterRename,
					catalogState, it.getEntityTypePrimaryKey(),
					it.getEntityType(),
					newIoService,
					this.cacheSupervisor,
					this.sequenceService,
					this.tracingContext
				)
			).toList();

		this.transactionManager.advanceVersion(catalogVersionAfterRename);
		return new Catalog(
			catalogVersionAfterRename,
			catalogState,
			this.catalogIndex.createCopyForNewCatalogAttachment(catalogState),
			newCollections,
			newIoService,
			this,
			this.tracingContext
		);
	}

	@Nonnull
	@Override
	public Map<String, EntitySchemaContract> getEntitySchemaIndex() {
		return entitySchemaIndex;
	}

	@Override
	@Nonnull
	public Optional<SealedEntitySchema> getEntitySchema(@Nonnull String entityType) {
		return ofNullable(entityCollections.get(entityType))
			.map(EntityCollection::getSchema);
	}

	@Override
	public boolean goLive() {
		try {
			Assert.isTrue(
				this.goingLive.compareAndSet(false, true),
				"Concurrent call of `goLive` method is not supported!"
			);

			Assert.isTrue(this.state == CatalogState.WARMING_UP, "Catalog has already alive state!");
			final CatalogGoesLiveEvent event = new CatalogGoesLiveEvent(getName());

			flush();

			final List<EntityCollection> newCollections = this.entityCollections
				.values()
				.stream()
				.map(collection -> collection.createCopyForNewCatalogAttachment(CatalogState.ALIVE))
				.toList();

			final Catalog newCatalog = new Catalog(
				1L,
				CatalogState.ALIVE,
				this.catalogIndex.createCopyForNewCatalogAttachment(CatalogState.ALIVE),
				newCollections,
				this.persistenceService,
				this,
				this.tracingContext
			);

			this.transactionManager.advanceVersion(newCatalog.getVersion());
			this.newCatalogVersionConsumer.accept(newCatalog);

			// emit the event
			event.finish().commit();

			return true;
		} finally {
			goingLive.set(false);
		}
	}

	@Override
	public void processWriteAheadLog(@Nonnull Consumer<CatalogContract> updatedCatalogConsumer) {
		this.persistenceService.getFirstNonProcessedTransactionInWal(getVersion())
			.ifPresentOrElse(
				transactionMutation -> {
					final long start = System.nanoTime();
					final Catalog catalog = this.transactionManager.processWriteAheadLog(
						transactionMutation.getCatalogVersion(), Long.MAX_VALUE, false
					);
					this.persistenceService.purgeAllObsoleteFiles();
					log.info("WAL of `{}` catalog was processed in {}.", this.getName(), StringUtils.formatNano(System.nanoTime() - start));
					updatedCatalogConsumer.accept(catalog);
				},
				() -> updatedCatalogConsumer.accept(this)
			);
	}

	@Nonnull
	@Override
	public PaginatedList<CatalogVersion> getCatalogVersions(@Nonnull TimeFlow timeFlow, int page, int pageSize) {
		return this.persistenceService.getCatalogVersions(timeFlow, page, pageSize);
	}

	@Nonnull
	@Override
	public Stream<CatalogVersionDescriptor> getCatalogVersionDescriptors(long... catalogVersion) {
		return this.persistenceService.getCatalogVersionDescriptors(catalogVersion);
	}

	@Override
	@Nonnull
	public Stream<Mutation> getCommittedMutationStream(long catalogVersion) {
		return this.persistenceService.getCommittedMutationStream(catalogVersion);
	}

	@Nonnull
	@Override
	public Stream<Mutation> getReversedCommittedMutationStream(long catalogVersion) {
		return this.persistenceService.getReversedCommittedMutationStream(catalogVersion);
	}

	@Nonnull
	@Override
	public ServerTask<?, FileForFetch> backup(@Nullable OffsetDateTime pastMoment, boolean includingWAL) throws TemporalDataNotAvailableException {
		final ServerTask<?, FileForFetch> backupTask = this.persistenceService.createBackupTask(pastMoment, includingWAL);
		this.scheduler.submit(backupTask);
		return backupTask;
	}

	@Override
	public void terminate() {
		try {
			final String catalogName = getName();
			final List<EntityCollectionHeader> entityHeaders;
			boolean changeOccurred = this.lastPersistedSchemaVersion != schema.get().version();
			final boolean warmingUpState = getCatalogState() == CatalogState.WARMING_UP;
			entityHeaders = new ArrayList<>(this.entityCollections.size());
			for (EntityCollection entityCollection : entityCollections.values()) {
				// in warmup state try to persist all changes in volatile memory
				if (warmingUpState) {
					final long lastSeenVersion = entityCollection.getVersion();
					entityHeaders.add(entityCollection.flush());
					changeOccurred = changeOccurred || entityCollection.getVersion() != lastSeenVersion;
				}
				// in all states terminate collection operations
				entityCollection.terminate();
			}

			// if any change occurred (this may happen only in warm up state)
			if (warmingUpState && changeOccurred) {
				// store catalog header
				this.persistenceService.storeHeader(
					this.catalogId,
					getCatalogState(),
					this.versionId.getId(),
					this.entityTypeSequence.get(),
					null,
					entityHeaders,
					this.dataStoreBuffer
				);
			}
			// close all resources here, here we just hand all objects to GC
			this.entityCollections.clear();
			// log info
			log.info("Catalog {} successfully terminated.", catalogName);
		} finally {
			persistenceService.close();
		}
	}

	/**
	 * Returns reference to the main catalog index that allows fast lookups for entities across all types.
	 */
	@Nonnull
	public CatalogIndex getCatalogIndex() {
		return catalogIndex;
	}

	/**
	 * Returns {@link EntitySchema} for passed `entityType` or throws {@link IllegalArgumentException} if schema for
	 * this type is not yet known.
	 */
	@Nonnull
	public <T extends EntityIndex> Optional<T> getEntityIndexIfExists(@Nonnull String entityType, @Nonnull EntityIndexKey indexKey, @Nonnull Class<T> expectedType) {
		final EntityCollection targetCollection = ofNullable(this.entityCollections.get(entityType))
			.orElseThrow(() -> new CollectionNotFoundException(entityType));
		final EntityIndex entityIndex = targetCollection.getIndexByKeyIfExists(indexKey);
		if (entityIndex == null) {
			return empty();
		} else if (expectedType.isInstance(entityIndex)) {
			//noinspection unchecked
			return of((T) entityIndex);
		} else {
			throw new IllegalArgumentException("Expected index of type " + expectedType.getName() + " but got " + entityIndex.getClass().getName());
		}
	}

	/**
	 * Returns internally held {@link CatalogSchema}.
	 */
	@Nonnull
	public CatalogSchema getInternalSchema() {
		return schema.get().getDelegate();
	}

	/**
	 * Updates schema in the map index on schema change.
	 *
	 * @param entitySchema updated entity schema
	 */
	public void entitySchemaUpdated(@Nonnull EntitySchemaContract entitySchema) {
		this.entitySchemaIndex.put(entitySchema.getName(), entitySchema);
	}

	/**
	 * Removes the entity schema from the map index.
	 *
	 * @param entityType the type of the entity schema to be removed
	 */
	public void entitySchemaRemoved(@Nonnull String entityType) {
		this.entitySchemaIndex.remove(entityType);
	}

	@Override
	public DataStoreChanges<CatalogIndexKey, CatalogIndex> createLayer() {
		return new DataStoreChanges<>(
			Transaction.createTransactionalPersistenceService(
				this.persistenceService.getStoragePartPersistenceService(getVersion())
			)
		);
	}

	@Override
	public void removeLayer(@Nonnull TransactionalLayerMaintainer transactionalLayer) {
		transactionalLayer.removeTransactionalMemoryLayerIfExists(this);
		this.schema.removeLayer(transactionalLayer);
		this.entityCollections.removeLayer(transactionalLayer);
		this.catalogIndex.removeLayer(transactionalLayer);
		this.entityCollectionsByPrimaryKey.removeLayer(transactionalLayer);
		this.entitySchemaIndex.removeLayer(transactionalLayer);
	}

	@Nonnull
	@Override
	public Catalog createCopyWithMergedTransactionalMemory(
		@Nullable DataStoreChanges<CatalogIndexKey, CatalogIndex> layer,
		@Nonnull TransactionalLayerMaintainer transactionalLayer
	) {
		/* the version is incremented via. {@link #setVersion} method */
		final long newCatalogVersionId = transactionalLayer.getStateCopyWithCommittedChanges(this.versionId).orElseThrow();
		final CatalogSchemaDecorator newSchema = transactionalLayer.getStateCopyWithCommittedChanges(this.schema).orElseThrow();
		final DataStoreChanges<CatalogIndexKey, CatalogIndex> transactionalChanges = transactionalLayer.getTransactionalMemoryLayerIfExists(this);

		final MapChanges<String, EntityCollection> collectionChanges = transactionalLayer.getTransactionalMemoryLayerIfExists(this.entityCollections);
		Map<String, EntityCollectionPersistenceService> updatedServiceCollections = null;
		if (collectionChanges != null) {
			// recognize renamed collections
			final Map<String, EntityCollection> originalCollectionContents = collectionChanges.getMapDelegate();
			final ObjectObjectIdentityHashMap<EntityCollection, String> originalCollections = new ObjectObjectIdentityHashMap<>(collectionChanges.getRemovedKeys().size());
			for (String removedKey : collectionChanges.getRemovedKeys()) {
				originalCollections.put(collectionChanges.getMapDelegate().get(removedKey), removedKey);
			}
			for (Entry<String, EntityCollection> updatedKey : collectionChanges.getModifiedKeys().entrySet()) {
				final EntityCollection updatedCollection = updatedKey.getValue();
				final String removedEntityType = originalCollections.get(updatedCollection);
				final String newEntityType = updatedKey.getKey();
				if (removedEntityType != null) {
					final EntityCollectionPersistenceService newPersistenceService = this.persistenceService.replaceCollectionWith(
						newCatalogVersionId, removedEntityType,
						updatedCollection.getEntityTypePrimaryKey(),
						newEntityType
					);
					if (updatedServiceCollections == null) {
						updatedServiceCollections = new HashMap<>(collectionChanges.getModifiedKeys().size());
					}
					updatedServiceCollections.put(newEntityType, newPersistenceService);
					originalCollections.remove(updatedCollection);
					ofNullable(originalCollectionContents.get(newEntityType)).ifPresent(it -> it.removeLayer(transactionalLayer));
				}
			}
			for (ObjectObjectCursor<EntityCollection, String> originalItem : originalCollections) {
				this.persistenceService.deleteEntityCollection(newCatalogVersionId, originalItem.key.getEntityCollectionHeader());
				originalCollectionContents.get(originalItem.value).removeLayer(transactionalLayer);
			}
		}

		final Map<String, EntityCollection> possiblyUpdatedCollections = transactionalLayer.getStateCopyWithCommittedChanges(entityCollections);
		// replace all entity collections with new one if their storage persistence service was changed
		if (updatedServiceCollections != null) {
			updatedServiceCollections.forEach((entityType, newPersistenceService) -> {
				possiblyUpdatedCollections.compute(
					entityType,
					(entityTypeKey, entityCollection) -> entityCollection.createCopyWithNewPersistenceService(newCatalogVersionId, CatalogState.ALIVE, newPersistenceService)
				);
			});
		}

		final CatalogIndex possiblyUpdatedCatalogIndex = transactionalLayer.getStateCopyWithCommittedChanges(catalogIndex);
		transactionalLayer.removeTransactionalMemoryLayerIfExists(this.entityCollectionsByPrimaryKey);
		transactionalLayer.removeTransactionalMemoryLayerIfExists(this.entitySchemaIndex);

		if (transactionalChanges != null) {
			final StoragePartPersistenceService storagePartPersistenceService = this.persistenceService.getStoragePartPersistenceService(newCatalogVersionId);
			final CatalogSchemaStoragePart storedSchema = CatalogSchemaStoragePart.deserializeWithCatalog(
				this, () -> storagePartPersistenceService.getStoragePart(newCatalogVersionId, 1, CatalogSchemaStoragePart.class)
			);

			if (newSchema.version() != storedSchema.catalogSchema().version()) {
				final CatalogSchemaStoragePart storagePart = new CatalogSchemaStoragePart(newSchema.getDelegate());
				storagePartPersistenceService.putStoragePart(storagePart.getStoragePartPK(), storagePart);
			}

			// when we register all storage parts for persisting we can now release transactional memory
			transactionalLayer.removeTransactionalMemoryLayer(this);

			return new Catalog(
				newCatalogVersionId,
				getCatalogState(),
				possiblyUpdatedCatalogIndex,
				possiblyUpdatedCollections.values(),
				this
			);
		} else {
			if (possiblyUpdatedCatalogIndex != catalogIndex ||
				possiblyUpdatedCollections
					.entrySet()
					.stream()
					.anyMatch(it -> this.entityCollections.get(it.getKey()) != it.getValue())
			) {
				return new Catalog(
					newCatalogVersionId,
					getCatalogState(),
					possiblyUpdatedCatalogIndex,
					possiblyUpdatedCollections.values(),
					this
				);
			} else {
				// no changes present we can return self
				return this;
			}
		}
	}

	/**
	 * Commits a Write-Ahead Log (WAL) for and processes the transaction.
	 *
	 * @param transactionId         The ID of the transaction to commit.
	 * @param commitBehaviour       The requested stage the transaction needs to reach in order the returned future
	 *                              is completed.
	 * @param walPersistenceService The Write-Ahead Log persistence service.
	 * @throws TransactionException If an unknown exception occurs while processing the transaction.
	 */
	public void commitWal(
		@Nonnull UUID transactionId,
		@Nonnull CommitBehavior commitBehaviour,
		@Nonnull IsolatedWalPersistenceService walPersistenceService,
		@Nonnull CompletableFuture<Long> transactionFinalizationFuture
	) {
		try {
			this.transactionManager.commit(
				transactionId, commitBehaviour, walPersistenceService, transactionFinalizationFuture
			);
		} catch (Exception e) {
			this.transactionManager.invalidateTransactionalPublisher();
			if (e.getCause() instanceof TransactionException txException) {
				throw txException;
			} else {
				throw new TransactionException(
					"Unknown exception occurred while processing transaction!", e
				);
			}
		}
	}

	/**
	 * Retrieves the stream of committed mutations since the given catalogVersion. The first mutation in the stream
	 * will be {@link TransactionMutation} that evolved the catalog to the catalogVersion plus one. This method differs
	 * from {@link #getCommittedMutationStream(long)} in that it expects the WAL is being actively written to and
	 * the returned stream may be potentially infinite.
	 *
	 * @param startCatalogVersion     the catalog version to start reading from
	 * @param requestedCatalogVersion the minimal catalog version to finish reading
	 * @return The stream of committed mutations since the given catalogVersion
	 */
	@Nonnull
	public Stream<Mutation> getCommittedLiveMutationStream(long startCatalogVersion, long requestedCatalogVersion) {
		return this.persistenceService.getCommittedLiveMutationStream(startCatalogVersion, requestedCatalogVersion);
	}

	/**
	 * Retrieves the last catalog version written in the WAL stream.
	 *
	 * @return the last catalog version written in the WAL stream
	 */
	public long getLastCatalogVersionInMutationStream() {
		return this.persistenceService.getLastCatalogVersionInMutationStream();
	}

	/**
	 * This method writes all changed storage parts into the file offset index of {@link EntityCollection} and then stores
	 * {@link CatalogHeader} marking the catalog version as committed.
	 */
	public void flush(long catalogVersion, @Nonnull TransactionMutation lastProcessedTransaction) {
		Assert.isPremiseValid(getCatalogState() == CatalogState.ALIVE, "Catalog is not in ALIVE state!");
		boolean changeOccurred = this.schema.get().version() != this.lastPersistedSchemaVersion;
		final List<EntityCollectionHeader> entityHeaders = new ArrayList<>(this.entityCollections.size());
		for (EntityCollection entityCollection : this.entityCollections.values()) {
			final long lastSeenVersion = entityCollection.getVersion();
			entityHeaders.add(entityCollection.flush(catalogVersion));
			changeOccurred = changeOccurred || entityCollection.getVersion() != lastSeenVersion;
		}

		if (changeOccurred) {
			this.persistenceService.flushTrappedUpdates(
				catalogVersion,
				this.dataStoreBuffer.getTrappedIndexChanges()
			);
			this.persistenceService.storeHeader(
				this.catalogId,
				CatalogState.ALIVE,
				catalogVersion,
				this.entityTypeSequence.get(),
				lastProcessedTransaction,
				entityHeaders,
				this.dataStoreBuffer
			);
			this.lastPersistedSchemaVersion = this.schema.get().version();
		}
	}

	/**
	 * Creates an isolated WAL (Write-Ahead Log) service for the specified transaction ID.
	 *
	 * @param transactionId The ID of the transaction.
	 * @return The IsolatedWalPersistenceService instance for the specified transaction ID.
	 */
	@Nonnull
	public IsolatedWalPersistenceService createIsolatedWalService(@Nonnull UUID transactionId) {
		return this.persistenceService.createIsolatedWalPersistenceService(transactionId);
	}

	/**
	 * Appends the given transaction mutation to the write-ahead log (WAL) and appends its mutation chain taken from
	 * offHeapWithFileBackupReference. After that it discards the specified off-heap data with file backup reference.
	 *
	 * @param transactionMutation The transaction mutation to append to the WAL.
	 * @param walReference        The off-heap data with file backup reference to discard.
	 * @return the number of Bytes written
	 */
	public long appendWalAndDiscard(
		@Nonnull TransactionMutation transactionMutation,
		@Nonnull OffHeapWithFileBackupReference walReference
	) {
		return this.persistenceService.appendWalAndDiscard(getVersion(), transactionMutation, walReference);
	}

	/**
	 * Notifies the system that a catalog is present in the live view.
	 * This method is used to indicate that a catalog is currently available in the live view.
	 */
	public void notifyCatalogPresentInLiveView() {
		this.transactionManager.notifyCatalogPresentInLiveView(this);
	}

	/**
	 * Method for internal use - allows emitting start events when observability facilities are already initialized.
	 * If we didn't postpone this initialization, events would become lost.
	 */
	public void emitStartObservabilityEvents() {
		this.persistenceService.emitStartObservabilityEvents();
	}

	/**
	 * Method for internal use. Allows to emit events clearing the information about deleted catalog.
	 */
	public void emitDeleteObservabilityEvents() {
		this.persistenceService.emitDeleteObservabilityEvents();
	}

	/**
	 * We need to forget all volatile data when the data written to catalog aren't going to be committed (incorporated
	 * in the final state).
	 *
	 * @see CatalogPersistenceService#forgetVolatileData()
	 */
	public void forgetVolatileData() {
		this.persistenceService.forgetVolatileData();
	}

	@Override
	public void catalogVersionBeyondTheHorizon(@Nullable Long minimalActiveCatalogVersion) {
		if (this.persistenceService instanceof CatalogVersionBeyondTheHorizonListener cvbthl) {
			cvbthl.catalogVersionBeyondTheHorizon(minimalActiveCatalogVersion);
		}
	}

	/**
	 * Method allows to immediately flush all information held in memory to the persistent storage.
	 * This method might do nothing particular in transaction ({@link CatalogState#ALIVE}) mode.
	 * Method stores {@link EntityCollectionHeader} in case there were any changes in the file offset index executed
	 * in BULK / non-transactional mode.
	 */
	void flush() {
		// if we're going live start with TRUE (force flush), otherwise start with false
		boolean changeOccurred = this.goingLive.get() || this.schema.get().version() != this.lastPersistedSchemaVersion;
		Assert.isPremiseValid(
			getCatalogState() == CatalogState.WARMING_UP,
			"Cannot flush catalog in transactional mode. Any changes could occur only in transaction!"
		);

		final List<EntityCollectionHeader> entityHeaders = new ArrayList<>(this.entityCollections.size());
		for (EntityCollection entityCollection : this.entityCollections.values()) {
			final long lastSeenVersion = entityCollection.getVersion();
			entityHeaders.add(entityCollection.flush());
			changeOccurred = changeOccurred || entityCollection.getVersion() != lastSeenVersion;
		}

		if (changeOccurred) {
			this.persistenceService.flushTrappedUpdates(
				0L,
				this.dataStoreBuffer.getTrappedIndexChanges()
			);
			this.persistenceService.storeHeader(
				this.catalogId,
				this.goingLive.get() ? CatalogState.ALIVE : getCatalogState(),
				0L,
				this.entityTypeSequence.get(),
				null,
				entityHeaders,
				this.dataStoreBuffer
			);
			this.lastPersistedSchemaVersion = this.schema.get().version();
		}
	}

	/*
		PRIVATE METHODS
	 */

	/**
	 * Replaces reference to the catalog in this instance. The reference is stored in transactional data structure so
	 * that it doesn't affect parallel clients until committed.
	 *
	 * @param updatedSchema updated schema
	 * @param currentSchema current schema
	 * @return updated schema
	 */
	@Nonnull
	private CatalogSchema updateCatalogSchema(
		@Nonnull CatalogSchemaContract updatedSchema,
		@Nonnull CatalogSchema currentSchema
	) {
		final CatalogSchemaContract nextSchema = updatedSchema;
		Assert.isPremiseValid(updatedSchema != null, "Catalog cannot be dropped by updating schema!");
		Assert.isPremiseValid(updatedSchema instanceof CatalogSchema, "Mutation is expected to produce CatalogSchema instance!");
		final CatalogSchema updatedInternalSchema = (CatalogSchema) updatedSchema;

		if (updatedSchema.version() > currentSchema.version()) {
			final CatalogSchemaDecorator originalSchemaBeforeExchange = this.schema.compareAndExchange(
				this.schema.get(),
				new CatalogSchemaDecorator(updatedInternalSchema)
			);
			Assert.isTrue(
				originalSchemaBeforeExchange.version() == currentSchema.version(),
				() -> new ConcurrentSchemaUpdateException(currentSchema, nextSchema)
			);
		}
		return updatedInternalSchema;
	}

	/**
	 * Modifies a catalog schema using the provided mutation and updated schema.
	 *
	 * @param theMutation   The mutation to be applied on the catalog schema.
	 * @param catalogSchema The updated catalog schema.
	 * @return The modified catalog schema along with its impact on entity schemas.
	 */
	@Nonnull
	private CatalogSchemaWithImpactOnEntitySchemas modifyCatalogSchema(
		@Nonnull CatalogSchemaMutation theMutation,
		@Nonnull CatalogSchemaContract catalogSchema
	) {
		final CatalogSchemaWithImpactOnEntitySchemas schemaWithImpactOnEntitySchemas;
		if (theMutation instanceof LocalCatalogSchemaMutation localCatalogSchemaMutation) {
			schemaWithImpactOnEntitySchemas = localCatalogSchemaMutation.mutate(catalogSchema, getEntitySchemaAccessor());
		} else {
			schemaWithImpactOnEntitySchemas = theMutation.mutate(catalogSchema);
		}
		Assert.isPremiseValid(
			schemaWithImpactOnEntitySchemas != null && schemaWithImpactOnEntitySchemas.updatedCatalogSchema() != null,
			"Catalog schema mutation is expected to produce CatalogSchema instance!"
		);
		return schemaWithImpactOnEntitySchemas;
	}

	/**
	 * Modifies the name of an entity schema.
	 *
	 * @param renameEntitySchemaMutation The mutation to rename the entity schema.
	 * @param transactionRef             The reference to the transaction.
	 * @param catalogSchema              The updated catalog schema.
	 * @return The modified entity schema.
	 */
	@Nonnull
	private CatalogSchemaContract modifyEntitySchemaName(
		@Nonnull ModifyEntitySchemaNameMutation renameEntitySchemaMutation,
		@Nullable Transaction transactionRef,
		@Nonnull CatalogSchemaContract catalogSchema
	) {
		if (renameEntitySchemaMutation.isOverwriteTarget() && entityCollections.containsKey(renameEntitySchemaMutation.getNewName())) {
			replaceEntityCollectionInternal(transactionRef != null, renameEntitySchemaMutation);
		} else {
			renameEntityCollectionInternal(transactionRef != null, renameEntitySchemaMutation);
		}
		return CatalogSchema._internalBuildWithUpdatedVersion(
			catalogSchema,
			getEntitySchemaAccessor()
		);
	}

	/**
	 * Creates a new entity schema and adds it to the catalog schema.
	 *
	 * @param createEntitySchemaMutation The mutation used to create the entity schema.
	 * @param catalogSchema              The catalog schema to add the new entity schema to.
	 * @return The updated catalog schema.
	 */
	@Nonnull
	private CatalogSchemaContract createEntitySchema(
		@Nonnull CreateEntitySchemaMutation createEntitySchemaMutation,
		@Nonnull CatalogSchemaContract catalogSchema
	) {
		this.persistenceService.verifyEntityType(
			this.entityCollections.values(),
			createEntitySchemaMutation.getName()
		);
		final EntityCollection newCollection = new EntityCollection(
			this.getName(),
			this.getVersion(),
			this.getCatalogState(), this.entityTypeSequence.incrementAndGet(),
			createEntitySchemaMutation.getName(),
			persistenceService,
			cacheSupervisor,
			sequenceService,
			tracingContext
		);
		this.entityCollectionsByPrimaryKey.put(newCollection.getEntityTypePrimaryKey(), newCollection);
		this.entityCollections.put(newCollection.getEntityType(), newCollection);
		newCollection.attachToCatalog(null, this);
		final CatalogSchema newSchema = CatalogSchema._internalBuildWithUpdatedVersion(
			catalogSchema,
			getEntitySchemaAccessor()
		);
		entitySchemaUpdated(newCollection.getSchema());
		return newSchema;
	}

	/**
	 * Removes an entity schema from the catalog schema.
	 *
	 * @param removeEntitySchemaMutation The remove entity schema mutation.
	 * @param transaction                The transaction (optional).
	 * @param catalogSchema              The catalog schema (optional).
	 * @return The catalog schema contract after removing the entity schema.
	 */
	@Nonnull
	private CatalogSchemaContract removeEntitySchema(
		@Nonnull RemoveEntitySchemaMutation removeEntitySchemaMutation,
		@Nullable Transaction transaction,
		@Nullable CatalogSchemaContract catalogSchema
	) {
		final EntityCollection collectionToRemove = this.entityCollections.remove(removeEntitySchemaMutation.getName());
		if (transaction == null) {
			final long catalogVersion = getVersion();
			this.persistenceService.deleteEntityCollection(
				catalogVersion,
				catalogVersion > 0L ? collectionToRemove.flush(catalogVersion) : collectionToRemove.flush()
			);
		}
		final CatalogSchemaContract result;
		if (collectionToRemove != null) {
			if (transaction != null) {
				collectionToRemove.removeLayer();
			}
			result = CatalogSchema._internalBuildWithUpdatedVersion(
				catalogSchema,
				getEntitySchemaAccessor()
			);
			entitySchemaRemoved(collectionToRemove.getEntityType());
		} else {
			result = catalogSchema;
		}
		return result;
	}

	/**
	 * Modifies the entity schema by applying the given schema mutations.
	 *
	 * @param modifyEntitySchemaMutation The modifications to be applied to the entity schema.
	 * @param catalogSchema              The catalog schema associated with the entity.
	 */
	private void modifyEntitySchema(
		@Nonnull ModifyEntitySchemaMutation modifyEntitySchemaMutation,
		@Nonnull CatalogSchemaContract catalogSchema
	) {
		final String entityType = modifyEntitySchemaMutation.getEntityType();
		// if the collection doesn't exist yet - create new one
		final EntityCollectionContract entityCollection = getOrCreateCollectionForEntityInternal(entityType);
		final SealedEntitySchema currentEntitySchema = entityCollection.getSchema();
		if (!ArrayUtils.isEmpty(modifyEntitySchemaMutation.getSchemaMutations())) {
			// validate the new schema version before any changes are applied
			currentEntitySchema.withMutations(modifyEntitySchemaMutation)
				.toInstance()
				.validate(catalogSchema);
			entityCollection.updateSchema(catalogSchema, modifyEntitySchemaMutation.getSchemaMutations());
		}
	}

	/**
	 * Retrieves an existing EntityCollection for the given entity type or creates a new one if it doesn't exist.
	 *
	 * @param entityType The type of the entity for which to retrieve or create an EntityCollection.
	 * @return The EntityCollection associated with the given entity type.
	 * @throws InvalidSchemaMutationException Thrown if the entity collection doesn't exist and cannot be automatically
	 *                                        created based on the catalog schema.
	 */
	@Nonnull
	private EntityCollection getOrCreateCollectionForEntityInternal(@Nonnull String entityType) {
		return ofNullable(entityCollections.get(entityType))
			.orElseGet(() -> {
				if (!getSchema().getCatalogEvolutionMode().contains(CatalogEvolutionMode.ADDING_ENTITY_TYPES)) {
					throw new InvalidSchemaMutationException(
						"The entity collection `" + entityType + "` doesn't exist and would be automatically created," +
							" providing that catalog schema allows `" + CatalogEvolutionMode.ADDING_ENTITY_TYPES + "`" +
							" evolution mode."
					);
				}
				updateSchema(new CreateEntitySchemaMutation(entityType));
				return Objects.requireNonNull(entityCollections.get(entityType));
			});
	}

	/**
	 * Method creates {@link QueryPlanningContext} that is used for read operations.
	 */
	@Nonnull
	private QueryPlanningContext createQueryContext(@Nonnull EvitaRequest evitaRequest, @Nonnull EvitaSessionContract session) {
		return new QueryPlanningContext(
			this,
			null,
			session, evitaRequest,
			evitaRequest.isQueryTelemetryRequested() ? new QueryTelemetry(QueryPhase.OVERALL) : null,
			Collections.emptyMap(),
			cacheSupervisor
		);
	}

	/**
	 * Renames the existing entity collection in catalog.
	 */
	private void renameEntityCollectionInternal(
		boolean transactionOpen,
		@Nonnull ModifyEntitySchemaNameMutation modifyEntitySchemaNameMutation
	) {
		final String currentName = modifyEntitySchemaNameMutation.getName();
		final String newName = modifyEntitySchemaNameMutation.getNewName();
		this.persistenceService.verifyEntityType(
			this.entityCollections.values(),
			modifyEntitySchemaNameMutation.getNewName()
		);

		final EntityCollection entityCollectionToBeRenamed = getCollectionForEntityOrThrowException(currentName);
		doReplaceEntityCollectionInternal(
			modifyEntitySchemaNameMutation, newName, currentName,
			entityCollectionToBeRenamed,
			transactionOpen
		);
	}

	/**
	 * Replaces existing entity collection in catalog.
	 */
	private void replaceEntityCollectionInternal(boolean transactionOpen, @Nonnull ModifyEntitySchemaNameMutation modifyEntitySchemaNameMutation) {
		final String currentName = modifyEntitySchemaNameMutation.getName();
		final String newName = modifyEntitySchemaNameMutation.getNewName();
		getCollectionForEntityOrThrowException(currentName);
		final EntityCollection entityCollectionToBeReplacedWith = getCollectionForEntityOrThrowException(currentName);

		doReplaceEntityCollectionInternal(
			modifyEntitySchemaNameMutation, newName, currentName,
			entityCollectionToBeReplacedWith,
			transactionOpen
		);
	}

	/**
	 * Internal shared implementation of catalog replacement used both from rename and replace existing catalog methods.
	 */
	private void doReplaceEntityCollectionInternal(
		@Nonnull ModifyEntitySchemaNameMutation modifyEntitySchemaName,
		@Nonnull String entityCollectionNameToBeReplaced,
		@Nonnull String entityCollectionNameToBeReplacedWith,
		@Nonnull EntityCollection entityCollectionToBeReplacedWith,
		boolean transactionOpen
	) {
		entityCollectionToBeReplacedWith.updateSchema(getSchema(), modifyEntitySchemaName);
		this.entityCollections.remove(entityCollectionNameToBeReplacedWith);
		if (!transactionOpen) {
			entityCollectionToBeReplacedWith.flush();
			final long catalogVersion = getVersion();
			Assert.isPremiseValid(catalogVersion == 0L, "Catalog version is expected to be `0`!");
			final EntityCollectionPersistenceService newPersistenceService = this.persistenceService.replaceCollectionWith(
				catalogVersion, entityCollectionNameToBeReplacedWith,
				entityCollectionToBeReplacedWith.getEntityTypePrimaryKey(),
				entityCollectionNameToBeReplaced
			);
			this.entityCollections.put(
				entityCollectionNameToBeReplaced,
				entityCollectionToBeReplacedWith.createCopyWithNewPersistenceService(
					catalogVersion, this.getCatalogState(), newPersistenceService
				)
			);
			// store catalog with a new file pointer
			this.flush();
		} else {
			this.entityCollections.put(entityCollectionNameToBeReplaced, entityCollectionToBeReplacedWith);
		}
	}

	/**
	 * This implementation just manipulates with the set of EntityIndex in entity collection.
	 */
	private class CatalogIndexMaintainerImpl implements IndexMaintainer<CatalogIndexKey, CatalogIndex> {

		/**
		 * Returns entity index by its key. If such index doesn't exist, it is automatically created.
		 */
		@Nonnull
		@Override
		public CatalogIndex getOrCreateIndex(@Nonnull CatalogIndexKey entityIndexKey) {
			return Catalog.this.dataStoreBuffer.getOrCreateIndexForModification(
				entityIndexKey,
				eik -> Catalog.this.catalogIndex
			);
		}

		/**
		 * Returns existing index for passed `entityIndexKey` or returns null.
		 */
		@Nullable
		@Override
		public CatalogIndex getIndexIfExists(@Nonnull CatalogIndexKey entityIndexKey) {
			return Catalog.this.catalogIndex;
		}

		/**
		 * Removes entity index by its key. If such index doesn't exist, exception is thrown.
		 *
		 * @throws IllegalArgumentException when entity index doesn't exist
		 */
		@Override
		public void removeIndex(@Nonnull CatalogIndexKey entityIndexKey) {
			throw new GenericEvitaInternalError("Global catalog index is not expected to be removed!");
		}

	}

	/**
	 * A class that serves as an accessor for the entity schemas in a Catalog object.
	 */
	private class CatalogEntitySchemaAccessor implements EntitySchemaProvider {
		@Nonnull
		@Override
		public Collection<EntitySchemaContract> getEntitySchemas() {
			return Catalog.this.getEntitySchemaIndex().values();
		}

		@Nonnull
		@Override
		public Optional<EntitySchemaContract> getEntitySchema(@Nonnull String entityType) {
			return Catalog.this.getEntitySchema(entityType).map(EntitySchemaContract.class::cast);
		}
	}
}
