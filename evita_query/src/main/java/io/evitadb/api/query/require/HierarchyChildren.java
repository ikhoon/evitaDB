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
	name = "children",
	shortDescription = "The constraint triggers computing the hierarchy subtree starting at currently requested hierarchy node in filter by constraint.",
	supportedIn = ConstraintDomain.HIERARCHY
)
public class HierarchyChildren extends AbstractRequireConstraintContainer implements HierarchyRequireConstraint {
	@Serial private static final long serialVersionUID = 9160175156714580097L;
	private static final String CONSTRAINT_NAME = "children";

	private HierarchyChildren(@Nonnull String outputName, @Nonnull RequireConstraint[] children) {
		super(CONSTRAINT_NAME, new Serializable[]{outputName}, children);
	}

	public HierarchyChildren(@Nonnull String outputName, @Nonnull EntityFetch entityFetch, @Nonnull HierarchyOutputRequireConstraint... requirements) {
		super(
			CONSTRAINT_NAME,
			new Serializable[]{outputName},
			ArrayUtils.mergeArrays(
				new RequireConstraint[]{entityFetch},
				requirements
			)
		);
	}

	public HierarchyChildren(@Nonnull String outputName, @Nonnull HierarchyOutputRequireConstraint... requirements) {
		super(
			CONSTRAINT_NAME,
			new Serializable[]{outputName},
			requirements
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
		for (RequireConstraint requireConstraint : children) {
			Assert.isTrue(
				requireConstraint instanceof HierarchyOutputRequireConstraint ||
					requireConstraint instanceof EntityFetch,
				"Constraint HierarchyChildren accepts only HierarchyStopAt, HierarchyStopAt and EntityFetch as inner constraints!"
			);
		}

		Assert.isTrue(ArrayUtils.isEmpty(additionalChildren), "Inner constraints of different type than `require` are not expected.");
		return new HierarchyChildren(getOutputName(), children);
	}

	@Nonnull
	@Override
	public RequireConstraint cloneWithArguments(@Nonnull Serializable[] newArguments) {
		Assert.isTrue(
			newArguments.length == 1 && newArguments[0] instanceof String,
			"HierarchyChildren container accepts only single String argument!"
		);
		return new HierarchyChildren((String) newArguments[0], getChildren());
	}

}
