/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.qualitygate.ws;

import java.io.IOException;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.System2;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.metric.MetricDto;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.WsActionTester;
import org.sonar.test.JsonAssert;
import org.sonarqube.ws.MediaTypes;
import org.sonarqube.ws.WsQualityGates.AppWsResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.api.measures.Metric.ValueType.BOOL;
import static org.sonar.api.measures.Metric.ValueType.DATA;
import static org.sonar.api.measures.Metric.ValueType.DISTRIB;
import static org.sonar.api.measures.Metric.ValueType.INT;
import static org.sonar.api.measures.Metric.ValueType.RATING;
import static org.sonar.api.measures.Metric.ValueType.WORK_DUR;
import static org.sonar.core.permission.GlobalPermissions.DASHBOARD_SHARING;
import static org.sonar.core.permission.GlobalPermissions.QUALITY_GATE_ADMIN;
import static org.sonar.db.metric.MetricTesting.newMetricDto;
import static org.sonarqube.ws.MediaTypes.JSON;

public class AppActionTest {

  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();

  @Rule
  public DbTester db = DbTester.create(System2.INSTANCE);

  DbClient dbClient = db.getDbClient();
  DbSession dbSession = db.getSession();

  AppAction underTest = new AppAction(userSession, dbClient);
  WsActionTester ws = new WsActionTester(underTest);

  @Test
  public void return_metrics() throws Exception {
    MetricDto metricDto = dbClient.metricDao().insert(dbSession, newMetricDto()
      .setKey("metric")
      .setShortName("Metric")
      .setDomain("General")
      .setValueType(BOOL.name())
      .setHidden(true));
    dbSession.commit();

    AppWsResponse response = executeRequest();

    List<AppWsResponse.Metric> metrics = response.getMetricsList();
    assertThat(metrics).hasSize(1);
    AppWsResponse.Metric metric = metrics.get(0);
    assertThat(metric.getId()).isEqualTo(metricDto.getId());
    assertThat(metric.getKey()).isEqualTo("metric");
    assertThat(metric.getName()).isEqualTo("Metric");
    assertThat(metric.getDomain()).isEqualTo("General");
    assertThat(metric.getType()).isEqualTo(BOOL.name());
    assertThat(metric.getHidden()).isTrue();
  }

  @Test
  public void return_rating_metrics() throws Exception {
    dbClient.metricDao().insert(dbSession, newMetricDto()
      .setKey("reliability_rating")
      .setShortName("Reliability Rating")
      .setDomain("Reliability")
      .setValueType(RATING.name())
      .setHidden(false));
    dbSession.commit();

    AppWsResponse response = executeRequest();

    List<AppWsResponse.Metric> metrics = response.getMetricsList();
    assertThat(metrics).hasSize(1);
    AppWsResponse.Metric metric = metrics.get(0);
    assertThat(metric.getKey()).isEqualTo("reliability_rating");
    assertThat(metric.getName()).isEqualTo("Reliability Rating");
    assertThat(metric.getDomain()).isEqualTo("Reliability");
    assertThat(metric.getType()).isEqualTo(RATING.name());
    assertThat(metric.getHidden()).isFalse();
  }

  @Test
  public void does_not_return_DISTRIB_metric() throws Exception {
    dbClient.metricDao().insert(dbSession, newMetricDto()
      .setKey("function_complexity_distribution")
      .setShortName("Function Distribution / Complexity")
      .setDomain("Complexity")
      .setValueType(DISTRIB.name())
      .setHidden(false));
    dbSession.commit();

    AppWsResponse response = executeRequest();

    assertThat(response.getMetricsList()).isEmpty();
  }

  @Test
  public void does_not_return_DATA_metric() throws Exception {
    dbClient.metricDao().insert(dbSession, newMetricDto()
      .setKey("ncloc_language_distribution")
      .setShortName("Lines of Code Per Language")
      .setDomain("Size")
      .setValueType(DATA.name())
      .setHidden(false));
    dbSession.commit();

    AppWsResponse response = executeRequest();

    assertThat(response.getMetricsList()).isEmpty();
  }

  @Test
  public void does_not_return_quality_gate_metric() throws Exception {
    dbClient.metricDao().insert(dbSession, newMetricDto()
      .setKey("alert_status")
      .setShortName("Quality Gate Status")
      .setDomain("Releasability")
      .setValueType(INT.name())
      .setHidden(false));
    dbSession.commit();

    AppWsResponse response = executeRequest();

    assertThat(response.getMetricsList()).isEmpty();
  }

  @Test
  public void return_edit_to_false_when_not_quality_gate_permission() throws Exception {
    userSession.login("not-admin").setGlobalPermissions(DASHBOARD_SHARING);

    AppWsResponse response = executeRequest();

    assertThat(response.getEdit()).isFalse();
  }

  @Test
  public void return_edit_to_true_when_quality_gate_permission() throws Exception {
    userSession.login("admin").setGlobalPermissions(QUALITY_GATE_ADMIN);

    AppWsResponse response = executeRequest();

    assertThat(response.getEdit()).isTrue();
  }

  @Test
  public void test_example_json_response() {
    dbClient.metricDao().insert(dbSession,
      newMetricDto()
        .setKey("accessors")
        .setShortName("Accessors")
        .setDomain("Size")
        .setValueType(INT.name())
        .setHidden(true),
      newMetricDto()
        .setKey("blocker_remediation_cost")
        .setShortName("Blocker Technical Debt")
        .setDomain("SQALE")
        .setValueType(WORK_DUR.name())
        .setHidden(false));
    dbSession.commit();

    String result = ws.newRequest()
      .setMediaType(JSON)
      .execute()
      .getInput();

    JsonAssert.assertJson(ws.getDef().responseExampleAsString()).ignoreFields("id").isSimilarTo(result);
  }

  @Test
  public void test_ws_definition() {
    WebService.Action action = ws.getDef();
    assertThat(action).isNotNull();
    assertThat(action.isInternal()).isTrue();
    assertThat(action.isPost()).isFalse();
    assertThat(action.responseExampleAsString()).isNotEmpty();
    assertThat(action.params()).isEmpty();
  }

  private AppWsResponse executeRequest() {
    try {
      return AppWsResponse.parseFrom(ws.newRequest().setMediaType(MediaTypes.PROTOBUF).execute().getInputStream());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
