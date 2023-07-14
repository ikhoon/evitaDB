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

package io.evitadb.api.proxy.impl.reference;

import io.evitadb.api.proxy.impl.ProxyUtils;
import io.evitadb.api.proxy.impl.SealedEntityReferenceProxyState;
import io.evitadb.api.requestResponse.data.annotation.PrimaryKeyRef;
import io.evitadb.api.requestResponse.data.annotation.ReferencedEntity;
import io.evitadb.utils.ClassUtils;
import io.evitadb.utils.ReflectionLookup;
import one.edee.oss.proxycian.DirectMethodClassification;

import java.util.Optional;
import java.util.function.UnaryOperator;

import static io.evitadb.api.proxy.impl.ProxyUtils.getWrappedGenericType;
import static io.evitadb.dataType.EvitaDataTypes.toTargetType;
import static io.evitadb.dataType.EvitaDataTypes.toWrappedForm;

/**
 * Identifies methods that are used to get referenced entity primary key from an entity and provides their implementation.
 *
 * @author Jan Novotný (novotny@fg.cz), FG Forrest a.s. (c) 2023
 */
public class GetReferencedEntityPrimaryKeyMethodClassifier extends DirectMethodClassification<Object, SealedEntityReferenceProxyState> {
	/**
	 * We may reuse singleton instance since advice is stateless.
	 */
	public static final GetReferencedEntityPrimaryKeyMethodClassifier INSTANCE = new GetReferencedEntityPrimaryKeyMethodClassifier();

	public GetReferencedEntityPrimaryKeyMethodClassifier() {
		super(
			"getReferencedEntityPrimaryKey",
			(method, proxyState) -> {
				// we are interested only in abstract methods with no arguments.
				if (!ClassUtils.isAbstractOrDefault(method) || method.getParameterCount() > 0) {
					return null;
				}

				// we try to find appropriate annotations on the method, if no Evita annotation is found it tries
				// to match the method by its name
				final ReflectionLookup reflectionLookup = proxyState.getReflectionLookup();
				final ReferencedEntity referencedEntity = reflectionLookup.getAnnotationInstance(method, ReferencedEntity.class);
				@SuppressWarnings("rawtypes") final Class returnType = method.getReturnType();
				@SuppressWarnings("rawtypes") final Class wrappedGenericType = getWrappedGenericType(method, proxyState.getProxyClass());
				final UnaryOperator<Object> resultWrapper = ProxyUtils.createOptionalWrapper(wrappedGenericType);
				@SuppressWarnings("rawtypes") final Class valueType = wrappedGenericType == null ? returnType : wrappedGenericType;

				final Optional<String> propertyName = ReflectionLookup.getPropertyNameFromMethodNameIfPossible(method.getName());
				if (Number.class.isAssignableFrom(toWrappedForm(valueType)) && referencedEntity != null || (
					!reflectionLookup.hasAnnotationInSamePackage(method, ReferencedEntity.class) &&
						propertyName
							.map(PrimaryKeyRef.POSSIBLE_ARGUMENT_NAMES::contains)
							.orElse(false)
				)
				) {
					// method matches - provide implementation
					//noinspection unchecked
					return (entityClassifier, theMethod, args, theState, invokeSuper) -> resultWrapper.apply(
						toTargetType(
							theState.getReference().getReferencedPrimaryKey(), valueType
						)
					);
				} else {
					// this method is not classified by this implementation
					return null;
				}
			}
		);
	}

}
