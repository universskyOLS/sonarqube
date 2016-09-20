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

import java.util.Collection;
import java.util.stream.Collectors;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.metric.MetricDto;
import org.sonar.server.user.UserSession;

import static org.sonar.api.measures.CoreMetrics.ALERT_STATUS_KEY;
import static org.sonar.api.measures.Metric.ValueType.DATA;
import static org.sonar.api.measures.Metric.ValueType.DISTRIB;
import static org.sonar.core.permission.GlobalPermissions.QUALITY_GATE_ADMIN;
import static org.sonar.server.ws.WsUtils.writeProtobuf;
import static org.sonarqube.ws.WsQualityGates.AppWsResponse;

public class AppAction implements QualityGatesWsAction {

  private final UserSession userSession;
  private final DbClient dbClient;

  public AppAction(UserSession userSession, DbClient dbClient) {
    this.userSession = userSession;
    this.dbClient = dbClient;
  }

  @Override
  public void define(WebService.NewController controller) {
    controller.createAction("app")
      .setInternal(true)
      .setDescription("Get initialization items for the admin UI. For internal use")
      .setResponseExample(getClass().getResource("app-example.json"))
      .setSince("4.3")
      .setHandler(this);
  }

  @Override
  public void handle(Request request, Response response) {
    AppWsResponse.Builder responseBuilder = AppWsResponse.newBuilder();
    addPermissions(responseBuilder);
    addMetrics(responseBuilder);
    writeProtobuf(responseBuilder.build(), request, response);
  }

  private void addPermissions(AppWsResponse.Builder responseBuilder) {
    responseBuilder.setEdit(userSession.hasPermission(QUALITY_GATE_ADMIN));
  }

  private void addMetrics(AppWsResponse.Builder builder) {
    for (MetricDto metric : loadMetrics()) {
      builder.addMetricsBuilder()
        .setId(metric.getId())
        .setKey(metric.getKey())
        .setName(metric.getShortName())
        .setType(metric.getValueType())
        .setDomain(metric.getDomain())
        .setHidden(metric.isHidden());
    }
  }

  private Collection<MetricDto> loadMetrics() {
    DbSession dbSession = dbClient.openSession(false);
    try {
      return dbClient.metricDao().selectEnabled(dbSession).stream()
        .filter(metric -> !isDataType(metric) && !ALERT_STATUS_KEY.equals(metric.getKey()))
        .collect(Collectors.toList());
    } finally {
      dbClient.closeSession(dbSession);
    }
  }

  private static boolean isDataType(MetricDto metric) {
    return DATA.name().equals(metric.getValueType()) || DISTRIB.name().equals(metric.getValueType());
  }

}
