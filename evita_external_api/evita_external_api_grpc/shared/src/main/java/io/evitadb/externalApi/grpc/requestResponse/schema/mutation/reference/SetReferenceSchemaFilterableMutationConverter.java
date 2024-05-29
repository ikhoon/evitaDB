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

package io.evitadb.externalApi.grpc.requestResponse.schema.mutation.reference;

import io.evitadb.api.requestResponse.schema.mutation.reference.SetReferenceSchemaIndexedMutation;
import io.evitadb.externalApi.grpc.generated.GrpcSetReferenceSchemaFilterableMutation;
import io.evitadb.externalApi.grpc.requestResponse.schema.mutation.SchemaMutationConverter;

import javax.annotation.Nonnull;

/**
 * Converts between {@link SetReferenceSchemaIndexedMutation} and {@link GrpcSetReferenceSchemaFilterableMutation} in both directions.
 *
 * @author Lukáš Hornych, FG Forrest a.s. (c) 2023
 */
public class SetReferenceSchemaFilterableMutationConverter implements SchemaMutationConverter<SetReferenceSchemaIndexedMutation, GrpcSetReferenceSchemaFilterableMutation> {

	@Nonnull
	public SetReferenceSchemaIndexedMutation convert(@Nonnull GrpcSetReferenceSchemaFilterableMutation mutation) {
		return new SetReferenceSchemaIndexedMutation(
			mutation.getName(),
			mutation.getFilterable()
		);
	}

	@Nonnull
	public GrpcSetReferenceSchemaFilterableMutation convert(@Nonnull SetReferenceSchemaIndexedMutation mutation) {
		return GrpcSetReferenceSchemaFilterableMutation.newBuilder()
			.setName(mutation.getName())
			.setFilterable(mutation.isIndexed())
			.build();
	}
}
