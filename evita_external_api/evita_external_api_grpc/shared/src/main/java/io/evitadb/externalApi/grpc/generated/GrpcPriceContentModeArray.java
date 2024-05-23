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

/**
 * <pre>
 * Wrapper for representing an array of PriceContentMode enums.
 * </pre>
 *
 * Protobuf type {@code io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray}
 */
public final class GrpcPriceContentModeArray extends
    com.google.protobuf.GeneratedMessageV3 implements
    // @@protoc_insertion_point(message_implements:io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray)
    GrpcPriceContentModeArrayOrBuilder {
private static final long serialVersionUID = 0L;
  // Use GrpcPriceContentModeArray.newBuilder() to construct.
  private GrpcPriceContentModeArray(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
    super(builder);
  }
  private GrpcPriceContentModeArray() {
    value_ = java.util.Collections.emptyList();
  }

  @java.lang.Override
  @SuppressWarnings({"unused"})
  protected java.lang.Object newInstance(
      UnusedPrivateParameter unused) {
    return new GrpcPriceContentModeArray();
  }

  @java.lang.Override
  public final com.google.protobuf.UnknownFieldSet
  getUnknownFields() {
    return this.unknownFields;
  }
  private GrpcPriceContentModeArray(
      com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    this();
    if (extensionRegistry == null) {
      throw new java.lang.NullPointerException();
    }
    int mutable_bitField0_ = 0;
    com.google.protobuf.UnknownFieldSet.Builder unknownFields =
        com.google.protobuf.UnknownFieldSet.newBuilder();
    try {
      boolean done = false;
      while (!done) {
        int tag = input.readTag();
        switch (tag) {
          case 0:
            done = true;
            break;
          case 8: {
            int rawValue = input.readEnum();
            if (!((mutable_bitField0_ & 0x00000001) != 0)) {
              value_ = new java.util.ArrayList<java.lang.Integer>();
              mutable_bitField0_ |= 0x00000001;
            }
            value_.add(rawValue);
            break;
          }
          case 10: {
            int length = input.readRawVarint32();
            int oldLimit = input.pushLimit(length);
            while(input.getBytesUntilLimit() > 0) {
              int rawValue = input.readEnum();
              if (!((mutable_bitField0_ & 0x00000001) != 0)) {
                value_ = new java.util.ArrayList<java.lang.Integer>();
                mutable_bitField0_ |= 0x00000001;
              }
              value_.add(rawValue);
            }
            input.popLimit(oldLimit);
            break;
          }
          default: {
            if (!parseUnknownField(
                input, unknownFields, extensionRegistry, tag)) {
              done = true;
            }
            break;
          }
        }
      }
    } catch (com.google.protobuf.InvalidProtocolBufferException e) {
      throw e.setUnfinishedMessage(this);
    } catch (java.io.IOException e) {
      throw new com.google.protobuf.InvalidProtocolBufferException(
          e).setUnfinishedMessage(this);
    } finally {
      if (((mutable_bitField0_ & 0x00000001) != 0)) {
        value_ = java.util.Collections.unmodifiableList(value_);
      }
      this.unknownFields = unknownFields.build();
      makeExtensionsImmutable();
    }
  }
  public static final com.google.protobuf.Descriptors.Descriptor
      getDescriptor() {
    return io.evitadb.externalApi.grpc.generated.GrpcEvitaDataTypes.internal_static_io_evitadb_externalApi_grpc_generated_GrpcPriceContentModeArray_descriptor;
  }

  @java.lang.Override
  protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internalGetFieldAccessorTable() {
    return io.evitadb.externalApi.grpc.generated.GrpcEvitaDataTypes.internal_static_io_evitadb_externalApi_grpc_generated_GrpcPriceContentModeArray_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray.class, io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray.Builder.class);
  }

  public static final int VALUE_FIELD_NUMBER = 1;
  private java.util.List<java.lang.Integer> value_;
  private static final com.google.protobuf.Internal.ListAdapter.Converter<
      java.lang.Integer, io.evitadb.externalApi.grpc.generated.GrpcPriceContentMode> value_converter_ =
          new com.google.protobuf.Internal.ListAdapter.Converter<
              java.lang.Integer, io.evitadb.externalApi.grpc.generated.GrpcPriceContentMode>() {
            public io.evitadb.externalApi.grpc.generated.GrpcPriceContentMode convert(java.lang.Integer from) {
              @SuppressWarnings("deprecation")
              io.evitadb.externalApi.grpc.generated.GrpcPriceContentMode result = io.evitadb.externalApi.grpc.generated.GrpcPriceContentMode.valueOf(from);
              return result == null ? io.evitadb.externalApi.grpc.generated.GrpcPriceContentMode.UNRECOGNIZED : result;
            }
          };
  /**
   * <pre>
   * Value that supports storing a PriceContentMode array.
   * </pre>
   *
   * <code>repeated .io.evitadb.externalApi.grpc.generated.GrpcPriceContentMode value = 1;</code>
   * @return A list containing the value.
   */
  @java.lang.Override
  public java.util.List<io.evitadb.externalApi.grpc.generated.GrpcPriceContentMode> getValueList() {
    return new com.google.protobuf.Internal.ListAdapter<
        java.lang.Integer, io.evitadb.externalApi.grpc.generated.GrpcPriceContentMode>(value_, value_converter_);
  }
  /**
   * <pre>
   * Value that supports storing a PriceContentMode array.
   * </pre>
   *
   * <code>repeated .io.evitadb.externalApi.grpc.generated.GrpcPriceContentMode value = 1;</code>
   * @return The count of value.
   */
  @java.lang.Override
  public int getValueCount() {
    return value_.size();
  }
  /**
   * <pre>
   * Value that supports storing a PriceContentMode array.
   * </pre>
   *
   * <code>repeated .io.evitadb.externalApi.grpc.generated.GrpcPriceContentMode value = 1;</code>
   * @param index The index of the element to return.
   * @return The value at the given index.
   */
  @java.lang.Override
  public io.evitadb.externalApi.grpc.generated.GrpcPriceContentMode getValue(int index) {
    return value_converter_.convert(value_.get(index));
  }
  /**
   * <pre>
   * Value that supports storing a PriceContentMode array.
   * </pre>
   *
   * <code>repeated .io.evitadb.externalApi.grpc.generated.GrpcPriceContentMode value = 1;</code>
   * @return A list containing the enum numeric values on the wire for value.
   */
  @java.lang.Override
  public java.util.List<java.lang.Integer>
  getValueValueList() {
    return value_;
  }
  /**
   * <pre>
   * Value that supports storing a PriceContentMode array.
   * </pre>
   *
   * <code>repeated .io.evitadb.externalApi.grpc.generated.GrpcPriceContentMode value = 1;</code>
   * @param index The index of the value to return.
   * @return The enum numeric value on the wire of value at the given index.
   */
  @java.lang.Override
  public int getValueValue(int index) {
    return value_.get(index);
  }
  private int valueMemoizedSerializedSize;

  private byte memoizedIsInitialized = -1;
  @java.lang.Override
  public final boolean isInitialized() {
    byte isInitialized = memoizedIsInitialized;
    if (isInitialized == 1) return true;
    if (isInitialized == 0) return false;

    memoizedIsInitialized = 1;
    return true;
  }

  @java.lang.Override
  public void writeTo(com.google.protobuf.CodedOutputStream output)
                      throws java.io.IOException {
    getSerializedSize();
    if (getValueList().size() > 0) {
      output.writeUInt32NoTag(10);
      output.writeUInt32NoTag(valueMemoizedSerializedSize);
    }
    for (int i = 0; i < value_.size(); i++) {
      output.writeEnumNoTag(value_.get(i));
    }
    unknownFields.writeTo(output);
  }

  @java.lang.Override
  public int getSerializedSize() {
    int size = memoizedSize;
    if (size != -1) return size;

    size = 0;
    {
      int dataSize = 0;
      for (int i = 0; i < value_.size(); i++) {
        dataSize += com.google.protobuf.CodedOutputStream
          .computeEnumSizeNoTag(value_.get(i));
      }
      size += dataSize;
      if (!getValueList().isEmpty()) {  size += 1;
        size += com.google.protobuf.CodedOutputStream
          .computeUInt32SizeNoTag(dataSize);
      }valueMemoizedSerializedSize = dataSize;
    }
    size += unknownFields.getSerializedSize();
    memoizedSize = size;
    return size;
  }

  @java.lang.Override
  public boolean equals(final java.lang.Object obj) {
    if (obj == this) {
     return true;
    }
    if (!(obj instanceof io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray)) {
      return super.equals(obj);
    }
    io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray other = (io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray) obj;

    if (!value_.equals(other.value_)) return false;
    if (!unknownFields.equals(other.unknownFields)) return false;
    return true;
  }

  @java.lang.Override
  public int hashCode() {
    if (memoizedHashCode != 0) {
      return memoizedHashCode;
    }
    int hash = 41;
    hash = (19 * hash) + getDescriptor().hashCode();
    if (getValueCount() > 0) {
      hash = (37 * hash) + VALUE_FIELD_NUMBER;
      hash = (53 * hash) + value_.hashCode();
    }
    hash = (29 * hash) + unknownFields.hashCode();
    memoizedHashCode = hash;
    return hash;
  }

  public static io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray parseFrom(
      java.nio.ByteBuffer data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray parseFrom(
      java.nio.ByteBuffer data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }
  public static io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray parseFrom(
      byte[] data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }
  public static io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray parseFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input, extensionRegistry);
  }
  public static io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray parseDelimitedFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input);
  }
  public static io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray parseDelimitedFrom(
      java.io.InputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
  }
  public static io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray parseFrom(
      com.google.protobuf.CodedInputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input);
  }
  public static io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray parseFrom(
      com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3
        .parseWithIOException(PARSER, input, extensionRegistry);
  }

  @java.lang.Override
  public Builder newBuilderForType() { return newBuilder(); }
  public static Builder newBuilder() {
    return DEFAULT_INSTANCE.toBuilder();
  }
  public static Builder newBuilder(io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray prototype) {
    return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
  }
  @java.lang.Override
  public Builder toBuilder() {
    return this == DEFAULT_INSTANCE
        ? new Builder() : new Builder().mergeFrom(this);
  }

  @java.lang.Override
  protected Builder newBuilderForType(
      com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
    Builder builder = new Builder(parent);
    return builder;
  }
  /**
   * <pre>
   * Wrapper for representing an array of PriceContentMode enums.
   * </pre>
   *
   * Protobuf type {@code io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray}
   */
  public static final class Builder extends
      com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
      // @@protoc_insertion_point(builder_implements:io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray)
      io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArrayOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return io.evitadb.externalApi.grpc.generated.GrpcEvitaDataTypes.internal_static_io_evitadb_externalApi_grpc_generated_GrpcPriceContentModeArray_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return io.evitadb.externalApi.grpc.generated.GrpcEvitaDataTypes.internal_static_io_evitadb_externalApi_grpc_generated_GrpcPriceContentModeArray_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray.class, io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray.Builder.class);
    }

    // Construct using io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray.newBuilder()
    private Builder() {
      maybeForceBuilderInitialization();
    }

    private Builder(
        com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
      super(parent);
      maybeForceBuilderInitialization();
    }
    private void maybeForceBuilderInitialization() {
      if (com.google.protobuf.GeneratedMessageV3
              .alwaysUseFieldBuilders) {
      }
    }
    @java.lang.Override
    public Builder clear() {
      super.clear();
      value_ = java.util.Collections.emptyList();
      bitField0_ = (bitField0_ & ~0x00000001);
      return this;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.Descriptor
        getDescriptorForType() {
      return io.evitadb.externalApi.grpc.generated.GrpcEvitaDataTypes.internal_static_io_evitadb_externalApi_grpc_generated_GrpcPriceContentModeArray_descriptor;
    }

    @java.lang.Override
    public io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray getDefaultInstanceForType() {
      return io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray.getDefaultInstance();
    }

    @java.lang.Override
    public io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray build() {
      io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray result = buildPartial();
      if (!result.isInitialized()) {
        throw newUninitializedMessageException(result);
      }
      return result;
    }

    @java.lang.Override
    public io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray buildPartial() {
      io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray result = new io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray(this);
      int from_bitField0_ = bitField0_;
      if (((bitField0_ & 0x00000001) != 0)) {
        value_ = java.util.Collections.unmodifiableList(value_);
        bitField0_ = (bitField0_ & ~0x00000001);
      }
      result.value_ = value_;
      onBuilt();
      return result;
    }

    @java.lang.Override
    public Builder clone() {
      return super.clone();
    }
    @java.lang.Override
    public Builder setField(
        com.google.protobuf.Descriptors.FieldDescriptor field,
        java.lang.Object value) {
      return super.setField(field, value);
    }
    @java.lang.Override
    public Builder clearField(
        com.google.protobuf.Descriptors.FieldDescriptor field) {
      return super.clearField(field);
    }
    @java.lang.Override
    public Builder clearOneof(
        com.google.protobuf.Descriptors.OneofDescriptor oneof) {
      return super.clearOneof(oneof);
    }
    @java.lang.Override
    public Builder setRepeatedField(
        com.google.protobuf.Descriptors.FieldDescriptor field,
        int index, java.lang.Object value) {
      return super.setRepeatedField(field, index, value);
    }
    @java.lang.Override
    public Builder addRepeatedField(
        com.google.protobuf.Descriptors.FieldDescriptor field,
        java.lang.Object value) {
      return super.addRepeatedField(field, value);
    }
    @java.lang.Override
    public Builder mergeFrom(com.google.protobuf.Message other) {
      if (other instanceof io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray) {
        return mergeFrom((io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray)other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray other) {
      if (other == io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray.getDefaultInstance()) return this;
      if (!other.value_.isEmpty()) {
        if (value_.isEmpty()) {
          value_ = other.value_;
          bitField0_ = (bitField0_ & ~0x00000001);
        } else {
          ensureValueIsMutable();
          value_.addAll(other.value_);
        }
        onChanged();
      }
      this.mergeUnknownFields(other.unknownFields);
      onChanged();
      return this;
    }

    @java.lang.Override
    public final boolean isInitialized() {
      return true;
    }

    @java.lang.Override
    public Builder mergeFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray parsedMessage = null;
      try {
        parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        parsedMessage = (io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray) e.getUnfinishedMessage();
        throw e.unwrapIOException();
      } finally {
        if (parsedMessage != null) {
          mergeFrom(parsedMessage);
        }
      }
      return this;
    }
    private int bitField0_;

    private java.util.List<java.lang.Integer> value_ =
      java.util.Collections.emptyList();
    private void ensureValueIsMutable() {
      if (!((bitField0_ & 0x00000001) != 0)) {
        value_ = new java.util.ArrayList<java.lang.Integer>(value_);
        bitField0_ |= 0x00000001;
      }
    }
    /**
     * <pre>
     * Value that supports storing a PriceContentMode array.
     * </pre>
     *
     * <code>repeated .io.evitadb.externalApi.grpc.generated.GrpcPriceContentMode value = 1;</code>
     * @return A list containing the value.
     */
    public java.util.List<io.evitadb.externalApi.grpc.generated.GrpcPriceContentMode> getValueList() {
      return new com.google.protobuf.Internal.ListAdapter<
          java.lang.Integer, io.evitadb.externalApi.grpc.generated.GrpcPriceContentMode>(value_, value_converter_);
    }
    /**
     * <pre>
     * Value that supports storing a PriceContentMode array.
     * </pre>
     *
     * <code>repeated .io.evitadb.externalApi.grpc.generated.GrpcPriceContentMode value = 1;</code>
     * @return The count of value.
     */
    public int getValueCount() {
      return value_.size();
    }
    /**
     * <pre>
     * Value that supports storing a PriceContentMode array.
     * </pre>
     *
     * <code>repeated .io.evitadb.externalApi.grpc.generated.GrpcPriceContentMode value = 1;</code>
     * @param index The index of the element to return.
     * @return The value at the given index.
     */
    public io.evitadb.externalApi.grpc.generated.GrpcPriceContentMode getValue(int index) {
      return value_converter_.convert(value_.get(index));
    }
    /**
     * <pre>
     * Value that supports storing a PriceContentMode array.
     * </pre>
     *
     * <code>repeated .io.evitadb.externalApi.grpc.generated.GrpcPriceContentMode value = 1;</code>
     * @param index The index to set the value at.
     * @param value The value to set.
     * @return This builder for chaining.
     */
    public Builder setValue(
        int index, io.evitadb.externalApi.grpc.generated.GrpcPriceContentMode value) {
      if (value == null) {
        throw new NullPointerException();
      }
      ensureValueIsMutable();
      value_.set(index, value.getNumber());
      onChanged();
      return this;
    }
    /**
     * <pre>
     * Value that supports storing a PriceContentMode array.
     * </pre>
     *
     * <code>repeated .io.evitadb.externalApi.grpc.generated.GrpcPriceContentMode value = 1;</code>
     * @param value The value to add.
     * @return This builder for chaining.
     */
    public Builder addValue(io.evitadb.externalApi.grpc.generated.GrpcPriceContentMode value) {
      if (value == null) {
        throw new NullPointerException();
      }
      ensureValueIsMutable();
      value_.add(value.getNumber());
      onChanged();
      return this;
    }
    /**
     * <pre>
     * Value that supports storing a PriceContentMode array.
     * </pre>
     *
     * <code>repeated .io.evitadb.externalApi.grpc.generated.GrpcPriceContentMode value = 1;</code>
     * @param values The value to add.
     * @return This builder for chaining.
     */
    public Builder addAllValue(
        java.lang.Iterable<? extends io.evitadb.externalApi.grpc.generated.GrpcPriceContentMode> values) {
      ensureValueIsMutable();
      for (io.evitadb.externalApi.grpc.generated.GrpcPriceContentMode value : values) {
        value_.add(value.getNumber());
      }
      onChanged();
      return this;
    }
    /**
     * <pre>
     * Value that supports storing a PriceContentMode array.
     * </pre>
     *
     * <code>repeated .io.evitadb.externalApi.grpc.generated.GrpcPriceContentMode value = 1;</code>
     * @return This builder for chaining.
     */
    public Builder clearValue() {
      value_ = java.util.Collections.emptyList();
      bitField0_ = (bitField0_ & ~0x00000001);
      onChanged();
      return this;
    }
    /**
     * <pre>
     * Value that supports storing a PriceContentMode array.
     * </pre>
     *
     * <code>repeated .io.evitadb.externalApi.grpc.generated.GrpcPriceContentMode value = 1;</code>
     * @return A list containing the enum numeric values on the wire for value.
     */
    public java.util.List<java.lang.Integer>
    getValueValueList() {
      return java.util.Collections.unmodifiableList(value_);
    }
    /**
     * <pre>
     * Value that supports storing a PriceContentMode array.
     * </pre>
     *
     * <code>repeated .io.evitadb.externalApi.grpc.generated.GrpcPriceContentMode value = 1;</code>
     * @param index The index of the value to return.
     * @return The enum numeric value on the wire of value at the given index.
     */
    public int getValueValue(int index) {
      return value_.get(index);
    }
    /**
     * <pre>
     * Value that supports storing a PriceContentMode array.
     * </pre>
     *
     * <code>repeated .io.evitadb.externalApi.grpc.generated.GrpcPriceContentMode value = 1;</code>
     * @param index The index of the value to return.
     * @return The enum numeric value on the wire of value at the given index.
     * @return This builder for chaining.
     */
    public Builder setValueValue(
        int index, int value) {
      ensureValueIsMutable();
      value_.set(index, value);
      onChanged();
      return this;
    }
    /**
     * <pre>
     * Value that supports storing a PriceContentMode array.
     * </pre>
     *
     * <code>repeated .io.evitadb.externalApi.grpc.generated.GrpcPriceContentMode value = 1;</code>
     * @param value The enum numeric value on the wire for value to add.
     * @return This builder for chaining.
     */
    public Builder addValueValue(int value) {
      ensureValueIsMutable();
      value_.add(value);
      onChanged();
      return this;
    }
    /**
     * <pre>
     * Value that supports storing a PriceContentMode array.
     * </pre>
     *
     * <code>repeated .io.evitadb.externalApi.grpc.generated.GrpcPriceContentMode value = 1;</code>
     * @param values The enum numeric values on the wire for value to add.
     * @return This builder for chaining.
     */
    public Builder addAllValueValue(
        java.lang.Iterable<java.lang.Integer> values) {
      ensureValueIsMutable();
      for (int value : values) {
        value_.add(value);
      }
      onChanged();
      return this;
    }
    @java.lang.Override
    public final Builder setUnknownFields(
        final com.google.protobuf.UnknownFieldSet unknownFields) {
      return super.setUnknownFields(unknownFields);
    }

    @java.lang.Override
    public final Builder mergeUnknownFields(
        final com.google.protobuf.UnknownFieldSet unknownFields) {
      return super.mergeUnknownFields(unknownFields);
    }


    // @@protoc_insertion_point(builder_scope:io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray)
  }

  // @@protoc_insertion_point(class_scope:io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray)
  private static final io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray DEFAULT_INSTANCE;
  static {
    DEFAULT_INSTANCE = new io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray();
  }

  public static io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  private static final com.google.protobuf.Parser<GrpcPriceContentModeArray>
      PARSER = new com.google.protobuf.AbstractParser<GrpcPriceContentModeArray>() {
    @java.lang.Override
    public GrpcPriceContentModeArray parsePartialFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return new GrpcPriceContentModeArray(input, extensionRegistry);
    }
  };

  public static com.google.protobuf.Parser<GrpcPriceContentModeArray> parser() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.protobuf.Parser<GrpcPriceContentModeArray> getParserForType() {
    return PARSER;
  }

  @java.lang.Override
  public io.evitadb.externalApi.grpc.generated.GrpcPriceContentModeArray getDefaultInstanceForType() {
    return DEFAULT_INSTANCE;
  }

}

