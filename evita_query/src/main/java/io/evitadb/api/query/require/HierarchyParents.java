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

package io.evitadb.api.query.require;

import io.evitadb.api.query.Constraint;
import io.evitadb.api.query.RequireConstraint;
import io.evitadb.api.query.descriptor.ConstraintDomain;
import io.evitadb.api.query.descriptor.annotation.ConstraintDef;
import io.evitadb.utils.ArrayUtils;
import io.evitadb.utils.Assert;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serial;
import java.io.Serializable;

/**
 * TOBEDONE JNO: docs
 *
 * @author Jan Novotný (novotny@fg.cz), FG Forrest a.s. (c) 2023
 */
@ConstraintDef(
	name = "parents",
	shortDescription = "The constraint triggers computing the hierarchy parent axis starting at currently requested hierarchy node in filter by constraint.",
	supportedIn = ConstraintDomain.HIERARCHY
)
public class HierarchyParents extends AbstractRequireConstraintContainer implements HierarchyRequireConstraint {
	@Serial private static final long serialVersionUID = -6336717342562034135L;
	private static final String CONSTRAINT_NAME = "parents";

	private HierarchyParents(@Nonnull String outputName, @Nonnull RequireConstraint[] children) {
		super(CONSTRAINT_NAME, new Serializable[] {outputName}, children);
	}

	public HierarchyParents(@Nonnull String outputName) {
		super(CONSTRAINT_NAME, new Serializable[] {outputName});
	}

	public HierarchyParents(@Nonnull String outputName, @Nonnull HierarchyOutputRequireConstraint... requirements) {
		super(CONSTRAINT_NAME, new Serializable[] {outputName}, requirements);
	}

	public HierarchyParents(@Nonnull String outputName, @Nonnull EntityFetch entityFetch) {
		super(CONSTRAINT_NAME, new Serializable[] {outputName}, entityFetch);
	}

	public HierarchyParents(@Nonnull String outputName, @Nonnull EntityFetch entityFetch, @Nonnull HierarchyOutputRequireConstraint... requirements) {
		super(
			CONSTRAINT_NAME,
			new Serializable[] {outputName},
			ArrayUtils.mergeArrays(
				new RequireConstraint[]{entityFetch},
				requirements
			)
		);
	}

	/**
	 * Returns the key the computed extra result should be registered to.
	 */
	@Nonnull
	public String getOutputName() {
		return (String) getArguments()[0];
	}

	/**
	 * Returns the condition that limits the top-down hierarchy traversal.
	 */
	@Nullable
	public HierarchyStopAt getStopAt() {
		for (RequireConstraint constraint : getChildren()) {
			if (constraint instanceof HierarchyStopAt hierarchyStopAt) {
				return hierarchyStopAt;
			}
		}
		return null;
	}

	/**
	 * Returns the constraint that defines whether siblings of all (or specific) returned parent nodes should
	 * be returned and what content requirements are connected with them.
	 */
	@Nullable
	public HierarchySiblings getSiblings() {
		for (RequireConstraint constraint : getChildren()) {
			if (constraint instanceof HierarchySiblings hierarchySiblings) {
				return hierarchySiblings;
			}
		}
		return null;
	}

	/**
	 * Returns content requirements for hierarchy entities.
	 */
	@Nullable
	public EntityFetch getEntityFetch() {
		for (RequireConstraint constraint : getChildren()) {
			if (constraint instanceof EntityFetch entityFetch) {
				return entityFetch;
			}
		}
		return null;
	}

	/**
	 * Returns true if the hierarchy entities should be accompanied with the count of their valid immediate children.
	 */
	public boolean isStatisticRequired() {
		for (RequireConstraint constraint : getChildren()) {
			if (constraint instanceof HierarchyStatistics) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean isApplicable() {
		return isArgumentsNonNull() && getArguments().length == 1;
	}

	@Nonnull
	@Override
	public RequireConstraint getCopyWithNewChildren(@Nonnull RequireConstraint[] children, @Nonnull Constraint<?>[] additionalChildren) {
		for (Serializable requireConstraint : children) {
			Assert.isTrue(
				requireConstraint instanceof HierarchyOutputRequireConstraint ||
					requireConstraint instanceof EntityFetch,
				"Constraint HierarchyParents accepts only HierarchyStopAt, HierarchyStopAt, HierarchySiblings and EntityFetch as inner constraints!"
			);
		}
		Assert.isTrue(ArrayUtils.isEmpty(additionalChildren), "Inner constraints of different type than `require` are not expected.");
		return new HierarchyParents(getOutputName(), children);
	}

	@Nonnull
	@Override
	public RequireConstraint cloneWithArguments(@Nonnull Serializable[] newArguments) {
		Assert.isTrue(
			newArguments.length == 1 && newArguments[0] instanceof String,
			"HierarchyParents container accepts only single String argument!"
		);
		return new HierarchyParents((String) newArguments[0], getChildren());
	}
	
}
