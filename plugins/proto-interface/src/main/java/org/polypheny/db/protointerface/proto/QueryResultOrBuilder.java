package org.polypheny.db.protointerface.proto;// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: Polypheny-DB/plugins/proto-interface/src/main/proto/protointerface.proto

public interface QueryResultOrBuilder extends
    // @@protoc_insertion_point(interface_extends:org.polypheny.db.protointerface.proto.QueryResult)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>bool noResult = 1;</code>
   */
  boolean getNoResult();

  /**
   * <code>.org.polypheny.db.protointerface.proto.Frame frame = 2;</code>
   */
  boolean hasFrame();
  /**
   * <code>.org.polypheny.db.protointerface.proto.Frame frame = 2;</code>
   */
  Frame getFrame();
  /**
   * <code>.org.polypheny.db.protointerface.proto.Frame frame = 2;</code>
   */
  FrameOrBuilder getFrameOrBuilder();

  /**
   * <code>int32 count = 3;</code>
   */
  int getCount();

  /**
   * <code>int64 bigCount = 4;</code>
   */
  long getBigCount();

  public QueryResult.ResultCase getResultCase();
}
