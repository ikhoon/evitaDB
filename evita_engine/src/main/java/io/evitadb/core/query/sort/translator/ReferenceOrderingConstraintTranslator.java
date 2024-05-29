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
 *   https://github.com/FgForrest/evitaDB/blob/master/LICENSE
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package io.evitadb.core.query.sort.translator;

import io.evitadb.api.query.OrderConstraint;
import io.evitadb.api.requestResponse.data.structure.ReferenceComparator;
import io.evitadb.api.requestResponse.data.structure.ReferenceDecorator;
import io.evitadb.core.query.sort.ReferenceOrderByVisitor;

import javax.annotation.Nonnull;

/**
 * Implementations of this interface translate specific {@link OrderConstraint}s to
 * a {@link ReferenceComparator} that can sort fetched {@link ReferenceDecorator} so that they fulfill the requested
 * ordering.
 *
 * @author Jan Novotný (novotny@fg.cz), FG Forrest a.s. (c) 2021
 */
@FunctionalInterface
public interface ReferenceOrderingConstraintTranslator<T extends OrderConstraint> {

	/**
	 * Method creates the appropriate {@link ReferenceComparator} implementation for passed constraint.
	 */
	void createComparator(@Nonnull T t, @Nonnull ReferenceOrderByVisitor orderByVisitor);

}
