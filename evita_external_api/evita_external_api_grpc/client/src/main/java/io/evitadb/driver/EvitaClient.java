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

package io.evitadb.driver;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import io.evitadb.api.EvitaContract;
import io.evitadb.api.EvitaSessionContract;
import io.evitadb.api.SessionTraits;
import io.evitadb.api.SessionTraits.SessionFlags;
import io.evitadb.api.TransactionContract.CommitBehavior;
import io.evitadb.api.exception.FileForFetchNotFoundException;
import io.evitadb.api.exception.InstanceTerminatedException;
import io.evitadb.api.exception.TemporalDataNotAvailableException;
import io.evitadb.api.exception.TransactionException;
import io.evitadb.api.file.FileForFetch;
import io.evitadb.api.requestResponse.schema.CatalogSchemaEditor.CatalogSchemaBuilder;
import io.evitadb.api.requestResponse.schema.EntitySchemaContract;
import io.evitadb.api.requestResponse.schema.mutation.TopLevelCatalogSchemaMutation;
import io.evitadb.api.requestResponse.schema.mutation.catalog.CreateCatalogSchemaMutation;
import io.evitadb.api.requestResponse.system.SystemStatus;
import io.evitadb.api.task.Task;
import io.evitadb.api.task.TaskStatus;
import io.evitadb.dataType.PaginatedList;
import io.evitadb.driver.config.EvitaClientConfiguration;
import io.evitadb.driver.exception.EvitaClientTimedOutException;
import io.evitadb.driver.exception.IncompatibleClientException;
import io.evitadb.driver.interceptor.ClientSessionInterceptor;
import io.evitadb.driver.pooling.ChannelPool;
import io.evitadb.driver.trace.ClientTracingContext;
import io.evitadb.driver.trace.ClientTracingContextProvider;
import io.evitadb.driver.trace.DefaultClientTracingContext;
import io.evitadb.exception.EvitaInternalError;
import io.evitadb.exception.EvitaInvalidUsageException;
import io.evitadb.exception.GenericEvitaInternalError;
import io.evitadb.exception.InvalidEvitaVersionException;
import io.evitadb.exception.UnexpectedIOException;
import io.evitadb.externalApi.grpc.certificate.ClientCertificateManager;
import io.evitadb.externalApi.grpc.dataType.EvitaDataTypesConverter;
import io.evitadb.externalApi.grpc.generated.*;
import io.evitadb.externalApi.grpc.generated.EvitaServiceGrpc.EvitaServiceFutureStub;
import io.evitadb.externalApi.grpc.generated.EvitaServiceGrpc.EvitaServiceStub;
import io.evitadb.externalApi.grpc.generated.GrpcSpecifiedTaskStatusesRequest.Builder;
import io.evitadb.externalApi.grpc.requestResponse.EvitaEnumConverter;
import io.evitadb.externalApi.grpc.requestResponse.schema.mutation.DelegatingTopLevelCatalogSchemaMutationConverter;
import io.evitadb.externalApi.grpc.requestResponse.schema.mutation.SchemaMutationConverter;
import io.evitadb.utils.ArrayUtils;
import io.evitadb.utils.Assert;
import io.evitadb.utils.CertificateUtils;
import io.evitadb.utils.CollectionUtils;
import io.evitadb.utils.ReflectionLookup;
import io.evitadb.utils.UUIDUtil;
import io.evitadb.utils.VersionUtils;
import io.evitadb.utils.VersionUtils.SemVer;
import io.grpc.ManagedChannel;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.evitadb.externalApi.grpc.dataType.EvitaDataTypesConverter.toGrpcUuid;
import static java.util.Optional.ofNullable;

/**
 * The EvitaClient implements {@link EvitaContract} interface and aims to behave identically as if the evitaDB is used
 * as an embedded engine. The purpose is to switch between the client & server setup and the single server setup
 * seamlessly. The client & server implementation takes advantage of gRPC API that is best suited for fast communication
 * between two endpoints if both parties are Java based.
 *
 * The class is thread-safe and can be used from multiple threads to acquire {@link EvitaClientSession} that are not
 * thread-safe.
 *
 * @author Jan Novotný (novotny@fg.cz), FG Forrest a.s. (c) 2022
 * @see EvitaContract
 */
@ThreadSafe
@Slf4j
public class EvitaClient implements EvitaContract {

	static final Pattern ERROR_MESSAGE_PATTERN = Pattern.compile("(\\w+:\\w+:\\w+): (.*)");

	private static final SchemaMutationConverter<TopLevelCatalogSchemaMutation, GrpcTopLevelCatalogSchemaMutation> CATALOG_SCHEMA_MUTATION_CONVERTER =
		new DelegatingTopLevelCatalogSchemaMutationConverter();

	/**
	 * The configuration of the evitaDB client.
	 */
	@Getter private final EvitaClientConfiguration configuration;
	/**
	 * The channel pool is used to manage the gRPC channels. The channels are created lazily and are reused for
	 * subsequent requests. The channel pool is thread-safe.
	 */
	private final ChannelPool channelPool;
	/**
	 * True if client is active and hasn't yet been closed.
	 */
	private final AtomicBoolean active = new AtomicBoolean(true);
	/**
	 * Reflection lookup is used to speed up reflection operation by memoizing the results for examined classes.
	 */
	@Getter private final ReflectionLookup reflectionLookup;
	/**
	 * Index of the {@link EntitySchemaContract} cache. See {@link EvitaEntitySchemaCache} for more information.
	 * The key in index is the catalog name.
	 */
	private final Map<String, EvitaEntitySchemaCache> entitySchemaCache = new ConcurrentHashMap<>(8);
	/**
	 * Index of the opened and active {@link EvitaClientSession} indexed by their unique {@link UUID}
	 */
	private final Map<UUID, EvitaSessionContract> activeSessions = CollectionUtils.createConcurrentHashMap(16);
	/**
	 * A lambda that needs to be invoked upon EvitaClient closing. It goes through all opened {@link EvitaClientSession}
	 * and closes them along with their gRPC channels.
	 */
	private final Runnable terminationCallback;
	/**
	 * Client call timeout.
	 */
	private final ThreadLocal<LinkedList<Timeout>> timeout;
	/**
	 * Executor service used for asynchronous operations.
	 */
	private final ExecutorService executor;
	/**
	 * Client task tracker is used to track the tasks and their status.
	 */
	private final ClientTaskTracker clientTaskTracker;

	@Nonnull
	private static ClientTracingContext getClientTracingContext(@Nonnull EvitaClientConfiguration configuration) {
		final ClientTracingContext context = ClientTracingContextProvider.getContext();
		final Object openTelemetryInstance = configuration.openTelemetryInstance();
		if (openTelemetryInstance != null && context instanceof DefaultClientTracingContext) {
			throw new EvitaInvalidUsageException(
				"OpenTelemetry instance is set, but tracing context is not configured!"
			);
		}
		return context;
	}

	public EvitaClient(@Nonnull EvitaClientConfiguration configuration) {
		this(configuration, null);
	}

	public EvitaClient(
		@Nonnull EvitaClientConfiguration configuration,
		@Nullable Consumer<NettyChannelBuilder> grpcConfigurator
	) {
		this.configuration = configuration;

		NettyChannelBuilder nettyChannelBuilder = NettyChannelBuilder.forAddress(configuration.host(), configuration.port());
		if (configuration.tlsEnabled()) {
			final ClientCertificateManager clientCertificateManager = new ClientCertificateManager.Builder()
				.useGeneratedCertificate(configuration.useGeneratedCertificate(), configuration.host(), configuration.systemApiPort())
				.usingTrustedRootCaCertificate(configuration.trustCertificate())
				.trustStorePassword(configuration.trustStorePassword())
				.mtls(configuration.mtlsEnabled())
				.certificateClientFolderPath(configuration.certificateFolderPath())
				.rootCaCertificateFilePath(configuration.rootCaCertificatePath())
				.clientCertificateFilePath(configuration.certificateFileName())
				.clientPrivateKeyFilePath(configuration.certificateKeyFileName())
				.clientPrivateKeyPassword(configuration.certificateKeyPassword())
				.build();
			nettyChannelBuilder.sslContext(
				clientCertificateManager.buildClientSslContext(
					(certificateType, certificate) -> {
						try {
							switch (certificateType) {
								case SERVER ->
									log.info("Server's CA certificate fingerprint: {}", CertificateUtils.getCertificateFingerprint(certificate));
								case CLIENT ->
									log.info("Client's certificate fingerprint: {}", CertificateUtils.getCertificateFingerprint(certificate));
							}
						} catch (NoSuchAlgorithmException | CertificateEncodingException e) {
							throw new GenericEvitaInternalError(
								"Failed to get certificate fingerprint.",
								"Failed to get certificate fingerprint: " + e.getMessage(),
								e
							);
						}
					}
				)
			);
		} else {
			nettyChannelBuilder.usePlaintext();
		}

		this.executor = Executors.newCachedThreadPool();
		nettyChannelBuilder
			.executor(this.executor)
			.defaultLoadBalancingPolicy("round_robin")
			.intercept(new ClientSessionInterceptor(configuration));

		final ClientTracingContext context = getClientTracingContext(configuration);
		if (configuration.openTelemetryInstance() != null) {
			context.setOpenTelemetry(configuration.openTelemetryInstance());
		}

		final NettyChannelBuilder finalNettyChannelBuilder = nettyChannelBuilder;
		ofNullable(grpcConfigurator)
			.ifPresent(it -> it.accept(finalNettyChannelBuilder));
		this.reflectionLookup = new ReflectionLookup(configuration.reflectionLookupBehaviour());
		this.channelPool = new ChannelPool(nettyChannelBuilder, 10);
		this.terminationCallback = () -> {
			try {
				Assert.isTrue(
					this.channelPool.awaitTermination(configuration.timeout(), configuration.timeoutUnit()),
					() -> new EvitaClientTimedOutException(configuration.timeout(), configuration.timeoutUnit())
				);
			} catch (InterruptedException e) {
				// terminated
				Thread.currentThread().interrupt();
			}
		};
		this.timeout = ThreadLocal.withInitial(() -> {
			final LinkedList<Timeout> timeouts = new LinkedList<>();
			timeouts.add(new Timeout(configuration.timeout(), configuration.timeoutUnit()));
			return timeouts;
		});
		this.active.set(true);
		this.clientTaskTracker = new ClientTaskTracker(
			this, configuration.trackedTaskLimit(), 2000
		);

		try {
			final SystemStatus systemStatus = this.getSystemStatus();
			final SemVer serverVersion;
			final SemVer clientVersion;

			try {
				serverVersion = SemVer.fromString(systemStatus.version());
			} catch (InvalidEvitaVersionException e) {
				log.warn("Server version `{}` is not a valid semantic version. Aborting version check, this situation may lead to compatibility issues.", systemStatus.version());
				return;
			}
			try {
				clientVersion = SemVer.fromString(getVersion());
			} catch (InvalidEvitaVersionException e) {
				log.warn("Client version `{}` is not a valid semantic version. Aborting version check, this situation may lead to compatibility issues.", getVersion());
				return;
			}

			final int comparisonResult = SemVer.compare(clientVersion, serverVersion);
			if (comparisonResult < 0) {
				log.warn(
					"Client version {} is lower than the server version {}. " +
						"It may not represent a compatibility issue, but it is recommended to update " +
						"the client to the latest version.",
					clientVersion,
					serverVersion
				);
			} else if (comparisonResult > 0) {
				if (clientVersion.snapshot() || serverVersion.snapshot()) {
					log.warn(
						"Client version `{}` is higher than server version `{}`. " +
							"This situation might lead to compatibility issues, but there is SNAPSHOT version involved " +
							"and some kind of testing is probably happening.",
						clientVersion,
						serverVersion
					);
				} else {
					throw new IncompatibleClientException(
						"Client version `" + clientVersion + "` is higher than server version `" + serverVersion + "`. " +
							"This situation will probably lead to compatibility issues. Please update the server to " +
							"the latest version.",
						"Incompatible client version!"
					);
				}
			}
		} catch (IncompatibleClientException ex) {
			throw ex;
		} catch (Exception ex) {
			log.error("Failed to connect to evitaDB server. Please check the connection settings.", ex);
		}
	}

	@Override
	public boolean isActive() {
		return active.get();
	}

	@Nonnull
	@Override
	public EvitaClientSession createSession(@Nonnull SessionTraits traits) {
		assertActive();
		final GrpcEvitaSessionResponse grpcResponse;

		if (traits.isReadWrite()) {
			if (traits.isBinary()) {
				grpcResponse = executeWithEvitaService(evitaService -> {
					final Timeout timeoutToUse = this.timeout.get().peek();
					return evitaService.createBinaryReadWriteSession(
						GrpcEvitaSessionRequest.newBuilder()
							.setCatalogName(traits.catalogName())
							.setCommitBehavior(EvitaEnumConverter.toGrpcCommitBehavior(traits.commitBehaviour()))
							.setDryRun(traits.isDryRun())
							.build()
					).get(timeoutToUse.timeout(), timeoutToUse.timeoutUnit());
				});
			} else {
				grpcResponse = executeWithEvitaService(evitaService -> {
					final Timeout timeoutToUse = this.timeout.get().peek();
					return evitaService.createReadWriteSession(
						GrpcEvitaSessionRequest.newBuilder()
							.setCatalogName(traits.catalogName())
							.setCommitBehavior(EvitaEnumConverter.toGrpcCommitBehavior(traits.commitBehaviour()))
							.setDryRun(traits.isDryRun())
							.build()
					).get(timeoutToUse.timeout(), timeoutToUse.timeoutUnit());
				});
			}
		} else {
			if (traits.isBinary()) {
				grpcResponse = executeWithEvitaService(evitaService -> {
					final Timeout timeoutToUse = this.timeout.get().peek();
					return evitaService.createBinaryReadOnlySession(
						GrpcEvitaSessionRequest.newBuilder()
							.setCatalogName(traits.catalogName())
							.setDryRun(traits.isDryRun())
							.build()
					).get(timeoutToUse.timeout(), timeoutToUse.timeoutUnit());
				});
			} else {
				grpcResponse = executeWithEvitaService(evitaService -> {
					final Timeout timeoutToUse = this.timeout.get().peek();
					return evitaService.createReadOnlySession(
						GrpcEvitaSessionRequest.newBuilder()
							.setCatalogName(traits.catalogName())
							.setDryRun(traits.isDryRun())
							.build()
					).get(timeoutToUse.timeout(), timeoutToUse.timeoutUnit());
				});
			}
		}
		final EvitaClientSession evitaClientSession = new EvitaClientSession(
			this,
			this.clientTaskTracker,
			this.entitySchemaCache.computeIfAbsent(
				traits.catalogName(),
				catalogName -> new EvitaEntitySchemaCache(catalogName, this.reflectionLookup)
			),
			this.channelPool,
			traits.catalogName(),
			EvitaEnumConverter.toCatalogState(grpcResponse.getCatalogState()),
			ofNullable(grpcResponse.getCatalogId())
				.filter(it -> !it.isBlank())
				.map(UUIDUtil::uuid)
				.orElseGet(UUIDUtil::randomUUID),
			UUIDUtil.uuid(grpcResponse.getSessionId()),
			EvitaEnumConverter.toCommitBehavior(grpcResponse.getCommitBehaviour()),
			traits,
			evitaSession -> {
				this.activeSessions.remove(evitaSession.getId());
				ofNullable(traits.onTermination())
					.ifPresent(it -> it.onTermination(evitaSession));
			},
			this.timeout.get().peek()
		);

		this.activeSessions.put(evitaClientSession.getId(), evitaClientSession);
		return evitaClientSession;
	}

	@Nonnull
	@Override
	public Optional<EvitaSessionContract> getSessionById(@Nonnull String catalogName, @Nonnull UUID uuid) {
		return ofNullable(this.activeSessions.get(uuid))
			.filter(it -> catalogName.equals(it.getCatalogName()));
	}

	@Override
	public void terminateSession(@Nonnull EvitaSessionContract session) {
		assertActive();
		if (session instanceof EvitaClientSession evitaClientSession) {
			evitaClientSession.close();
		} else {
			throw new EvitaInvalidUsageException(
				"Passed session is expected to be `EvitaClientSession`, but it is not (" + session.getClass().getSimpleName() + ")!"
			);
		}
	}

	@Nonnull
	@Override
	public Set<String> getCatalogNames() {
		assertActive();
		final GrpcCatalogNamesResponse grpcResponse = executeWithEvitaService(
			evitaService -> {
				final Timeout timeoutToUse = this.timeout.get().peek();
				return evitaService.getCatalogNames(Empty.newBuilder().build())
					.get(timeoutToUse.timeout(), timeoutToUse.timeoutUnit());
			}
		);
		return new LinkedHashSet<>(
			grpcResponse.getCatalogNamesList()
		);
	}

	@Nonnull
	@Override
	public CatalogSchemaBuilder defineCatalog(@Nonnull String catalogName) {
		assertActive();
		if (!getCatalogNames().contains(catalogName)) {
			update(new CreateCatalogSchemaMutation(catalogName));
		}
		return queryCatalog(
			catalogName,
			session -> {
				return ((EvitaClientSession) session).getCatalogSchema(this);
			}
		).openForWrite();
	}

	@Override
	public void renameCatalog(@Nonnull String catalogName, @Nonnull String newCatalogName) {
		assertActive();
		final GrpcRenameCatalogRequest request = GrpcRenameCatalogRequest.newBuilder()
			.setCatalogName(catalogName)
			.setNewCatalogName(newCatalogName)
			.build();
		final GrpcRenameCatalogResponse grpcResponse = executeWithEvitaService(
			evitaService -> {
				final Timeout timeoutToUse = this.timeout.get().peek();
				return evitaService.renameCatalog(request)
					.get(timeoutToUse.timeout(), timeoutToUse.timeoutUnit());
			}
		);
		final boolean success = grpcResponse.getSuccess();
		if (success) {
			this.entitySchemaCache.remove(catalogName);
			this.entitySchemaCache.remove(newCatalogName);
		}
	}

	@Override
	public void replaceCatalog(@Nonnull String catalogNameToBeReplacedWith, @Nonnull String catalogNameToBeReplaced) {
		assertActive();
		final GrpcReplaceCatalogRequest request = GrpcReplaceCatalogRequest.newBuilder()
			.setCatalogNameToBeReplacedWith(catalogNameToBeReplacedWith)
			.setCatalogNameToBeReplaced(catalogNameToBeReplaced)
			.build();

		final GrpcReplaceCatalogResponse grpcResponse = executeWithEvitaService(
			evitaService -> {
				final Timeout timeoutToUse = this.timeout.get().peek();
				return evitaService.replaceCatalog(request)
					.get(timeoutToUse.timeout(), timeoutToUse.timeoutUnit());
			}
		);
		final boolean success = grpcResponse.getSuccess();
		if (success) {
			this.entitySchemaCache.remove(catalogNameToBeReplaced);
			this.entitySchemaCache.remove(catalogNameToBeReplacedWith);
		}
	}

	@Override
	public boolean deleteCatalogIfExists(@Nonnull String catalogName) {
		assertActive();

		final GrpcDeleteCatalogIfExistsRequest request = GrpcDeleteCatalogIfExistsRequest.newBuilder()
			.setCatalogName(catalogName)
			.build();

		final GrpcDeleteCatalogIfExistsResponse grpcResponse = executeWithEvitaService(
			evitaService -> {
				final Timeout timeoutToUse = this.timeout.get().peek();
				return evitaService.deleteCatalogIfExists(request)
					.get(timeoutToUse.timeout(), timeoutToUse.timeoutUnit());
			}
		);
		final boolean success = grpcResponse.getSuccess();
		if (success) {
			this.entitySchemaCache.remove(catalogName);
		}
		return success;
	}

	@Override
	public void update(@Nonnull TopLevelCatalogSchemaMutation... catalogMutations) {
		assertActive();

		final List<GrpcTopLevelCatalogSchemaMutation> grpcSchemaMutations = Arrays.stream(catalogMutations)
			.map(CATALOG_SCHEMA_MUTATION_CONVERTER::convert)
			.toList();

		final GrpcUpdateEvitaRequest request = GrpcUpdateEvitaRequest.newBuilder()
			.addAllSchemaMutations(grpcSchemaMutations)
			.build();

		executeWithEvitaService(
			evitaService -> {
				final Timeout timeoutToUse = this.timeout.get().peek();
				return evitaService.update(request)
					.get(timeoutToUse.timeout(), timeoutToUse.timeoutUnit());
			}
		);
	}

	@Override
	public <T> T queryCatalog(@Nonnull String catalogName, @Nonnull Function<EvitaSessionContract, T> queryLogic, @Nullable SessionFlags... flags) {
		assertActive();
		try (final EvitaSessionContract session = this.createSession(new SessionTraits(catalogName, flags))) {
			return queryLogic.apply(session);
		}
	}

	@Override
	public void queryCatalog(@Nonnull String
		                         catalogName, @Nonnull Consumer<EvitaSessionContract> queryLogic, @Nullable SessionFlags... flags) {
		assertActive();
		try (final EvitaSessionContract session = this.createSession(new SessionTraits(catalogName, flags))) {
			queryLogic.accept(session);
		}
	}

	@Override
	public <T> CompletableFuture<T> queryCatalogAsync(@Nonnull String catalogName, @Nonnull Function<EvitaSessionContract, T> queryLogic, @Nullable SessionFlags... flags) {
		return CompletableFuture.supplyAsync(
			() -> {
				assertActive();
				try (final EvitaSessionContract session = this.createSession(new SessionTraits(catalogName, flags))) {
					return queryLogic.apply(session);
				}
			},
			this.executor
		);
	}

	@Override
	public <T> T updateCatalog(
		@Nonnull String catalogName,
		@Nonnull Function<EvitaSessionContract, T> updater,
		@Nonnull CommitBehavior commitBehaviour,
		@Nullable SessionFlags... flags
	) {
		assertActive();
		final SessionTraits traits = new SessionTraits(
			catalogName,
			commitBehaviour,
			flags == null ?
				new SessionFlags[]{SessionFlags.READ_WRITE} :
				ArrayUtils.insertRecordIntoArray(SessionFlags.READ_WRITE, flags, flags.length)
		);
		try (final EvitaSessionContract session = this.createSession(traits)) {
			return updater.apply(session);
		}
	}

	@Override
	public <T> CompletableFuture<T> updateCatalogAsync(
		@Nonnull String catalogName,
		@Nonnull Function<EvitaSessionContract, T> updater,
		@Nonnull CommitBehavior commitBehaviour,
		@Nullable SessionFlags... flags
	) {
		assertActive();
		final SessionTraits traits = new SessionTraits(
			catalogName,
			commitBehaviour,
			flags == null ?
				new SessionFlags[]{SessionFlags.READ_WRITE} :
				ArrayUtils.insertRecordIntoArray(SessionFlags.READ_WRITE, flags, flags.length)
		);
		final EvitaSessionContract session = this.createSession(traits);
		final CompletableFuture<Long> closeFuture;
		final T resultValue;
		try {
			resultValue = updater.apply(session);
		} finally {
			closeFuture = session.closeNow(commitBehaviour);
		}

		// join the transaction future and return
		final CompletableFuture<T> result = new CompletableFuture<>();
		closeFuture.whenComplete((txId, ex) -> {
			if (ex != null) {
				result.completeExceptionally(ex);
			} else {
				result.complete(resultValue);
			}
		});
		return result;
	}

	@Override
	public void updateCatalog(@Nonnull String catalogName, @Nonnull Consumer<EvitaSessionContract> updater, @Nonnull CommitBehavior commitBehaviour, @Nullable SessionFlags... flags) {
		assertActive();
		final SessionTraits traits = new SessionTraits(
			catalogName,
			commitBehaviour,
			flags == null ?
				new SessionFlags[]{SessionFlags.READ_WRITE} :
				ArrayUtils.insertRecordIntoArray(SessionFlags.READ_WRITE, flags, flags.length)
		);
		try (final EvitaSessionContract session = this.createSession(traits)) {
			updater.accept(session);
		}
	}

	@Override
	public CompletableFuture<Long> updateCatalogAsync(
		@Nonnull String catalogName,
		@Nonnull Consumer<EvitaSessionContract> updater,
		@Nonnull CommitBehavior commitBehaviour,
		@Nullable SessionFlags... flags
	) throws TransactionException {
		assertActive();
		final SessionTraits traits = new SessionTraits(
			catalogName,
			commitBehaviour,
			flags == null ?
				new SessionFlags[]{SessionFlags.READ_WRITE} :
				ArrayUtils.insertRecordIntoArray(SessionFlags.READ_WRITE, flags, flags.length)
		);
		final EvitaSessionContract session = this.createSession(traits);
		final CompletableFuture<Long> closeFuture;
		try {
			updater.accept(session);
		} finally {
			closeFuture = session.closeNow(commitBehaviour);
		}

		return closeFuture;
	}

	@Nonnull
	@Override
	public CompletableFuture<FileForFetch> backupCatalog(
		@Nonnull String catalogName,
		@Nullable OffsetDateTime pastMoment,
		boolean includingWAL
	) throws TemporalDataNotAvailableException {
		assertActive();
		try (final EvitaSessionContract session = this.createReadWriteSession(catalogName)) {
			final Task<?, FileForFetch> resultTask = session.backupCatalog(pastMoment, includingWAL);
			return resultTask.getFutureResult();
		}
	}

	@Nonnull
	@Override
	public Task<?, Void> restoreCatalog(
		@Nonnull String catalogName,
		long totalBytesExpected,
		@Nonnull InputStream inputStream
	) throws UnexpectedIOException {
		assertActive();

		return executeWithAsyncEvitaService(
			evitaService -> {
				final CompletableFuture<TaskStatus<?, ?>> result = new CompletableFuture<>();
				final AtomicLong bytesSent = new AtomicLong(0);
				final AtomicReference<TaskStatus<?, ?>> taskStatus = new AtomicReference<>();
				final StreamObserver<GrpcRestoreCatalogRequest> requestObserver = evitaService.restoreCatalog(
					new StreamObserver<>() {
						final AtomicLong bytesReceived = new AtomicLong(0);

						@Override
						public void onNext(GrpcRestoreCatalogResponse value) {
							bytesReceived.accumulateAndGet(value.getRead(), Math::max);
							if (value.hasTask()) {
								taskStatus.set(EvitaDataTypesConverter.toTaskStatus(value.getTask()));
							}
						}

						@Override
						public void onError(Throwable t) {
							log.error("Error occurred during catalog restoration: {}", t.getMessage(), t);
							result.completeExceptionally(t);
						}

						@Override
						public void onCompleted() {
							if (bytesSent.get() == bytesReceived.get()) {
								result.complete(taskStatus.get());
							} else {
								result.completeExceptionally(
									new UnexpectedIOException(
										"Number of bytes sent and received during catalog restoration does not match (sent " + bytesSent.get() + ", received " + bytesReceived.get() + ")!",
										"Number of bytes sent and received during catalog restoration does not match!"
									)
								);
							}
						}
					}
				);

				// Send data in chunks
				final ByteBuffer buffer = ByteBuffer.allocate(65_536);
				try (inputStream) {
					while (inputStream.available() > 0) {
						final int read = inputStream.read(buffer.array());
						if (read == -1) {
							requestObserver.onCompleted();
						}
						buffer.limit(read);
						requestObserver.onNext(
							GrpcRestoreCatalogRequest.newBuilder()
								.setCatalogName(catalogName)
								.setBackupFile(ByteString.copyFrom(buffer))
								.build()
						);
						buffer.clear();
						bytesSent.addAndGet(read);
					}

					requestObserver.onCompleted();
				} catch (IOException e) {
					requestObserver.onError(e);
					throw new RuntimeException(e);
				}

				//noinspection unchecked
				return (Task<?, Void>) clientTaskTracker.createTask(
					Objects.requireNonNull(result.get())
				);
			}
		);
	}

	@Nonnull
	@Override
	public Task<?, Void> restoreCatalog(@Nonnull String catalogName, @Nonnull UUID fileId) throws FileForFetchNotFoundException {
		assertActive();

		final GrpcRestoreCatalogResponse response = executeWithEvitaService(
			evitaService -> {
				final Timeout timeoutToUse = this.timeout.get().peek();
				return evitaService.restoreCatalogFromServerFile(
					GrpcRestoreCatalogFromServerFileRequest.newBuilder()
						.setFileId(toGrpcUuid(fileId))
						.setCatalogName(catalogName)
						.build()
				).get(timeoutToUse.timeout(), timeoutToUse.timeoutUnit());
			}
		);

		//noinspection unchecked
		return (Task<?, Void>) this.clientTaskTracker.createTask(
			EvitaDataTypesConverter.toTaskStatus(response.getTask())
		);
	}

	@Nonnull
	@Override
	public PaginatedList<TaskStatus<?, ?>> listTaskStatuses(int page, int pageSize) {
		assertActive();

		final GrpcTaskStatusesResponse response = executeWithEvitaService(
			evitaService -> {
				final Timeout timeoutToUse = this.timeout.get().peek();
				return evitaService.listTaskStatuses(
					GrpcTaskStatusesRequest.newBuilder()
						.setPageNumber(page)
						.setPageSize(pageSize)
						.build()
				).get(timeoutToUse.timeout(), timeoutToUse.timeoutUnit());
			}
		);

		return new PaginatedList<>(
			response.getPageNumber(),
			response.getPageSize(),
			response.getTotalNumberOfRecords(),
			response.getTaskStatusList()
				.stream()
				.map(EvitaDataTypesConverter::toTaskStatus)
				.collect(Collectors.toCollection(ArrayList::new))
		);
	}

	@Nonnull
	@Override
	public Optional<TaskStatus<?, ?>> getTaskStatus(@Nonnull UUID jobId) {
		assertActive();

		final GrpcTaskStatusResponse response = executeWithEvitaService(
			evitaService -> {
				final Timeout timeoutToUse = this.timeout.get().peek();
				return evitaService.getTaskStatus(
					GrpcTaskStatusRequest.newBuilder()
						.setTaskId(toGrpcUuid(jobId))
						.build()
				).get(timeoutToUse.timeout(), timeoutToUse.timeoutUnit());
			}
		);

		return response.hasTaskStatus() ?
			Optional.of(EvitaDataTypesConverter.toTaskStatus(response.getTaskStatus())) : Optional.empty();
	}

	@Nonnull
	@Override
	public Collection<TaskStatus<?, ?>> getTaskStatuses(@Nonnull UUID... jobId) {
		assertActive();

		final GrpcSpecifiedTaskStatusesResponse response = executeWithEvitaService(
			evitaService -> {
				final Timeout timeoutToUse = this.timeout.get().peek();
				final Builder builder = GrpcSpecifiedTaskStatusesRequest.newBuilder();
				for (UUID id : jobId) {
					builder.addTaskIds(toGrpcUuid(id));
				}
				return evitaService.getTaskStatuses(
					builder.build()
				).get(timeoutToUse.timeout(), timeoutToUse.timeoutUnit());
			}
		);

		return response.getTaskStatusList()
				.stream()
				.map(EvitaDataTypesConverter::toTaskStatus)
				.collect(Collectors.toCollection(ArrayList::new));
	}

	@Override
	public boolean cancelTask(@Nonnull UUID jobId) {
		assertActive();

		final GrpcCancelTaskResponse response = executeWithEvitaService(
			evitaService -> {
				final Timeout timeoutToUse = this.timeout.get().peek();
				return evitaService.cancelTask(
					GrpcCancelTaskRequest.newBuilder()
						.setTaskId(toGrpcUuid(jobId))
						.build()
				).get(timeoutToUse.timeout(), timeoutToUse.timeoutUnit());
			}
		);

		return response.getSuccess();
	}

	@Nonnull
	@Override
	public PaginatedList<FileForFetch> listFilesToFetch(int page, int pageSize, @Nullable String origin) {
		assertActive();

		final GrpcFilesToFetchResponse response = executeWithEvitaService(
			evitaService -> {
				final Timeout timeoutToUse = this.timeout.get().peek();
				return evitaService.listFilesToFetch(
					GrpcFilesToFetchRequest.newBuilder()
						.setPageNumber(page)
						.setPageSize(pageSize)
						.build()
				).get(timeoutToUse.timeout(), timeoutToUse.timeoutUnit());
			}
		);

		return new PaginatedList<>(
			response.getPageNumber(),
			response.getPageSize(),
			response.getTotalNumberOfRecords(),
			response.getFilesToFetchList()
				.stream()
				.map(EvitaDataTypesConverter::toFileForFetch)
				.collect(Collectors.toCollection(ArrayList::new))
		);
	}

	@Nonnull
	@Override
	public Optional<FileForFetch> getFileToFetch(@Nonnull UUID fileId) {
		assertActive();

		final GrpcFileToFetchResponse response = executeWithEvitaService(
			evitaService -> {
				final Timeout timeoutToUse = this.timeout.get().peek();
				return evitaService.getFileToFetch(
					GrpcFileToFetchRequest.newBuilder()
						.setFileId(toGrpcUuid(fileId))
						.build()
				).get(timeoutToUse.timeout(), timeoutToUse.timeoutUnit());
			}
		);

		return response.hasFileToFetch() ?
			Optional.of(EvitaDataTypesConverter.toFileForFetch(response.getFileToFetch())) : Optional.empty();
	}

	@Nonnull
	@Override
	public InputStream fetchFile(@Nonnull UUID fileId) throws FileForFetchNotFoundException, UnexpectedIOException {
		assertActive();
		try {
			// Create a temporary file
			Path tempFile = Files.createTempFile("downloadedFile", ".tmp");
			CompletableFuture<Void> downloadFuture = new CompletableFuture<>();

			// Download the file asynchronously
			executeWithAsyncEvitaService(evitaService -> {
				evitaService.fetchFile(
					GrpcFetchFileRequest.newBuilder().setFileId(toGrpcUuid(fileId)).build(),
					new StreamObserver<>() {
						@Override
						public void onNext(GrpcFetchFileResponse response) {
							try {
								// Write chunks to the temporary file
								Files.write(tempFile, response.getFileContents().toByteArray(), StandardOpenOption.APPEND);
							} catch (IOException e) {
								onError(e);
							}
						}

						@Override
						public void onError(Throwable t) {
							downloadFuture.completeExceptionally(t);
						}

						@Override
						public void onCompleted() {
							downloadFuture.complete(null);
						}
					}
				);
				return null;
			});

			// Wait for the download to complete
			downloadFuture.join();

			// Return an InputStream for the temporary file
			return new FileInputStream(tempFile.toFile()) {
				@Override
				public void close() throws IOException {
					super.close();
					// Cleanup - delete the temporary file after reading
					Files.deleteIfExists(tempFile);
				}
			};
		} catch (IOException e) {
			throw new UnexpectedIOException(
				"Failed to create temporary file or write to it: " + e.getMessage(),
				"Failed to create temporary file or write to it",
				e
			);
		}
	}

	@Override
	public void deleteFile(@Nonnull UUID fileId) throws FileForFetchNotFoundException {
		assertActive();

		final GrpcDeleteFileToFetchResponse response = executeWithEvitaService(
			evitaService -> {
				final Timeout timeoutToUse = this.timeout.get().peek();
				return evitaService.deleteFile(
					GrpcDeleteFileToFetchRequest.newBuilder()
						.setFileId(toGrpcUuid(fileId))
						.build()
				).get(timeoutToUse.timeout(), timeoutToUse.timeoutUnit());
			}
		);

		if (!response.getSuccess()) {
			throw new FileForFetchNotFoundException(fileId);
		}
	}

	@Nonnull
	@Override
	public SystemStatus getSystemStatus() {
		assertActive();

		return executeWithEvitaService(
			evitaService -> {
				final Timeout timeoutToUse = this.timeout.get().peek();
				final GrpcEvitaServerStatusResponse response = evitaService.serverStatus(Empty.newBuilder().build())
					.get(timeoutToUse.timeout(), timeoutToUse.timeoutUnit());
				return new SystemStatus(
					response.getVersion(),
					EvitaDataTypesConverter.toOffsetDateTime(response.getStartedAt()),
					Duration.of(response.getUptime(), ChronoUnit.SECONDS),
					response.getInstanceId(),
					response.getCatalogsCorrupted(),
					response.getCatalogsOk()
				);
			}
		);
	}

	/**
	 * Retrieves the version number of the evitaDB client.
	 *
	 * @return The version number as a string.
	 */
	@Nonnull
	public String getVersion() {
		return VersionUtils.readVersion();
	}

	@Override
	public void close() {
		if (active.compareAndSet(true, false)) {
			this.activeSessions.values().forEach(EvitaSessionContract::close);
			this.activeSessions.clear();
			this.clientTaskTracker.close();
			this.channelPool.shutdown();
			this.terminationCallback.run();
		}
	}

	/**
	 * Method executes lambda using specified timeout for the call ignoring the defaults specified
	 * in {@link EvitaClientConfiguration#timeout()}.
	 *
	 * @param lambda  logic to be executed
	 * @param timeout timeout value
	 * @param unit    time unit of the timeout
	 */
	public void executeWithExtendedTimeout(@Nonnull Runnable lambda, long timeout, @Nonnull TimeUnit unit) {
		try {
			this.timeout.get().push(new Timeout(timeout, unit));
			lambda.run();
		} finally {
			this.timeout.get().pop();
		}
	}

	/**
	 * Method executes lambda using specified timeout for the call ignoring the defaults specified
	 * in {@link EvitaClientConfiguration#timeout()}.
	 *
	 * @param lambda  logic to be executed
	 * @param timeout timeout value
	 * @param unit    time unit of the timeout
	 * @param <T>     type of the result
	 * @return result of the lambda
	 */
	public <T> T executeWithExtendedTimeout(@Nonnull Supplier<T> lambda, long timeout, @Nonnull TimeUnit unit) {
		try {
			this.timeout.get().push(new Timeout(timeout, unit));
			return lambda.get();
		} finally {
			this.timeout.get().pop();
		}
	}

	/**
	 * Verifies this instance is still active.
	 */
	protected void assertActive() {
		if (!active.get()) {
			throw new InstanceTerminatedException("client instance");
		}
	}

	/**
	 * Method that is called within the {@link EvitaClientSession} to apply the wanted logic on a channel retrieved
	 * from a channel pool.
	 *
	 * @param lambda function that holds a logic passed by the caller
	 * @param <T>    return type of the function
	 * @return result of the applied function
	 */
	private <T> T executeWithEvitaService(@Nonnull AsyncCallFunction<EvitaServiceFutureStub, T> lambda) {
		final ManagedChannel managedChannel = this.channelPool.getChannel();
		try {
			return lambda.apply(EvitaServiceGrpc.newFutureStub(managedChannel));
		} catch (StatusRuntimeException statusRuntimeException) {
			final Code statusCode = statusRuntimeException.getStatus().getCode();
			final String description = ofNullable(statusRuntimeException.getStatus().getDescription())
				.orElse("No description.");
			if (statusCode == Code.INVALID_ARGUMENT) {
				final Matcher expectedFormat = ERROR_MESSAGE_PATTERN.matcher(description);
				if (expectedFormat.matches()) {
					throw EvitaInvalidUsageException.createExceptionWithErrorCode(
						expectedFormat.group(2), expectedFormat.group(1)
					);
				} else {
					throw new EvitaInvalidUsageException(description);
				}
			} else {
				final Matcher expectedFormat = ERROR_MESSAGE_PATTERN.matcher(description);
				if (expectedFormat.matches()) {
					throw GenericEvitaInternalError.createExceptionWithErrorCode(
						expectedFormat.group(2), expectedFormat.group(1)
					);
				} else {
					throw new GenericEvitaInternalError(description);
				}
			}
		} catch (EvitaInvalidUsageException | EvitaInternalError evitaError) {
			throw evitaError;
		} catch (Throwable e) {
			log.error("Unexpected internal Evita error occurred: {}", e.getMessage(), e);
			throw new GenericEvitaInternalError(
				"Unexpected internal Evita error occurred: " + e.getMessage(),
				"Unexpected internal Evita error occurred.",
				e
			);
		} finally {
			this.channelPool.releaseChannel(managedChannel);
		}
	}

	/**
	 * Method that is called within the {@link EvitaClientSession} to apply the wanted logic on a channel retrieved
	 * from a channel pool.
	 *
	 * @param lambda function that holds a logic passed by the caller
	 * @param <T>    return type of the function
	 * @return result of the applied function
	 */
	private <T> T executeWithAsyncEvitaService(@Nonnull AsyncCallFunction<EvitaServiceStub, T> lambda) {
		final ManagedChannel managedChannel = this.channelPool.getChannel();
		try {
			return lambda.apply(EvitaServiceGrpc.newStub(managedChannel));
		} catch (StatusRuntimeException statusRuntimeException) {
			final Code statusCode = statusRuntimeException.getStatus().getCode();
			final String description = ofNullable(statusRuntimeException.getStatus().getDescription())
				.orElse("No description.");
			if (statusCode == Code.INVALID_ARGUMENT) {
				final Matcher expectedFormat = ERROR_MESSAGE_PATTERN.matcher(description);
				if (expectedFormat.matches()) {
					throw EvitaInvalidUsageException.createExceptionWithErrorCode(
						expectedFormat.group(2), expectedFormat.group(1)
					);
				} else {
					throw new EvitaInvalidUsageException(description);
				}
			} else {
				final Matcher expectedFormat = ERROR_MESSAGE_PATTERN.matcher(description);
				if (expectedFormat.matches()) {
					throw GenericEvitaInternalError.createExceptionWithErrorCode(
						expectedFormat.group(2), expectedFormat.group(1)
					);
				} else {
					throw new GenericEvitaInternalError(description);
				}
			}
		} catch (EvitaInvalidUsageException | EvitaInternalError evitaError) {
			throw evitaError;
		} catch (Throwable e) {
			log.error("Unexpected internal Evita error occurred: {}", e.getMessage(), e);
			throw new GenericEvitaInternalError(
				"Unexpected internal Evita error occurred: " + e.getMessage(),
				"Unexpected internal Evita error occurred.",
				e
			);
		} finally {
			this.channelPool.releaseChannel(managedChannel);
		}
	}

}
