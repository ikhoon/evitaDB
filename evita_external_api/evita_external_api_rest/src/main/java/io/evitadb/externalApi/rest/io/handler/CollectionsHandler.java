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

package io.evitadb.externalApi.rest.io.handler;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.evitadb.externalApi.rest.api.catalog.ParamDescriptor;
import io.evitadb.externalApi.rest.api.catalog.model.CollectionDescriptor;
import io.evitadb.externalApi.rest.io.serializer.ObjectJsonSerializer;
import io.undertow.server.HttpServerExchange;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * This handler is used to get list of names (and counts) of existing collections withing one catalog.
 *
 * @author Martin Veska (veska@fg.cz), FG Forrest a.s. (c) 2022
 */
public class CollectionsHandler extends RestHandler<RestHandlingContext> {

	public CollectionsHandler(@Nonnull RestHandlingContext restHandlingContext) {
		super(restHandlingContext);
	}

	@Override
	public void handleRequest(@Nonnull HttpServerExchange exchange) throws Exception {
		final Map<String, Object> parametersFromRequest = getParametersFromRequest(exchange, restApiHandlingContext.getEndpointOperation());
		final Boolean withCounts = (Boolean) parametersFromRequest.get(ParamDescriptor.ENTITY_COUNT.name());

		restApiHandlingContext.queryCatalog(session -> {
			final ObjectJsonSerializer objectJsonSerializer = new ObjectJsonSerializer(restApiHandlingContext.getObjectMapper());
			final ArrayNode collections = objectJsonSerializer.arrayNode();
			for (String entityType : session.getAllEntityTypes()) {
				final ObjectNode collectionNode = objectJsonSerializer.objectNode();
				collectionNode.putIfAbsent(CollectionDescriptor.ENTITY_TYPE.name(), objectJsonSerializer.serializeObject(entityType));
				collections.add(collectionNode);
				if(withCounts != null && withCounts) {
					collectionNode.putIfAbsent(CollectionDescriptor.COUNT.name(), objectJsonSerializer.serializeObject(session.getEntityCollectionSize(entityType)));
				}
			}

			setSuccessResponse(exchange, serializeResult(collections));
		});
	}
}
