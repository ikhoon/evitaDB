/*
 *
 *                         _ _        ____  ____
 *               _____   _(_) |_ __ _|  _ \| __ )
 *              / _ \ \ / / | __/ _` | | | |  _ \
 *             |  __/\ V /| | || (_| | |_| | |_) |
 *              \___| \_/ |_|\__\__,_|____/|____/
 *
 *   Copyright (c) 2023-2024
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

// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: GrpcCatalogSchemaMutations.proto

package io.evitadb.externalApi.grpc.generated;

public interface GrpcDisallowEvolutionModeInCatalogSchemaMutationOrBuilder extends
    // @@protoc_insertion_point(interface_extends:io.evitadb.externalApi.grpc.generated.GrpcDisallowEvolutionModeInCatalogSchemaMutation)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * Set of forbidden evolution modes. These allow to specify how strict is evitaDB when unknown information is
   * presented to her for the first time. When no evolution mode is set, each violation of the `CatalogSchema` is
   * reported by an error. This behaviour can be changed by this evolution mode, however.
   * </pre>
   *
   * <code>repeated .io.evitadb.externalApi.grpc.generated.GrpcCatalogEvolutionMode evolutionModes = 1;</code>
   * @return A list containing the evolutionModes.
   */
  java.util.List<io.evitadb.externalApi.grpc.generated.GrpcCatalogEvolutionMode> getEvolutionModesList();
  /**
   * <pre>
   * Set of forbidden evolution modes. These allow to specify how strict is evitaDB when unknown information is
   * presented to her for the first time. When no evolution mode is set, each violation of the `CatalogSchema` is
   * reported by an error. This behaviour can be changed by this evolution mode, however.
   * </pre>
   *
   * <code>repeated .io.evitadb.externalApi.grpc.generated.GrpcCatalogEvolutionMode evolutionModes = 1;</code>
   * @return The count of evolutionModes.
   */
  int getEvolutionModesCount();
  /**
   * <pre>
   * Set of forbidden evolution modes. These allow to specify how strict is evitaDB when unknown information is
   * presented to her for the first time. When no evolution mode is set, each violation of the `CatalogSchema` is
   * reported by an error. This behaviour can be changed by this evolution mode, however.
   * </pre>
   *
   * <code>repeated .io.evitadb.externalApi.grpc.generated.GrpcCatalogEvolutionMode evolutionModes = 1;</code>
   * @param index The index of the element to return.
   * @return The evolutionModes at the given index.
   */
  io.evitadb.externalApi.grpc.generated.GrpcCatalogEvolutionMode getEvolutionModes(int index);
  /**
   * <pre>
   * Set of forbidden evolution modes. These allow to specify how strict is evitaDB when unknown information is
   * presented to her for the first time. When no evolution mode is set, each violation of the `CatalogSchema` is
   * reported by an error. This behaviour can be changed by this evolution mode, however.
   * </pre>
   *
   * <code>repeated .io.evitadb.externalApi.grpc.generated.GrpcCatalogEvolutionMode evolutionModes = 1;</code>
   * @return A list containing the enum numeric values on the wire for evolutionModes.
   */
  java.util.List<java.lang.Integer>
  getEvolutionModesValueList();
  /**
   * <pre>
   * Set of forbidden evolution modes. These allow to specify how strict is evitaDB when unknown information is
   * presented to her for the first time. When no evolution mode is set, each violation of the `CatalogSchema` is
   * reported by an error. This behaviour can be changed by this evolution mode, however.
   * </pre>
   *
   * <code>repeated .io.evitadb.externalApi.grpc.generated.GrpcCatalogEvolutionMode evolutionModes = 1;</code>
   * @param index The index of the value to return.
   * @return The enum numeric value on the wire of evolutionModes at the given index.
   */
  int getEvolutionModesValue(int index);
}
