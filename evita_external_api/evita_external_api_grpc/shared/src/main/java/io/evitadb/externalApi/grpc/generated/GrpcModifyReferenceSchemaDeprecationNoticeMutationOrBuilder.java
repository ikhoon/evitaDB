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
// source: GrpcReferenceSchemaMutations.proto

package io.evitadb.externalApi.grpc.generated;

public interface GrpcModifyReferenceSchemaDeprecationNoticeMutationOrBuilder extends
    // @@protoc_insertion_point(interface_extends:io.evitadb.externalApi.grpc.generated.GrpcModifyReferenceSchemaDeprecationNoticeMutation)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * Name of the reference the mutation is targeting.
   * </pre>
   *
   * <code>string name = 1;</code>
   * @return The name.
   */
  java.lang.String getName();
  /**
   * <pre>
   * Name of the reference the mutation is targeting.
   * </pre>
   *
   * <code>string name = 1;</code>
   * @return The bytes for name.
   */
  com.google.protobuf.ByteString
      getNameBytes();

  /**
   * <pre>
   * Deprecation notice contains information about planned removal of this schema from the model / client API.
   * This allows to plan and evolve the schema allowing clients to adapt early to planned breaking changes.
   * </pre>
   *
   * <code>.google.protobuf.StringValue deprecationNotice = 2;</code>
   * @return Whether the deprecationNotice field is set.
   */
  boolean hasDeprecationNotice();
  /**
   * <pre>
   * Deprecation notice contains information about planned removal of this schema from the model / client API.
   * This allows to plan and evolve the schema allowing clients to adapt early to planned breaking changes.
   * </pre>
   *
   * <code>.google.protobuf.StringValue deprecationNotice = 2;</code>
   * @return The deprecationNotice.
   */
  com.google.protobuf.StringValue getDeprecationNotice();
  /**
   * <pre>
   * Deprecation notice contains information about planned removal of this schema from the model / client API.
   * This allows to plan and evolve the schema allowing clients to adapt early to planned breaking changes.
   * </pre>
   *
   * <code>.google.protobuf.StringValue deprecationNotice = 2;</code>
   */
  com.google.protobuf.StringValueOrBuilder getDeprecationNoticeOrBuilder();
}
