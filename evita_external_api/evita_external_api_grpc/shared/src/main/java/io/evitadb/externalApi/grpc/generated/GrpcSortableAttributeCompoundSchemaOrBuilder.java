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
// source: GrpcEntitySchema.proto

package io.evitadb.externalApi.grpc.generated;

public interface GrpcSortableAttributeCompoundSchemaOrBuilder extends
    // @@protoc_insertion_point(interface_extends:io.evitadb.externalApi.grpc.generated.GrpcSortableAttributeCompoundSchema)
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
   * <pre>
   * Contains description of the model is optional but helps authors of the schema / client API to better
   * explain the original purpose of the model to the consumers.
   * </pre>
   *
   * <code>.google.protobuf.StringValue description = 2;</code>
   * @return Whether the description field is set.
   */
  boolean hasDescription();
  /**
   * <pre>
   * Contains description of the model is optional but helps authors of the schema / client API to better
   * explain the original purpose of the model to the consumers.
   * </pre>
   *
   * <code>.google.protobuf.StringValue description = 2;</code>
   * @return The description.
   */
  com.google.protobuf.StringValue getDescription();
  /**
   * <pre>
   * Contains description of the model is optional but helps authors of the schema / client API to better
   * explain the original purpose of the model to the consumers.
   * </pre>
   *
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
   * Collection of attribute elements that define the sortable compound. The order of the elements
   * is important, as it defines the order of the sorting.
   * </pre>
   *
   * <code>repeated .io.evitadb.externalApi.grpc.generated.GrpcAttributeElement attributeElements = 4;</code>
   */
  java.util.List<io.evitadb.externalApi.grpc.generated.GrpcAttributeElement> 
      getAttributeElementsList();
  /**
   * <pre>
   * Collection of attribute elements that define the sortable compound. The order of the elements
   * is important, as it defines the order of the sorting.
   * </pre>
   *
   * <code>repeated .io.evitadb.externalApi.grpc.generated.GrpcAttributeElement attributeElements = 4;</code>
   */
  io.evitadb.externalApi.grpc.generated.GrpcAttributeElement getAttributeElements(int index);
  /**
   * <pre>
   * Collection of attribute elements that define the sortable compound. The order of the elements
   * is important, as it defines the order of the sorting.
   * </pre>
   *
   * <code>repeated .io.evitadb.externalApi.grpc.generated.GrpcAttributeElement attributeElements = 4;</code>
   */
  int getAttributeElementsCount();
  /**
   * <pre>
   * Collection of attribute elements that define the sortable compound. The order of the elements
   * is important, as it defines the order of the sorting.
   * </pre>
   *
   * <code>repeated .io.evitadb.externalApi.grpc.generated.GrpcAttributeElement attributeElements = 4;</code>
   */
  java.util.List<? extends io.evitadb.externalApi.grpc.generated.GrpcAttributeElementOrBuilder> 
      getAttributeElementsOrBuilderList();
  /**
   * <pre>
   * Collection of attribute elements that define the sortable compound. The order of the elements
   * is important, as it defines the order of the sorting.
   * </pre>
   *
   * <code>repeated .io.evitadb.externalApi.grpc.generated.GrpcAttributeElement attributeElements = 4;</code>
   */
  io.evitadb.externalApi.grpc.generated.GrpcAttributeElementOrBuilder getAttributeElementsOrBuilder(
      int index);
}
