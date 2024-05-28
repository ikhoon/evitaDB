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

package io.evitadb.store.entity.model.entity.price;

import io.evitadb.api.requestResponse.data.PriceContract;
import io.evitadb.api.requestResponse.data.structure.Price.PriceKey;
import io.evitadb.utils.NumberUtils;

import javax.annotation.Nullable;

/**
 * Interface allow accessing internally assigned price identifiers. We use our own identifiers because we need
 * to stick to Java int types in indexes and searching. The priceId and innerRecordId are related to its entity. And
 * prices with same priceId and innerRecordId used in different entity may hold entirely different data -
 * see {@link PriceContract#priceId()}.
 *
 * So to uniquely address the price we need the combination of entity primary key and the price id, or the entity
 * primary key and inner record id. This would require two ints, or single long to represent. This is quite large data
 * to keep in memory, and we would be forced to use `Roaring64Bitmap` which are considerably slower and even slower
 * for data generated by {@link NumberUtils#join(int, int)}, because there are big gaps between two adjoining entity
 * identifiers. By mapping external ids to internally assigned int ids we could stick to the int type and allow
 * the required relation between identifiers and entity.
 *
 * @author Jan Novotný (novotny@fg.cz), FG Forrest a.s. (c) 2022
 */
public interface PriceInternalIdContainer {

	/**
	 * Returns internal id for {@link PriceContract#priceId()}. The is unique for the price identified
	 * by {@link PriceKey} inside single entity. The id is different for two prices sharing same {@link PriceKey}
	 * but are present in different entities.
	 */
	@Nullable
	Integer getInternalPriceId();

}
