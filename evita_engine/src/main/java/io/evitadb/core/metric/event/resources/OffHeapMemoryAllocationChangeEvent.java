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
 *   https://github.com/FgForrest/evitaDB/blob/main/LICENSE
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package io.evitadb.core.metric.event.resources;

import io.evitadb.api.configuration.metric.MetricType;
import io.evitadb.core.metric.annotation.ExportMetric;
import jdk.jfr.Description;
import jdk.jfr.Label;
import jdk.jfr.Name;
import lombok.Getter;

import javax.annotation.Nonnull;

/**
 * Event that is fired when a transaction passed conflict resolution stage.
 *
 * @author Jan Novotný (novotny@fg.cz), FG Forrest a.s. (c) 2024
 */
@Name(AbstractResourcesEvent.PACKAGE_NAME + ".OffHeapMemoryAllocationChangeEvent")
@Description("Event that is fired when off-heap memory allocation changes.")
@Label("Off-heap memory allocation change")
@Getter
public class OffHeapMemoryAllocationChangeEvent extends AbstractResourcesEvent {
	/**
	 * Amount of memory allocated for off-heap storage in bytes.
	 */
	@ExportMetric(metricType = MetricType.GAUGE)
	@Label("Allocated memory bytes")
	private final long allocatedMemoryBytes;

	/**
	 * Amount of memory used for off-heap storage in bytes.
	 */
	@ExportMetric(metricType = MetricType.GAUGE)
	@Label("Used memory bytes")
	private final long usedMemoryBytes;


	public OffHeapMemoryAllocationChangeEvent(@Nonnull String catalogName, long allocatedMemoryBytes, long usedMemoryBytes) {
		super(catalogName);
		this.allocatedMemoryBytes = allocatedMemoryBytes;
		this.usedMemoryBytes = usedMemoryBytes;
	}
}
