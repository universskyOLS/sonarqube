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

import java.util.Optional;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.web.UserRole;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.qualitygate.QualityGateDto;
import org.sonar.server.component.ComponentFinder;
import org.sonar.server.component.ComponentFinder.ParamNames;
import org.sonar.server.qualitygate.QualityGates;
import org.sonar.server.user.UserSession;
import org.sonarqube.ws.WsQualityGates.GetByProjectWsResponse;

import static org.sonar.core.util.Uuids.UUID_EXAMPLE_01;
import static org.sonar.server.user.AbstractUserSession.insufficientPrivilegesException;
import static org.sonar.server.ws.KeyExamples.KEY_PROJECT_EXAMPLE_001;
import static org.sonar.server.ws.WsUtils.writeProtobuf;
import static org.sonarqube.ws.client.qualitygate.QualityGatesWsParameters.PARAM_PROJECT_ID;
import static org.sonarqube.ws.client.qualitygate.QualityGatesWsParameters.PARAM_PROJECT_KEY;

public class GetByProjectAction implements QualityGatesWsAction {
  private final UserSession userSession;
  private final DbClient dbClient;
  private final ComponentFinder componentFinder;
  private final QualityGates qualityGates;

  public GetByProjectAction(UserSession userSession, DbClient dbClient, ComponentFinder componentFinder, QualityGates qualityGates) {
    this.userSession = userSession;
    this.dbClient = dbClient;
    this.componentFinder = componentFinder;
    this.qualityGates = qualityGates;
  }

  @Override
  public void define(WebService.NewController context) {
    WebService.NewAction action = context.createAction("get_by_project")
      .setInternal(true)
      .setSince("6.1")
      .setDescription("Get the quality gate of a project.<br> " +
        "Either project id or project key must be provided, not both.")
      .setResponseExample(getClass().getResource("get_by_project-example.json"))
      .setHandler(this);

    action.createParam(PARAM_PROJECT_ID)
      .setDescription("Project id")
      .setExampleValue(UUID_EXAMPLE_01);

    action.createParam(PARAM_PROJECT_KEY)
      .setDescription("Project key")
      .setExampleValue(KEY_PROJECT_EXAMPLE_001);
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    DbSession dbSession = dbClient.openSession(false);
    try {
      ComponentDto project = getProject(dbSession, request.param(PARAM_PROJECT_ID), request.param(PARAM_PROJECT_KEY));
      QualityGateData data = getQualityGate(dbSession, project.getId());

      writeProtobuf(buildResponse(data), request, response);
    } finally {
      dbClient.closeSession(dbSession);
    }
  }

  private ComponentDto getProject(DbSession dbSession, String projectUuid, String projectKey) {
    ComponentDto project = componentFinder.getByUuidOrKey(dbSession, projectUuid, projectKey, ParamNames.PROJECT_ID_AND_KEY);

    if (!userSession.hasComponentUuidPermission(UserRole.USER, projectUuid) &&
      !userSession.hasComponentUuidPermission(UserRole.ADMIN, projectUuid)) {
      throw insufficientPrivilegesException();
    }

    return project;
  }

  private static GetByProjectWsResponse buildResponse(QualityGateData data) {
    if (!data.qualityGate.isPresent()) {
      return GetByProjectWsResponse.getDefaultInstance();
    }

    QualityGateDto qualityGate = data.qualityGate.get();
    GetByProjectWsResponse.Builder response = GetByProjectWsResponse.newBuilder();

    response.getQualityGateBuilder()
      .setId(String.valueOf(qualityGate.getId()))
      .setName(qualityGate.getName())
      .setDefault(data.isDefault);

    return response.build();
  }

  private QualityGateData getQualityGate(DbSession dbSession, long componentId) {
    Optional<Long> qualityGateId = dbClient.projectQgateAssociationDao().selectQGateIdByComponentId(dbSession, componentId);

    return qualityGateId.isPresent()
      ? new QualityGateData(Optional.ofNullable(dbClient.qualityGateDao().selectById(dbSession, qualityGateId.get())), false)
      : new QualityGateData(Optional.ofNullable(qualityGates.getDefault()), true);
  }

  private static class QualityGateData {
    private final Optional<QualityGateDto> qualityGate;
    private final boolean isDefault;

    private QualityGateData(Optional<QualityGateDto> qualityGate, boolean isDefault) {
      this.qualityGate = qualityGate;
      this.isDefault = isDefault;
    }
  }
}
