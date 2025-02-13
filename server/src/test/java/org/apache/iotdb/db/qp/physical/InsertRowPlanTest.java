/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.db.qp.physical;

import org.apache.iotdb.db.conf.IoTDBDescriptor;
import org.apache.iotdb.db.exception.StorageEngineException;
import org.apache.iotdb.db.exception.metadata.IllegalPathException;
import org.apache.iotdb.db.exception.metadata.MetadataException;
import org.apache.iotdb.db.exception.query.QueryProcessException;
import org.apache.iotdb.db.metadata.path.PartialPath;
import org.apache.iotdb.db.qp.Planner;
import org.apache.iotdb.db.qp.executor.PlanExecutor;
import org.apache.iotdb.db.qp.physical.PhysicalPlan.PhysicalPlanType;
import org.apache.iotdb.db.qp.physical.crud.InsertRowPlan;
import org.apache.iotdb.db.qp.physical.crud.QueryPlan;
import org.apache.iotdb.db.qp.physical.sys.CreateTemplatePlan;
import org.apache.iotdb.db.qp.physical.sys.SetTemplatePlan;
import org.apache.iotdb.db.service.IoTDB;
import org.apache.iotdb.db.utils.EnvironmentUtils;
import org.apache.iotdb.tsfile.exception.filter.QueryFilterOptimizationException;
import org.apache.iotdb.tsfile.file.metadata.enums.CompressionType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;
import org.apache.iotdb.tsfile.read.common.RowRecord;
import org.apache.iotdb.tsfile.read.query.dataset.QueryDataSet;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class InsertRowPlanTest {

  private final Planner processor = new Planner();

  @Before
  public void before() {
    EnvironmentUtils.envSetUp();
  }

  @After
  public void clean() throws IOException, StorageEngineException {
    IoTDBDescriptor.getInstance().getConfig().setAutoCreateSchemaEnabled(true);
    EnvironmentUtils.cleanEnv();
  }

  @Test
  public void testInsertRowPlan()
      throws QueryProcessException, MetadataException, InterruptedException,
          QueryFilterOptimizationException, StorageEngineException, IOException {
    InsertRowPlan rowPlan = getInsertRowPlan();

    PlanExecutor executor = new PlanExecutor();
    executor.insert(rowPlan);

    QueryPlan queryPlan = (QueryPlan) processor.parseSQLToPhysicalPlan("select * from root.isp.d1");
    QueryDataSet dataSet = executor.processQuery(queryPlan, EnvironmentUtils.TEST_QUERY_CONTEXT);
    Assert.assertEquals(6, dataSet.getPaths().size());
    while (dataSet.hasNext()) {
      RowRecord record = dataSet.next();
      Assert.assertEquals(6, record.getFields().size());
    }
  }

  @Test
  public void testInsertRowPlanWithAlignedTimeseries()
      throws QueryProcessException, MetadataException, InterruptedException,
          QueryFilterOptimizationException, StorageEngineException, IOException {
    InsertRowPlan vectorRowPlan = getInsertAlignedRowPlan();

    PlanExecutor executor = new PlanExecutor();
    executor.insert(vectorRowPlan);

    Assert.assertEquals("[s1, s2, s3]", Arrays.toString(vectorRowPlan.getMeasurementMNodes()));

    QueryPlan queryPlan =
        (QueryPlan) processor.parseSQLToPhysicalPlan("select * from root.isp.d1.GPS");
    QueryDataSet dataSet = executor.processQuery(queryPlan, EnvironmentUtils.TEST_QUERY_CONTEXT);
    Assert.assertEquals(1, dataSet.getPaths().size());
    while (dataSet.hasNext()) {
      RowRecord record = dataSet.next();
      Assert.assertEquals(3, record.getFields().size());
    }
  }

  @Test
  public void testInsertRowPlanWithTreeSchemaTemplate()
      throws QueryProcessException, MetadataException, InterruptedException,
          QueryFilterOptimizationException, StorageEngineException, IOException {
    List<List<String>> measurementList = new ArrayList<>();
    List<String> v1 = Arrays.asList("GPS.s1", "GPS.s2", "GPS.s3");
    measurementList.add(v1);
    List<String> v2 = Arrays.asList("GPS2.s4", "GPS2.s5");
    measurementList.add(v2);
    measurementList.add(Collections.singletonList("s6"));

    List<List<TSDataType>> dataTypesList = new ArrayList<>();
    List<TSDataType> d1 = Arrays.asList(TSDataType.DOUBLE, TSDataType.FLOAT, TSDataType.INT64);
    dataTypesList.add(d1);
    List<TSDataType> d2 = Arrays.asList(TSDataType.INT32, TSDataType.BOOLEAN);
    dataTypesList.add(d2);
    dataTypesList.add(Collections.singletonList(TSDataType.TEXT));

    List<List<TSEncoding>> encodingList = new ArrayList<>();
    List<TSEncoding> e1 = Arrays.asList(TSEncoding.PLAIN, TSEncoding.PLAIN, TSEncoding.PLAIN);
    encodingList.add(e1);
    List<TSEncoding> e2 = Arrays.asList(TSEncoding.PLAIN, TSEncoding.PLAIN);
    encodingList.add(e2);
    encodingList.add(Collections.singletonList(TSEncoding.PLAIN));

    List<List<CompressionType>> compressionTypes = new ArrayList<>();
    compressionTypes.add(
        Arrays.asList(CompressionType.SNAPPY, CompressionType.SNAPPY, CompressionType.SNAPPY));
    compressionTypes.add(Arrays.asList(CompressionType.SNAPPY, CompressionType.SNAPPY));
    compressionTypes.add(Arrays.asList(CompressionType.SNAPPY));

    CreateTemplatePlan plan =
        new CreateTemplatePlan(
            "templateN", measurementList, dataTypesList, encodingList, compressionTypes);

    IoTDB.schemaProcessor.createSchemaTemplate(plan);
    IoTDB.schemaProcessor.setSchemaTemplate(new SetTemplatePlan("templateN", "root.isp.d1"));

    IoTDBDescriptor.getInstance().getConfig().setAutoCreateSchemaEnabled(false);

    InsertRowPlan rowPlan = getInsertAlignedRowPlan(111L);
    PlanExecutor executor = new PlanExecutor();

    executor.insert(rowPlan);
    rowPlan = getInsertAlignedRowPlan(112L);
    executor.insert(rowPlan);
    rowPlan = getInsertAlignedRowPlan(113L);
    executor.insert(rowPlan);

    QueryPlan queryPlan =
        (QueryPlan) processor.parseSQLToPhysicalPlan("select * from root.isp.d1.GPS");
    QueryDataSet dataSet = executor.processQuery(queryPlan, EnvironmentUtils.TEST_QUERY_CONTEXT);

    Assert.assertEquals(1, dataSet.getPaths().size());
    int resSize = 0;
    while (dataSet.hasNext()) {
      Assert.assertEquals(3, dataSet.next().getFields().size());
      resSize++;
    }
    Assert.assertEquals(3, resSize);

    TSDataType[] dataTypes = new TSDataType[] {TSDataType.TEXT};

    String[] columns = new String[1];
    columns[0] = "a";

    rowPlan =
        new InsertRowPlan(
            new PartialPath("root.isp.d1"), 1111L, new String[] {"s6"}, dataTypes, columns, false);
    executor.insert(rowPlan);

    queryPlan = (QueryPlan) processor.parseSQLToPhysicalPlan("select * from root.isp.d1");
    dataSet = executor.processQuery(queryPlan, EnvironmentUtils.TEST_QUERY_CONTEXT);
    Assert.assertEquals(1, dataSet.getPaths().size());
    Assert.assertEquals(true, dataSet.hasNext());

    queryPlan = (QueryPlan) processor.parseSQLToPhysicalPlan("select * from root.isp.d1.**");
    dataSet = executor.processQuery(queryPlan, EnvironmentUtils.TEST_QUERY_CONTEXT);
    Assert.assertEquals(2, dataSet.getPaths().size());
    Assert.assertEquals(true, dataSet.hasNext());
    Assert.assertEquals(5, dataSet.next().getFields().size());
  }

  @Test
  public void testInsertRowPlanWithSchemaTemplate()
      throws QueryProcessException, MetadataException, InterruptedException,
          QueryFilterOptimizationException, StorageEngineException, IOException {
    List<List<String>> measurementList = new ArrayList<>();
    for (int i = 1; i <= 6; i++) {
      measurementList.add(Collections.singletonList("s" + i));
    }

    List<List<TSDataType>> dataTypesList = new ArrayList<>();
    dataTypesList.add(Collections.singletonList(TSDataType.DOUBLE));
    dataTypesList.add(Collections.singletonList(TSDataType.FLOAT));
    dataTypesList.add(Collections.singletonList(TSDataType.INT64));
    dataTypesList.add(Collections.singletonList(TSDataType.INT32));
    dataTypesList.add(Collections.singletonList(TSDataType.BOOLEAN));
    dataTypesList.add(Collections.singletonList(TSDataType.TEXT));

    List<List<TSEncoding>> encodingList = new ArrayList<>();
    for (int i = 1; i <= 6; i++) {
      encodingList.add(Collections.singletonList(TSEncoding.PLAIN));
    }

    List<List<CompressionType>> compressionTypes = new ArrayList<>();
    for (int i = 1; i <= 6; i++) {
      compressionTypes.add(Collections.singletonList(CompressionType.SNAPPY));
    }

    List<String> schemaNames = new ArrayList<>();
    for (int i = 1; i <= 6; i++) {
      schemaNames.add("s" + i);
    }

    CreateTemplatePlan plan =
        new CreateTemplatePlan(
            "template1",
            schemaNames,
            measurementList,
            dataTypesList,
            encodingList,
            compressionTypes);

    IoTDB.schemaProcessor.createSchemaTemplate(plan);
    IoTDB.schemaProcessor.setSchemaTemplate(new SetTemplatePlan("template1", "root.isp.d1"));

    IoTDBDescriptor.getInstance().getConfig().setAutoCreateSchemaEnabled(false);

    InsertRowPlan rowPlan = getInsertRowPlan();

    PlanExecutor executor = new PlanExecutor();
    executor.insert(rowPlan);

    QueryPlan queryPlan = (QueryPlan) processor.parseSQLToPhysicalPlan("select * from root.isp.d1");
    QueryDataSet dataSet = executor.processQuery(queryPlan, EnvironmentUtils.TEST_QUERY_CONTEXT);
    Assert.assertEquals(6, dataSet.getPaths().size());
    while (dataSet.hasNext()) {
      RowRecord record = dataSet.next();
      Assert.assertEquals(6, record.getFields().size());
    }
  }

  @Test
  public void testInsertRowSerialization() throws IllegalPathException, QueryProcessException {
    InsertRowPlan plan1 = getInsertAlignedRowPlan();

    PlanExecutor executor = new PlanExecutor();
    executor.insert(plan1);

    ByteBuffer byteBuffer = ByteBuffer.allocate(10000);
    plan1.serialize(byteBuffer);
    byteBuffer.flip();

    Assert.assertEquals(PhysicalPlanType.INSERT.ordinal(), byteBuffer.get());

    InsertRowPlan plan2 = new InsertRowPlan();
    plan2.deserialize(byteBuffer);

    executor.insert(plan2);
    Assert.assertEquals(plan1, plan2);
  }

  @Test
  public void testInsertRowPlanWithSchemaTemplateFormer()
      throws QueryProcessException, MetadataException, InterruptedException,
          QueryFilterOptimizationException, StorageEngineException, IOException {
    List<List<String>> measurementList = new ArrayList<>();
    List<String> v1 = new ArrayList<>();
    v1.add("GPS.s1");
    v1.add("GPS.s2");
    v1.add("GPS.s3");
    measurementList.add(v1);
    List<String> v2 = new ArrayList<>();
    v2.add("GPS2.s4");
    v2.add("GPS2.s5");
    measurementList.add(v2);
    measurementList.add(Collections.singletonList("s6"));

    List<List<TSDataType>> dataTypesList = new ArrayList<>();
    List<TSDataType> d1 = new ArrayList<>();
    d1.add(TSDataType.DOUBLE);
    d1.add(TSDataType.FLOAT);
    d1.add(TSDataType.INT64);
    dataTypesList.add(d1);
    List<TSDataType> d2 = new ArrayList<>();
    d2.add(TSDataType.INT32);
    d2.add(TSDataType.BOOLEAN);
    dataTypesList.add(d2);
    dataTypesList.add(Collections.singletonList(TSDataType.TEXT));

    List<List<TSEncoding>> encodingList = new ArrayList<>();
    List<TSEncoding> e1 = new ArrayList<>();
    e1.add(TSEncoding.PLAIN);
    e1.add(TSEncoding.PLAIN);
    e1.add(TSEncoding.PLAIN);
    encodingList.add(e1);
    List<TSEncoding> e2 = new ArrayList<>();
    e2.add(TSEncoding.PLAIN);
    e2.add(TSEncoding.PLAIN);
    encodingList.add(e2);
    encodingList.add(Collections.singletonList(TSEncoding.PLAIN));

    List<List<CompressionType>> compressionTypes = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      List<CompressionType> compressorList = new ArrayList<>();
      for (int j = 0; j < 3; j++) {
        compressorList.add(CompressionType.SNAPPY);
      }
      compressionTypes.add(compressorList);
    }

    CreateTemplatePlan plan =
        new CreateTemplatePlan(
            "template1", measurementList, dataTypesList, encodingList, compressionTypes);

    IoTDB.schemaProcessor.createSchemaTemplate(plan);
    IoTDB.schemaProcessor.setSchemaTemplate(new SetTemplatePlan("template1", "root.isp.d1"));

    IoTDBDescriptor.getInstance().getConfig().setAutoCreateSchemaEnabled(false);

    InsertRowPlan rowPlan = getInsertAlignedRowPlan();

    PlanExecutor executor = new PlanExecutor();
    executor.insert(rowPlan);

    QueryPlan queryPlan =
        (QueryPlan) processor.parseSQLToPhysicalPlan("select * from root.isp.d1.GPS");
    QueryDataSet dataSet = executor.processQuery(queryPlan, EnvironmentUtils.TEST_QUERY_CONTEXT);
    Assert.assertEquals(1, dataSet.getPaths().size());
    Assert.assertEquals(true, dataSet.hasNext());
    while (dataSet.hasNext()) {
      RowRecord record = dataSet.next();
      Assert.assertEquals(3, record.getFields().size());
    }
  }

  private InsertRowPlan getInsertRowPlan() throws IllegalPathException {
    long time = 110L;
    TSDataType[] dataTypes =
        new TSDataType[] {
          TSDataType.DOUBLE,
          TSDataType.FLOAT,
          TSDataType.INT64,
          TSDataType.INT32,
          TSDataType.BOOLEAN,
          TSDataType.TEXT
        };

    String[] columns = new String[6];
    columns[0] = 1.0 + "";
    columns[1] = 2 + "";
    columns[2] = 10000 + "";
    columns[3] = 100 + "";
    columns[4] = false + "";
    columns[5] = "hh" + 0;

    return new InsertRowPlan(
        new PartialPath("root.isp.d1"),
        time,
        new String[] {"s1", "s2", "s3", "s4", "s5", "s6"},
        dataTypes,
        columns);
  }

  private InsertRowPlan getInsertAlignedRowPlan() throws IllegalPathException {
    return getInsertAlignedRowPlan(110L);
  }

  private InsertRowPlan getInsertAlignedRowPlan(long time) throws IllegalPathException {
    TSDataType[] dataTypes =
        new TSDataType[] {TSDataType.DOUBLE, TSDataType.FLOAT, TSDataType.INT64};

    String[] columns = new String[3];
    columns[0] = 1.0 + "";
    columns[1] = 2 + "";
    columns[2] = 10000 + "";

    return new InsertRowPlan(
        new PartialPath("root.isp.d1.GPS"),
        time,
        new String[] {"s1", "s2", "s3"},
        dataTypes,
        columns,
        true);
  }
}
