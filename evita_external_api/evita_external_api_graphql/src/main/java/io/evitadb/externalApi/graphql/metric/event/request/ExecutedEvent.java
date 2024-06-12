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

package io.evitadb.externalApi.graphql.metric.event.request;

import graphql.language.OperationDefinition.Operation;
import io.evitadb.api.configuration.metric.MetricType;
import io.evitadb.api.observability.annotation.ExportDurationMetric;
import io.evitadb.api.observability.annotation.ExportInvocationMetric;
import io.evitadb.api.observability.annotation.ExportMetric;
import io.evitadb.api.observability.annotation.ExportMetricLabel;
import io.evitadb.api.observability.annotation.HistogramSettings;
import io.evitadb.externalApi.graphql.exception.GraphQLInternalError;
import io.evitadb.externalApi.graphql.io.GraphQLInstanceType;
import io.evitadb.utils.Assert;
import jdk.jfr.Description;
import jdk.jfr.Label;
import jdk.jfr.Name;
import lombok.Getter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * JFR Event fired when GQL request is full executed and its response sent to client.
 *
 * @author Lukáš Hornych, 2024
 */
@Name(AbstractGraphQLRequestEvent.PACKAGE_NAME + ".Executed")
@Description("Event that is fired when a GraphQL request is executed.")
@ExportInvocationMetric(label = "GraphQL request executed total")
@ExportDurationMetric(label = "GraphQL request execution duration")
@Label("GraphQL request executed")
@Getter
public class ExecutedEvent extends AbstractGraphQLRequestEvent {

	/**
	 * Operation type specified by user in GQL request.
	 */
	@Label("Operation type")
	@Name("operationType")
	@ExportMetricLabel
	@Nullable
	String operationType;

	/**
	 * The name of the catalog the transaction relates to.
	 */
	@Label("Catalog")
	@Name("catalogName")
	@ExportMetricLabel
	@Nullable
	String catalogName;

	/**
	 * Operation name specified by user in GQL request.
	 */
	@Label("GraphQL operation")
	@Name("operationName")
	@ExportMetricLabel
	@Nullable
	String operationName;

	@Label("Response status")
	@Name("responseStatus")
	@ExportMetricLabel
	@Nonnull
	String responseStatus = ResponseStatus.OK.name();

	/**
	 * Process started timestamp.
	 */
	private final long processStarted;

	@Label("Input deserialization duration in milliseconds")
	@ExportMetric(metricType = MetricType.HISTOGRAM)
	@HistogramSettings(factor = 1.9)
	private long inputDeserializationDurationMilliseconds;

	private long preparationStarted;
	@Label("Request execution preparation duration in milliseconds")
	@ExportMetric(metricType = MetricType.HISTOGRAM)
	@HistogramSettings(factor = 1.9)
	private long preparationDurationMilliseconds;

	private long parseStarted;
	@Label("Request parsing duration in milliseconds")
	@ExportMetric(metricType = MetricType.HISTOGRAM)
	@HistogramSettings(factor = 1.9)
	private long parseDurationMilliseconds;

	private long validationStarted;
	@Label("Request validation duration in milliseconds")
	@ExportMetric(metricType = MetricType.HISTOGRAM)
	@HistogramSettings(factor = 1.9)
	private long validationDurationMilliseconds;

	private long operationExecutionStarted;
	@Label("Request operation execution duration in milliseconds")
	@ExportMetric(metricType = MetricType.HISTOGRAM)
	@HistogramSettings(factor = 1.9)
	private long operationExecutionDurationMilliseconds;

	@Label("Duration of all internal evitaDB input (query, mutations, ...) reconstructions in milliseconds")
	@ExportMetric(metricType = MetricType.HISTOGRAM)
	@HistogramSettings(factor = 1.9)
	private long internalEvitadbInputReconstructionDurationMilliseconds;

	/**
	 * Duration of all internal evitaDB executions in milliseconds.
	 */
	private long internalEvitadbExecutionDurationMilliseconds;

	private long resultSerializationStarted;
	@Label("Request result serialization duration in milliseconds")
	@ExportMetric(metricType = MetricType.HISTOGRAM)
	@HistogramSettings(factor = 1.9)
	private long resultSerializationDurationMilliseconds = -1;

	/**
	 * Overall request execution duration in milliseconds for calculating API overhead.
	 */
	private long executionDurationMilliseconds;

	@Label("Overall request execution API overhead duration in milliseconds")
	@ExportMetric(metricType = MetricType.HISTOGRAM)
	@HistogramSettings(factor = 1.9)
	private long executionApiOverheadDurationMilliseconds;

	@Label("Number of root fields (queries, mutations) processed within single GraphQL request")
	@ExportMetric(metricType = MetricType.GAUGE)
	private int rootFieldsProcessed;

	public ExecutedEvent(@Nonnull GraphQLInstanceType instanceType) {
		super(instanceType);
		this.begin();
		this.processStarted = System.currentTimeMillis();
	}

	/**
	 * Provide operation type for this event. Can be called only once.
	 * @return this
	 */
	@Nonnull
	public ExecutedEvent provideOperationType(@Nonnull Operation operationType) {
		Assert.isPremiseValid(
			this.operationType == null,
			() -> new GraphQLInternalError("Operation type is already set.")
		);
		this.operationType = operationType.toString();
		return this;
	}

	/**
	 * Provide catalog name for this event. Can be called only once.
	 * @return this
	 */
	@Nonnull
	public ExecutedEvent provideCatalogName(@Nonnull String catalogName) {
		Assert.isPremiseValid(
			this.catalogName == null,
			() -> new GraphQLInternalError("Catalog name is already set.")
		);
		this.catalogName = catalogName;
		return this;
	}

	/**
	 * Provide operation name for this event. Can be called only once.
	 * @return this
	 */
	@Nonnull
	public ExecutedEvent provideOperationName(@Nonnull String operationName) {
		this.operationName = operationName;
		return this;
	}

	/**
	 * Provide response status for this event. Can be called only once. Default is {@link ResponseStatus#OK}
	 * @return this
	 */
	@Nonnull
	public ExecutedEvent provideResponseStatus(@Nonnull ResponseStatus responseStatus) {
		this.responseStatus = responseStatus.toString();
		return this;
	}

	@Nonnull
	public ExecutedEvent provideRootFieldsProcessed(int rootFieldsProcessed) {
		this.rootFieldsProcessed = rootFieldsProcessed;
		return this;
	}

	/**
	 * Measures duration of request deserialization from previous state. Should be called only once.
	 * @return this
	 */
	@Nonnull
	public ExecutedEvent finishInputDeserialization() {
		Assert.isPremiseValid(
			this.processStarted != 0,
			() -> new GraphQLInternalError("Process didn't started. Cannot measure input deserialization duration.")
		);
		final long now = System.currentTimeMillis();
		this.inputDeserializationDurationMilliseconds = now - this.processStarted;
		this.preparationStarted = now;
		return this;
	}

	/**
	 * Measures duration of preparation from previous state. Should be called only once.
	 * @return this
	 */
	@Nonnull
	public ExecutedEvent finishPreparation() {
		Assert.isPremiseValid(
			this.preparationStarted != 0,
			() -> new GraphQLInternalError("Preparation didn't started. Cannot measure preparation duration.")
		);
		final long now = System.currentTimeMillis();
		this.preparationDurationMilliseconds = now - this.preparationStarted;
		this.parseStarted = now;
		return this;
	}

	/**
	 * Measures duration of parsing from previous state. Should be called only once.
	 * @return this
	 */
	@Nonnull
	public ExecutedEvent finishParse() {
		Assert.isPremiseValid(
			this.parseStarted != 0,
			() -> new GraphQLInternalError("Parse didn't started. Cannot measure parse duration.")
		);
		final long now = System.currentTimeMillis();
		this.parseDurationMilliseconds = now - this.parseStarted;
		this.validationStarted = now;
		return this;
	}

	/**
	 * Measures duration of validation deserialization from previous state. Should be called only once.
	 * @return this
	 */
	@Nonnull
	public ExecutedEvent finishValidation() {
		Assert.isPremiseValid(
			this.validationStarted != 0,
			() -> new GraphQLInternalError("Validation didn't started. Cannot measure validation duration.")
		);
		final long now = System.currentTimeMillis();
		this.validationDurationMilliseconds = now - this.validationStarted;
		this.operationExecutionStarted = now;
		return this;
	}

	/**
	 * Measures duration of evitaDB input reconstruction within the supplier. Can be called mutliple times, all durations
	 * are summed.
	 * @return this
	 */
	public <T> T measureInternalEvitaDBInputReconstruction(@Nonnull Supplier<T> supplier) {
		final long started = System.currentTimeMillis();
		final T result = supplier.get();
		this.internalEvitadbInputReconstructionDurationMilliseconds += System.currentTimeMillis() - started;
		return result;
	}

	/**
	 * Measures duration of evitaDB execution within the supplier. Can be called mutliple times, all durations
	 * are summed.
	 * @return this
	 */
	public <T> T measureInternalEvitaDBExecution(@Nonnull Supplier<T> supplier) {
		final long started = System.currentTimeMillis();
		final T result = supplier.get();
		this.internalEvitadbExecutionDurationMilliseconds += System.currentTimeMillis() - started;
		return result;
	}

	/**
	 * Measures duration of operation execution from previous state. Should be called only once.
	 * @return this
	 */
	@Nonnull
	public ExecutedEvent finishOperationExecution() {
		Assert.isPremiseValid(
			this.operationExecutionStarted != 0,
			() -> new GraphQLInternalError("Operation execution didn't started. Cannot measure operation execution duration.")
		);
		final long now = System.currentTimeMillis();
		this.operationExecutionDurationMilliseconds = now - this.operationExecutionStarted;
		this.resultSerializationStarted = now;
		return this;
	}

	/**
	 * Measures duration of result serialization from previous state. Should be called only once.
	 * @return this
	 */
	@Nonnull
	public ExecutedEvent finishResultSerialization() {
		Assert.isPremiseValid(
			this.resultSerializationStarted != 0,
			() -> new GraphQLInternalError("Result serialization didn't started. Cannot measure result serialization duration.")
		);
		this.resultSerializationDurationMilliseconds = System.currentTimeMillis() - this.resultSerializationStarted;
		return this;
	}

	/**
	 * Finish the event.
	 * @return this
	 */
	@Nonnull
	public ExecutedEvent finish() {
		this.end();

		Assert.isPremiseValid(
			this.resultSerializationDurationMilliseconds != -1,
			() -> new GraphQLInternalError("Result serialization didn't finished.")
		);
		Assert.isPremiseValid(
			this.processStarted != 0,
			() -> new GraphQLInternalError("Process didn't started. Cannot measure execution duration duration.")
		);
		this.executionDurationMilliseconds = System.currentTimeMillis() - this.processStarted;
		this.executionApiOverheadDurationMilliseconds = this.executionDurationMilliseconds - this.internalEvitadbExecutionDurationMilliseconds;

		return this;
	}

	/**
	 * Response status of GraphQL request
	 */
	public enum ResponseStatus {
		OK, ERROR
	}
}
