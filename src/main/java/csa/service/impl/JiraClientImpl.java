/*
 * Copyright 2012 SURFnet bv, The Netherlands
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package csa.service.impl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;

import csa.domain.CoinUser;
import csa.model.JiraTask;
import csa.service.impl.deprecated.RemoteCustomFieldValue;
import csa.service.impl.deprecated.RemoteIssue;

public class JiraClientImpl implements JiraClient {
  private static final Logger LOG = LoggerFactory.getLogger(JiraClientImpl.class);

  private final static String STATUS_CLOSED = "6";

  public static final String[] EMPTY_STRINGS = new String[0];
  public static final RemoteCustomFieldValue[] EMPTY_REMOTE_CUSTOM_FIELD_VALUES = new RemoteCustomFieldValue[0];
  public static final String SP_CUSTOM_FIELD = "customfield_10100";
  public static final String IDP_CUSTOM_FIELD = "customfield_10101";
  private static final long DEFAULT_SECURITY_LEVEL = 10100L;

  public static final String TYPE_LINKREQUEST = "13";
  public static final String TYPE_UNLINKREQUEST = "17";
  public static final String TYPE_QUESTION = "16";

  private static final Map<JiraTask.Type, String> TASKTYPE_TO_ISSUETYPE_CODE = ImmutableMap.of(
    JiraTask.Type.QUESTION, "16",
    JiraTask.Type.LINKREQUEST, "13",
    JiraTask.Type.UNLINKREQUEST, "17");

  public static final String PRIORITY_MEDIUM = "3";

  private final String username;
  private final String password;
  private final String baseUrl;

  private final RestTemplate restTemplate;
  private final String projectKey;

  public JiraClientImpl(final String baseUrl, final String username, final String password, final String projectKey) {
    this.username = username;
    this.password = password;
    this.projectKey = projectKey;
    this.baseUrl = baseUrl;

    HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
    BasicCredentialsProvider basicCredentialsProvider = new BasicCredentialsProvider();
    basicCredentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
    httpClientBuilder.setDefaultCredentialsProvider(basicCredentialsProvider);
    CloseableHttpClient httpClient = httpClientBuilder.build();
    this.restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
  }

  @Override
  public String create(final JiraTask task, CoinUser user) {
    RemoteIssue remoteIssue;
    switch (task.getIssueType()) {
      case LINKREQUEST:
      case UNLINKREQUEST:
        remoteIssue = createRequest(task, user);
        break;
      default:
        remoteIssue = createQuestion(task, user);
        break;
    }
    //TODO hans call REST api here with DEFAULT_SECURITY_LEVEL
    // jiraSoapService.createIssueWithSecurityLevel(getToken(), remoteIssue, DEFAULT_SECURITY_LEVEL);
    final RemoteIssue createdIssue = null;


    if (createdIssue == null) {
      return null;
    }
    return createdIssue.getKey();
  }

  private RemoteIssue createQuestion(final JiraTask task, CoinUser user) {
    RemoteIssue remoteIssue = new RemoteIssue();
    remoteIssue.setType(TYPE_QUESTION);
    remoteIssue.setSummary(new StringBuilder().append("Question about ").append(task.getServiceProvider()).toString());
    remoteIssue.setProject(projectKey);
    remoteIssue.setPriority(PRIORITY_MEDIUM);
    StringBuilder description = new StringBuilder();
    description.append("Applicant name: ").append(user.getDisplayName()).append("\n");
    description.append("Applicant email: ").append(user.getEmail()).append("\n");
    description.append("Identity Provider: ").append(task.getIdentityProvider()).append("\n");
    description.append("Service Provider: ").append(task.getServiceProvider()).append("\n");
    description.append("Time: ").append(new SimpleDateFormat("HH:mm dd-MM-yy").format(new Date())).append("\n");
    description.append("Service Provider: ").append(task.getServiceProvider()).append("\n");
    description.append("Request: ").append(task.getBody()).append("\n");
    remoteIssue.setDescription(description.toString());
    appendSpAndIdp(task, remoteIssue);
    return remoteIssue;
  }

  private RemoteIssue createRequest(final JiraTask task, CoinUser user) {
    RemoteIssue remoteIssue = new RemoteIssue();
    remoteIssue.setType(TASKTYPE_TO_ISSUETYPE_CODE.get(task.getIssueType()));
    if (remoteIssue.getType().equals(TYPE_LINKREQUEST)) {
      remoteIssue.setSummary("New connection for IdP " + task.getIdentityProvider() + " to SP " + task.getServiceProvider());
    } else if (remoteIssue.getType().equals(TYPE_UNLINKREQUEST)) {
      remoteIssue.setSummary("Disconnect IdP " + task.getIdentityProvider() + " and SP " + task.getServiceProvider());
    } else {
      throw new IllegalStateException("Unknown type: " + remoteIssue.getType());
    }

    remoteIssue.setProject(projectKey);
    remoteIssue.setPriority(PRIORITY_MEDIUM);
    StringBuilder description = new StringBuilder();
    if (task.getIssueType() == JiraTask.Type.LINKREQUEST) {
      description.append("Request: Create a new connection").append("\n");
    } else {
      description.append("Request: terminate a connection").append("\n");
    }

    description.append("Applicant name: ").append(user.getDisplayName()).append("\n");
    description.append("Applicant email: ").append(user.getEmail()).append("\n");
    description.append("Identity Provider: ").append(task.getIdentityProvider()).append("\n");
    description.append("Service Provider: ").append(task.getServiceProvider()).append("\n");
    description.append("Time: ").append(new SimpleDateFormat("HH:mm dd-MM-yy").format(new Date())).append("\n");
    description.append("Service Provider: ").append(task.getServiceProvider()).append("\n");
    description.append("Remark from user: ").append(task.getBody()).append("\n");
    remoteIssue.setDescription(description.toString());
    appendSpAndIdp(task, remoteIssue);
    return remoteIssue;
  }

  private void appendSpAndIdp(final JiraTask task, final RemoteIssue remoteIssue) {
    final List<RemoteCustomFieldValue> customFieldValues = new ArrayList<>();
    final List<String> spValue = Collections.singletonList(task.getServiceProvider());
    final List<String> idpValue = Collections.singletonList(task.getIdentityProvider());
    customFieldValues.add(new RemoteCustomFieldValue(SP_CUSTOM_FIELD, null, spValue.toArray(EMPTY_STRINGS)));
    customFieldValues.add(new RemoteCustomFieldValue(IDP_CUSTOM_FIELD, null, idpValue.toArray(EMPTY_STRINGS)));
    remoteIssue.setCustomFieldValues(customFieldValues.toArray(EMPTY_REMOTE_CUSTOM_FIELD_VALUES));
  }

  @Override
  public List<JiraTask> getTasks(final List<String> keys)  {
    if (keys == null || keys.size() == 0) {
      return Collections.emptyList();
    }
    List<JiraTask> jiraTasks = new ArrayList<>();
    StringBuilder query = new StringBuilder("project = ");
    query.append(projectKey);
    query.append(" AND key IN(");
    Joiner.on(",").skipNulls().appendTo(query, keys);
    query.append(")");
    if (LOG.isDebugEnabled()) {
      LOG.debug("Sending query to JIRA: " + query.toString());
    }
    final RemoteIssue[] issuesFromJqlSearch = null;
    //final RemoteIssue[] issuesFromJqlSearch = jiraSoapService.getIssuesFromJqlSearch(getToken(), query.toString(), 1000);

    for (RemoteIssue remoteIssue : issuesFromJqlSearch) {
      String identityProvider = fetchValue(IDP_CUSTOM_FIELD, remoteIssue.getCustomFieldValues());
      String serviceProvider = fetchValue(SP_CUSTOM_FIELD, remoteIssue.getCustomFieldValues());
      final JiraTask jiraTask = new JiraTask.Builder().key(remoteIssue.getKey()).identityProvider(identityProvider)
        .serviceProvider(serviceProvider).institution("???").status(fetchStatus(remoteIssue)).body(remoteIssue.getDescription()).build();
      jiraTasks.add(jiraTask);
    }
    return jiraTasks;
  }

  private JiraTask.Status fetchStatus(final RemoteIssue remoteIssue) {
    if (STATUS_CLOSED.equals(remoteIssue.getStatus())) {
      return JiraTask.Status.CLOSED;
    } else {
      return JiraTask.Status.OPEN;
    }
  }

  private String fetchValue(final String name, final RemoteCustomFieldValue[] customFieldValues) {
    for (RemoteCustomFieldValue customFieldValue : customFieldValues) {
      if (name.equals(customFieldValue.getCustomfieldId())) {
        return customFieldValue.getValues()[0];
      }
    }
    return "";
  }


}
