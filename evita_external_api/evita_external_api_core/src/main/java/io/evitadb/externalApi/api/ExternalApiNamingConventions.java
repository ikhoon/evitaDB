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

package io.evitadb.externalApi.api;

import io.evitadb.utils.NamingConvention;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Defines naming convention for specific purposes in external APIs to avoid inconsistencies throughout external APIs.
 *
 * @author Lukáš Hornych, FG Forrest a.s. (c) 2022
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ExternalApiNamingConventions {

	public static final NamingConvention URL_NAME_NAMING_CONVENTION = NamingConvention.KEBAB_CASE;
	public static final NamingConvention TYPE_NAME_NAMING_CONVENTION = NamingConvention.PASCAL_CASE;
	public static final NamingConvention PROPERTY_NAME_NAMING_CONVENTION = NamingConvention.CAMEL_CASE;
	public static final NamingConvention PROPERTY_NAME_PART_NAMING_CONVENTION = NamingConvention.PASCAL_CASE;
	public static final NamingConvention ARGUMENT_NAME_NAMING_CONVENTION = NamingConvention.CAMEL_CASE;
	public static final NamingConvention CLASSIFIER_NAMING_CONVENTION = NamingConvention.CAMEL_CASE;
}
