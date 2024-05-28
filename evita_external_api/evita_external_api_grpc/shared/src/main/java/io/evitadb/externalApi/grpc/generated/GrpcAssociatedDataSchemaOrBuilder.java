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
// source: GrpcEntitySchema.proto

package io.evitadb.externalApi.grpc.generated;

public interface GrpcAssociatedDataSchemaOrBuilder extends
    // @@protoc_insertion_point(interface_extends:io.evitadb.externalApi.grpc.generated.GrpcAssociatedDataSchema)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * Contains unique name of the model. Case-sensitive. Distinguishes one model item from another
   * within single entity instance.
   * </pre>
   *
   * <code>string name = 1;</code>
   * @return The name.
   */
  java.lang.String getName();
  /**
   * <pre>
   * Contains unique name of the model. Case-sensitive. Distinguishes one model item from another
   * within single entity instance.
   * </pre>
   *
   * <code>string name = 1;</code>
   * @return The bytes for name.
   */
  com.google.protobuf.ByteString
      getNameBytes();

  /**
   * <code>.google.protobuf.StringValue description = 2;</code>
   * @return Whether the description field is set.
   */
  boolean hasDescription();
  /**
   * <code>.google.protobuf.StringValue description = 2;</code>
   * @return The description.
   */
  com.google.protobuf.StringValue getDescription();
  /**
   * <code>.google.protobuf.StringValue description = 2;</code>
   */
  com.google.protobuf.StringValueOrBuilder getDescriptionOrBuilder();

  /**
   * <pre>
   * Deprecation notice contains information about planned removal of this entity from the model / client API.
   * This allows to plan and evolve the schema allowing clients to adapt early to planned breaking changes.
   * If notice is `null`, this schema is considered not deprecated.
   * </pre>
   *
   * <code>.google.protobuf.StringValue deprecationNotice = 3;</code>
   * @return Whether the deprecationNotice field is set.
   */
  boolean hasDeprecationNotice();
  /**
   * <pre>
   * Deprecation notice contains information about planned removal of this entity from the model / client API.
   * This allows to plan and evolve the schema allowing clients to adapt early to planned breaking changes.
   * If notice is `null`, this schema is considered not deprecated.
   * </pre>
   *
   * <code>.google.protobuf.StringValue deprecationNotice = 3;</code>
   * @return The deprecationNotice.
   */
  com.google.protobuf.StringValue getDeprecationNotice();
  /**
   * <pre>
   * Deprecation notice contains information about planned removal of this entity from the model / client API.
   * This allows to plan and evolve the schema allowing clients to adapt early to planned breaking changes.
   * If notice is `null`, this schema is considered not deprecated.
   * </pre>
   *
   * <code>.google.protobuf.StringValue deprecationNotice = 3;</code>
   */
  com.google.protobuf.StringValueOrBuilder getDeprecationNoticeOrBuilder();

  /**
   * <pre>
   * Data type of the associated data. Must be one of Evita-supported values.
   * Internally the type is converted into Java-corresponding data type.
   * The type may be scalar type or may represent complex object type (JSON).
   * </pre>
   *
   * <code>.io.evitadb.externalApi.grpc.generated.GrpcEvitaAssociatedDataDataType.GrpcEvitaDataType type = 4;</code>
   * @return The enum numeric value on the wire for type.
   */
  int getTypeValue();
  /**
   * <pre>
   * Data type of the associated data. Must be one of Evita-supported values.
   * Internally the type is converted into Java-corresponding data type.
   * The type may be scalar type or may represent complex object type (JSON).
   * </pre>
   *
   * <code>.io.evitadb.externalApi.grpc.generated.GrpcEvitaAssociatedDataDataType.GrpcEvitaDataType type = 4;</code>
   * @return The type.
   */
  io.evitadb.externalApi.grpc.generated.GrpcEvitaAssociatedDataDataType.GrpcEvitaDataType getType();

  /**
   * <pre>
   * Localized associated data has to be ALWAYS used in connection with specific `Locale`. In other
   * words - it cannot be stored unless associated locale is also provided.
   * </pre>
   *
   * <code>bool localized = 5;</code>
   * @return The localized.
   */
  boolean getLocalized();

  /**
   * <pre>
   * When associated data is nullable, its values may be missing in the entities. Otherwise, the system will enforce
   * non-null checks upon upserting of the entity.
   * </pre>
   *
   * <code>bool nullable = 6;</code>
   * @return The nullable.
   */
  boolean getNullable();
}
