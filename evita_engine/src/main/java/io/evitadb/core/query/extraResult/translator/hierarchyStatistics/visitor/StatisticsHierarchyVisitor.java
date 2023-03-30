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

package io.evitadb.core.query.extraResult.translator.hierarchyStatistics.visitor;

import io.evitadb.api.query.RequireConstraint;
import io.evitadb.api.requestResponse.data.EntityClassifier;
import io.evitadb.api.requestResponse.data.SealedEntity;
import io.evitadb.api.requestResponse.extraResult.HierarchyStatistics.LevelInfo;
import io.evitadb.core.query.extraResult.translator.hierarchyStatistics.producer.HierarchyEntityFetcher;
import io.evitadb.core.query.extraResult.translator.hierarchyStatistics.producer.HierarchyEntityPredicate;
import io.evitadb.index.bitmap.Bitmap;
import io.evitadb.index.bitmap.RoaringBitmapBackedBitmap;
import io.evitadb.index.hierarchy.HierarchyNode;
import io.evitadb.index.hierarchy.HierarchyVisitor;
import lombok.RequiredArgsConstructor;
import org.roaringbitmap.RoaringBitmap;

import javax.annotation.Nonnull;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntFunction;

/**
 * This {@link HierarchyVisitor} implementation is called for each hierarchical entity and cooperates
 * with {@link Accumulator} to compose a tree of {@link LevelInfo} objects.
 */
@RequiredArgsConstructor
public class StatisticsHierarchyVisitor implements HierarchyVisitor {
	/**
	 * Contains true if hierarchy statistics should be stripped of results with zero occurrences.
	 */
	private final boolean removeEmptyResults;
	/**
	 * Predicate is used to filter out hierarchical entities that doesn't match the language requirement.
	 */
	@Nonnull private final HierarchyEntityPredicate entityPredicate;
	/**
	 * Contains bitmap of entity primary keys that fulfills the filter of the query.
	 */
	@Nonnull private final RoaringBitmap filteredEntityPks;
	/**
	 * Deque of accumulators allow to compose a tree of results
	 */
	@Nonnull private final Deque<Accumulator> accumulator;
	/**
	 * Contains a function that produces bitmap of queried entity ids connected with particular hierarchical entity.
	 */
	@Nonnull private final IntFunction<Bitmap> hierarchyReferencingEntityPks;
	/**
	 * Function that allows to fetch {@link SealedEntity} for `entityType` + `primaryKey` combination. SealedEntity
	 * is fetched to the depth specified by {@link RequireConstraint[]}.
	 */
	@Nonnull private final HierarchyEntityFetcher entityFetcher;

	@Override
	public void visit(@Nonnull HierarchyNode node, int level, int distance, @Nonnull Runnable childrenTraverser) {
		final int entityPrimaryKey = node.entityPrimaryKey();
		// check whether the hierarchical entity passes the language test
		if (entityPredicate.test(entityPrimaryKey, level, distance)) {
			// get all queried entity primary keys that refer to this hierarchical node
			final Bitmap allEntitiesReferencingEntity = hierarchyReferencingEntityPks.apply(entityPrimaryKey);
			// now combine them with primary keys that are really returned by the query and compute matching count
			final int matchingCount = RoaringBitmap.and(
				RoaringBitmapBackedBitmap.getRoaringBitmap(allEntitiesReferencingEntity),
				filteredEntityPks
			).getCardinality();

			// now fetch the appropriate form of the hierarchical entity
			final EntityClassifier hierarchyEntity = entityFetcher.apply(entityPrimaryKey);
			// and create element in accumulator that will be filled in
			accumulator.push(new Accumulator(hierarchyEntity, matchingCount));
			// traverse subtree - filling up the accumulator on previous row
			childrenTraverser.run();
			// now remove current accumulator from stack
			final Accumulator finalizedAccumulator = accumulator.pop();
			// and if its cardinality is greater than zero (contains at least one queried entity)
			// add it to the result
			final Accumulator topAccumulator = Objects.requireNonNull(accumulator.peek());
			if (removeEmptyResults) {
				Optional.of(finalizedAccumulator.toLevelInfo())
					.filter(it -> it.queriedEntityCount() > 0)
					.ifPresent(topAccumulator::add);
			} else {
				topAccumulator.add(finalizedAccumulator.toLevelInfo());
			}
		}
	}
}
