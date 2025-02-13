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
package org.apache.iotdb.db.qp;

import org.apache.iotdb.commons.conf.IoTDBConstant;
import org.apache.iotdb.db.exception.metadata.IllegalPathException;
import org.apache.iotdb.db.exception.query.LogicalOperatorException;
import org.apache.iotdb.db.exception.query.LogicalOptimizeException;
import org.apache.iotdb.db.exception.query.PathNumOverLimitException;
import org.apache.iotdb.db.exception.query.QueryProcessException;
import org.apache.iotdb.db.protocol.rest.handler.PhysicalPlanValidationHandler;
import org.apache.iotdb.db.qp.logical.Operator;
import org.apache.iotdb.db.qp.logical.crud.FilterOperator;
import org.apache.iotdb.db.qp.logical.crud.QueryOperator;
import org.apache.iotdb.db.qp.logical.crud.SelectIntoOperator;
import org.apache.iotdb.db.qp.logical.crud.WhereComponent;
import org.apache.iotdb.db.qp.physical.PhysicalPlan;
import org.apache.iotdb.db.qp.strategy.LogicalChecker;
import org.apache.iotdb.db.qp.strategy.LogicalGenerator;
import org.apache.iotdb.db.qp.strategy.PhysicalGenerator;
import org.apache.iotdb.db.qp.strategy.optimizer.ConcatPathOptimizer;
import org.apache.iotdb.db.qp.strategy.optimizer.DnfFilterOptimizer;
import org.apache.iotdb.db.qp.strategy.optimizer.MergeSingleFilterOptimizer;
import org.apache.iotdb.db.qp.strategy.optimizer.RemoveNotOptimizer;
import org.apache.iotdb.service.rpc.thrift.TSLastDataQueryReq;
import org.apache.iotdb.service.rpc.thrift.TSRawDataQueryReq;

import java.time.ZoneId;

/** provide a integration method for other user. */
public class Planner {

  public Planner() {
    // do nothing
  }

  public PhysicalPlan parseSQLToPhysicalPlan(String sqlStr) throws QueryProcessException {
    return parseSQLToPhysicalPlan(
        sqlStr, ZoneId.systemDefault(), IoTDBConstant.ClientVersion.V_0_13);
  }

  public PhysicalPlan parseSQLToPhysicalPlan(
      String sqlStr, ZoneId zoneId, IoTDBConstant.ClientVersion clientVersion)
      throws QueryProcessException {
    // from SQL to logical operator
    Operator operator = LogicalGenerator.generate(sqlStr, zoneId, clientVersion);
    return generatePhysicalPlanFromOperator(operator, clientVersion);
  }

  /** convert raw data query to physical plan directly */
  public PhysicalPlan rawDataQueryReqToPhysicalPlan(
      TSRawDataQueryReq rawDataQueryReq, ZoneId zoneId, IoTDBConstant.ClientVersion clientVersion)
      throws IllegalPathException, QueryProcessException {
    // from TSRawDataQueryReq to logical operator
    Operator operator = LogicalGenerator.generate(rawDataQueryReq, zoneId);
    return generatePhysicalPlanFromOperator(operator, clientVersion);
  }

  /** convert last data query to physical plan directly */
  public PhysicalPlan lastDataQueryReqToPhysicalPlan(
      TSLastDataQueryReq lastDataQueryReq, ZoneId zoneId, IoTDBConstant.ClientVersion clientVersion)
      throws QueryProcessException, IllegalPathException {
    // from TSLastDataQueryReq to logical operator
    Operator operator = LogicalGenerator.generate(lastDataQueryReq, zoneId);
    return generatePhysicalPlanFromOperator(operator, clientVersion);
  }

  public PhysicalPlan parseSQLToRestQueryPlan(String sqlStr, ZoneId zoneId)
      throws QueryProcessException {
    // from SQL to logical operator
    Operator operator = LogicalGenerator.generate(sqlStr, zoneId);
    // extra check for rest query
    PhysicalPlanValidationHandler.checkRestQuery(operator);
    return generatePhysicalPlanFromOperator(operator, IoTDBConstant.ClientVersion.V_0_13);
  }

  public PhysicalPlan parseSQLToGrafanaQueryPlan(String sqlStr, ZoneId zoneId)
      throws QueryProcessException {
    // from SQL to logical operator
    Operator operator = LogicalGenerator.generate(sqlStr, zoneId);
    // extra check for grafana query
    PhysicalPlanValidationHandler.checkGrafanaQuery(operator);
    return generatePhysicalPlanFromOperator(operator, IoTDBConstant.ClientVersion.V_0_13);
  }

  public PhysicalPlan operatorToPhysicalPlan(Operator operator) throws QueryProcessException {
    return generatePhysicalPlanFromOperator(operator, IoTDBConstant.ClientVersion.V_0_13);
  }

  private PhysicalPlan generatePhysicalPlanFromOperator(
      Operator operator, IoTDBConstant.ClientVersion clientVersion) throws QueryProcessException {
    // if client version is before 0.13, match path with prefix
    operator.setPrefixMatchPath(IoTDBConstant.ClientVersion.V_0_12.equals(clientVersion));
    // check if there are logical errors
    LogicalChecker.check(operator);
    // optimize the logical operator
    operator = logicalOptimize(operator);
    // from logical operator to physical plan
    return generatePhysicalPlanFromOperator(operator);
  }

  protected PhysicalPlan generatePhysicalPlanFromOperator(Operator operator)
      throws QueryProcessException {
    // from logical operator to physical plan
    return new PhysicalGenerator().transformToPhysicalPlan(operator);
  }

  /**
   * given an unoptimized logical operator tree and return a optimized result.
   *
   * @param operator unoptimized logical operator
   * @return optimized logical operator
   * @throws LogicalOptimizeException exception in logical optimizing
   */
  protected Operator logicalOptimize(Operator operator)
      throws LogicalOperatorException, PathNumOverLimitException {
    switch (operator.getType()) {
      case QUERY:
      case QUERY_INDEX:
        return optimizeQueryOperator((QueryOperator) operator);
      case SELECT_INTO:
        return optimizeSelectIntoOperator((SelectIntoOperator) operator);
      default:
        return operator;
    }
  }

  /**
   * given an unoptimized query operator and return an optimized result.
   *
   * @param root unoptimized query operator
   * @return optimized query operator
   * @throws LogicalOptimizeException exception in query optimizing
   */
  private QueryOperator optimizeQueryOperator(QueryOperator root)
      throws LogicalOperatorException, PathNumOverLimitException {
    root = (QueryOperator) new ConcatPathOptimizer().transform(root);

    WhereComponent whereComponent = root.getWhereComponent();
    if (whereComponent == null) {
      return root;
    }
    FilterOperator filter = whereComponent.getFilterOperator();
    filter = new RemoveNotOptimizer().optimize(filter);
    filter = new DnfFilterOptimizer().optimize(filter);
    filter = new MergeSingleFilterOptimizer().optimize(filter);
    whereComponent.setFilterOperator(filter);

    return root;
  }

  private Operator optimizeSelectIntoOperator(SelectIntoOperator operator)
      throws PathNumOverLimitException, LogicalOperatorException {
    operator.setQueryOperator(optimizeQueryOperator(operator.getQueryOperator()));
    return operator;
  }
}
