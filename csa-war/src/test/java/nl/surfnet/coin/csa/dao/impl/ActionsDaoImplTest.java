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

package nl.surfnet.coin.csa.dao.impl;

import nl.surfnet.coin.csa.dao.ActionsDao;
import nl.surfnet.coin.csa.model.Action;
import nl.surfnet.coin.csa.model.JiraTask;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.transaction.TransactionConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.junit.matchers.JUnitMatchers.hasItems;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:coin-csa-context.xml",
        "classpath:coin-csa-properties-context.xml",
        "classpath:coin-shared-context.xml"})
@TransactionConfiguration(transactionManager = "csaTransactionManager", defaultRollback = true)
@Transactional

public class ActionsDaoImplTest {

  @Autowired
  private ActionsDao actionsDao;

  @Test
  public void findNone() {
    assertNull(actionsDao.findAction(123123L));
  }

  @Test
  public void saveAndFind() {
    Action a = new Action("key", "userid", "username", "john.doe@nl", JiraTask.Type.QUESTION, JiraTask.Status.OPEN, "body", "idp", "sp",
            "institute", new Date());
    long id = actionsDao.saveAction(a);
    Action savedA = actionsDao.findAction(id);
    assertNotNull(savedA);
    assertThat(savedA.getBody(), is("body"));
    assertThat(savedA.getJiraKey(), is("key"));
    assertThat(savedA.getUserName(), is("username"));
    assertThat(savedA.getSpId(), is("sp"));
    assertThat(savedA.getStatus(), is(JiraTask.Status.OPEN));
    assertThat(savedA.getType(), is(JiraTask.Type.QUESTION));
  }

  @Test
  public void findByIdP() {
    for (int i = 0; i < 3; i++) {
      Action a = new Action("key" + i, "userid", "username", "john.doe@nl", JiraTask.Type.QUESTION, JiraTask.Status.OPEN, "body", "idp",
              "sp", "foobar", new Date());
      actionsDao.saveAction(a);
    }

    final List<Action> actions = actionsDao.findActionsByIdP("idp");
    assertThat(actions.size(), is(3));
    final List<Action> actions2 = actionsDao.findActionsByIdP("another-idp");
    assertThat(actions2.size(), is(0));
  }

  @Test
  public void getJiraKeys() {
    final String idp = "idp123";
    String[] keys = {"TEST-1", "TEST-2", "TEST-3", "TEST-4"};
    for (String key : keys) {
      actionsDao.saveAction(new Action(key, "userid", "username", "john.doe@nl", JiraTask.Type.QUESTION, JiraTask.Status.OPEN, "body", idp, "sp", "institute-123", new Date()));
    }
    final List<String> fetchedKeys = actionsDao.getKeys(idp);
    assertThat(fetchedKeys, hasItems(keys));
  }

  @Test
  public void close() {
    final String jiraKey = "TEST-1346";
    Action a = new Action(jiraKey, "userid", "username", "john.doe@nl", JiraTask.Type.QUESTION, JiraTask.Status.OPEN, "body", "idp", "sp",
            "institute", new Date());
    long id = actionsDao.saveAction(a);

    final Action before = actionsDao.findAction(id);
    assertThat(before.getStatus(), is(JiraTask.Status.OPEN));

    actionsDao.close(jiraKey);

    final Action after = actionsDao.findAction(id);
    assertThat(after.getStatus(), is(JiraTask.Status.CLOSED));
  }

}
