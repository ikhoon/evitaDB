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

// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: GrpcEvitaSessionAPI.proto

package io.evitadb.externalApi.grpc.generated;

public interface GrpcDeleteEntityRequestOrBuilder extends
    // @@protoc_insertion_point(interface_extends:io.evitadb.externalApi.grpc.generated.GrpcDeleteEntityRequest)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>string entityType = 1;</code>
   * @return The entityType.
   */
  java.lang.String getEntityType();
  /**
   * <code>string entityType = 1;</code>
   * @return The bytes for entityType.
   */
  com.google.protobuf.ByteString
      getEntityTypeBytes();

  /**
   * <code>.google.protobuf.Int32Value primaryKey = 2;</code>
   * @return Whether the primaryKey field is set.
   */
  boolean hasPrimaryKey();
  /**
   * <code>.google.protobuf.Int32Value primaryKey = 2;</code>
   * @return The primaryKey.
   */
  com.google.protobuf.Int32Value getPrimaryKey();
  /**
   * <code>.google.protobuf.Int32Value primaryKey = 2;</code>
   */
  com.google.protobuf.Int32ValueOrBuilder getPrimaryKeyOrBuilder();

  /**
   * <code>.io.evitadb.externalApi.grpc.generated.GrpcEntityExistence entityExistence = 3;</code>
   * @return The enum numeric value on the wire for entityExistence.
   */
  int getEntityExistenceValue();
  /**
   * <code>.io.evitadb.externalApi.grpc.generated.GrpcEntityExistence entityExistence = 3;</code>
   * @return The entityExistence.
   */
  io.evitadb.externalApi.grpc.generated.GrpcEntityExistence getEntityExistence();

  /**
   * <code>string require = 4;</code>
   * @return The require.
   */
  java.lang.String getRequire();
  /**
   * <code>string require = 4;</code>
   * @return The bytes for require.
   */
  com.google.protobuf.ByteString
      getRequireBytes();

  /**
   * <code>repeated .io.evitadb.externalApi.grpc.generated.QueryParam positionalQueryParams = 5;</code>
   */
  java.util.List<io.evitadb.externalApi.grpc.generated.QueryParam> 
      getPositionalQueryParamsList();
  /**
   * <code>repeated .io.evitadb.externalApi.grpc.generated.QueryParam positionalQueryParams = 5;</code>
   */
  io.evitadb.externalApi.grpc.generated.QueryParam getPositionalQueryParams(int index);
  /**
   * <code>repeated .io.evitadb.externalApi.grpc.generated.QueryParam positionalQueryParams = 5;</code>
   */
  int getPositionalQueryParamsCount();
  /**
   * <code>repeated .io.evitadb.externalApi.grpc.generated.QueryParam positionalQueryParams = 5;</code>
   */
  java.util.List<? extends io.evitadb.externalApi.grpc.generated.QueryParamOrBuilder> 
      getPositionalQueryParamsOrBuilderList();
  /**
   * <code>repeated .io.evitadb.externalApi.grpc.generated.QueryParam positionalQueryParams = 5;</code>
   */
  io.evitadb.externalApi.grpc.generated.QueryParamOrBuilder getPositionalQueryParamsOrBuilder(
      int index);

  /**
   * <code>map&lt;string, .io.evitadb.externalApi.grpc.generated.QueryParam&gt; namedQueryParams = 6;</code>
   */
  int getNamedQueryParamsCount();
  /**
   * <code>map&lt;string, .io.evitadb.externalApi.grpc.generated.QueryParam&gt; namedQueryParams = 6;</code>
   */
  boolean containsNamedQueryParams(
      java.lang.String key);
  /**
   * Use {@link #getNamedQueryParamsMap()} instead.
   */
  @java.lang.Deprecated
  java.util.Map<java.lang.String, io.evitadb.externalApi.grpc.generated.QueryParam>
  getNamedQueryParams();
  /**
   * <code>map&lt;string, .io.evitadb.externalApi.grpc.generated.QueryParam&gt; namedQueryParams = 6;</code>
   */
  java.util.Map<java.lang.String, io.evitadb.externalApi.grpc.generated.QueryParam>
  getNamedQueryParamsMap();
  /**
   * <code>map&lt;string, .io.evitadb.externalApi.grpc.generated.QueryParam&gt; namedQueryParams = 6;</code>
   */

  io.evitadb.externalApi.grpc.generated.QueryParam getNamedQueryParamsOrDefault(
      java.lang.String key,
      io.evitadb.externalApi.grpc.generated.QueryParam defaultValue);
  /**
   * <code>map&lt;string, .io.evitadb.externalApi.grpc.generated.QueryParam&gt; namedQueryParams = 6;</code>
   */

  io.evitadb.externalApi.grpc.generated.QueryParam getNamedQueryParamsOrThrow(
      java.lang.String key);
}
