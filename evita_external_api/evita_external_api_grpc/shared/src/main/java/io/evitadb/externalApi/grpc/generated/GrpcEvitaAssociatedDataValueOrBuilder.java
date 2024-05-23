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
// source: GrpcEvitaDataTypes.proto

package io.evitadb.externalApi.grpc.generated;

public interface GrpcEvitaAssociatedDataValueOrBuilder extends
    // @@protoc_insertion_point(interface_extends:io.evitadb.externalApi.grpc.generated.GrpcEvitaAssociatedDataValue)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * Primitive value.
   * </pre>
   *
   * <code>.io.evitadb.externalApi.grpc.generated.GrpcEvitaValue primitiveValue = 1;</code>
   * @return Whether the primitiveValue field is set.
   */
  boolean hasPrimitiveValue();
  /**
   * <pre>
   * Primitive value.
   * </pre>
   *
   * <code>.io.evitadb.externalApi.grpc.generated.GrpcEvitaValue primitiveValue = 1;</code>
   * @return The primitiveValue.
   */
  io.evitadb.externalApi.grpc.generated.GrpcEvitaValue getPrimitiveValue();
  /**
   * <pre>
   * Primitive value.
   * </pre>
   *
   * <code>.io.evitadb.externalApi.grpc.generated.GrpcEvitaValue primitiveValue = 1;</code>
   */
  io.evitadb.externalApi.grpc.generated.GrpcEvitaValueOrBuilder getPrimitiveValueOrBuilder();

  /**
   * <pre>
   * JSON string value.
   * </pre>
   *
   * <code>string jsonValue = 2;</code>
   * @return Whether the jsonValue field is set.
   */
  boolean hasJsonValue();
  /**
   * <pre>
   * JSON string value.
   * </pre>
   *
   * <code>string jsonValue = 2;</code>
   * @return The jsonValue.
   */
  java.lang.String getJsonValue();
  /**
   * <pre>
   * JSON string value.
   * </pre>
   *
   * <code>string jsonValue = 2;</code>
   * @return The bytes for jsonValue.
   */
  com.google.protobuf.ByteString
      getJsonValueBytes();

  /**
   * <pre>
   * Contains version of this value and gets increased with any entity type update. Allows to execute
   *			optimistic locking i.e. avoiding parallel modifications.
   * </pre>
   *
   * <code>.google.protobuf.Int32Value version = 3;</code>
   * @return Whether the version field is set.
   */
  boolean hasVersion();
  /**
   * <pre>
   * Contains version of this value and gets increased with any entity type update. Allows to execute
   *			optimistic locking i.e. avoiding parallel modifications.
   * </pre>
   *
   * <code>.google.protobuf.Int32Value version = 3;</code>
   * @return The version.
   */
  com.google.protobuf.Int32Value getVersion();
  /**
   * <pre>
   * Contains version of this value and gets increased with any entity type update. Allows to execute
   *			optimistic locking i.e. avoiding parallel modifications.
   * </pre>
   *
   * <code>.google.protobuf.Int32Value version = 3;</code>
   */
  com.google.protobuf.Int32ValueOrBuilder getVersionOrBuilder();

  public io.evitadb.externalApi.grpc.generated.GrpcEvitaAssociatedDataValue.ValueCase getValueCase();
}
