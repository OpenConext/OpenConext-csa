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

package nl.surfnet.coin.selfservice.control.requests;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;
import javax.validation.Valid;

import nl.surfnet.coin.selfservice.command.LinkRequest;
import nl.surfnet.coin.selfservice.control.BaseController;
import nl.surfnet.coin.selfservice.domain.Action;
import nl.surfnet.coin.selfservice.domain.CoinUser;
import nl.surfnet.coin.selfservice.domain.IdentityProvider;
import nl.surfnet.coin.selfservice.domain.JiraTask;
import nl.surfnet.coin.selfservice.domain.ServiceProvider;
import nl.surfnet.coin.selfservice.service.ActionsService;
import nl.surfnet.coin.selfservice.service.JiraService;
import nl.surfnet.coin.selfservice.service.PersonAttributeLabelService;
import nl.surfnet.coin.selfservice.service.ServiceProviderService;
import nl.surfnet.coin.selfservice.service.impl.PersonAttributeLabelServiceJsonImpl;
import nl.surfnet.coin.selfservice.util.SpringSecurity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/requests")
@SessionAttributes(value = "linkrequest")
public class LinkrequestController extends BaseController {

  private static final Logger LOG = LoggerFactory.getLogger(LinkrequestController.class);

  @Resource(name = "providerService")
  private ServiceProviderService providerService;

  @Resource(name = "jiraService")
  private JiraService jiraService;

  @Resource(name = "actionsService")
  private ActionsService actionsService;
  
  private PersonAttributeLabelService personAttributeLabelService = new PersonAttributeLabelServiceJsonImpl("classpath:person_attributes.json"); 

  /**
   * Controller for request form page.
   * 
   * @param spEntityId
   *          the entity id
   * @return ModelAndView
   */
  @RequestMapping(value = "/linkrequest.shtml", method = RequestMethod.GET)
  public ModelAndView spLinkRequest(@RequestParam String spEntityId, @RequestParam Long compoundSpId, @ModelAttribute(value = "selectedidp") IdentityProvider selectedidp) {
    Map<String, Object> m = new HashMap<String, Object>();
    final ServiceProvider sp = providerService.getServiceProvider(spEntityId, selectedidp.getId());
    m.put("sp", sp);
    m.put("compoundSpId", compoundSpId);
    m.put("linkrequest", new LinkRequest());
    m.put("personAttributeLabels", personAttributeLabelService.getAttributeLabelMap());
    return new ModelAndView("requests/linkrequest", m);
  }

  @RequestMapping(value = "/linkrequest.shtml", method = RequestMethod.POST)
  public ModelAndView spRequestPost(@RequestParam String spEntityId, @RequestParam Long compoundSpId,
                                    @Valid @ModelAttribute("linkrequest") LinkRequest linkrequest, BindingResult result,
                                    @ModelAttribute(value = "selectedidp") IdentityProvider selectedidp,
                                    SessionStatus sessionStatus) {
    Map<String, Object> m = new HashMap<String, Object>();
    final ServiceProvider sp = providerService.getServiceProvider(spEntityId, selectedidp.getId());
    m.put("sp", sp);
    m.put("compoundSpId", compoundSpId);

    if (result.hasErrors()) {
      LOG.debug("Errors in data binding, will return to form view: {}", result.getAllErrors());
      return new ModelAndView("requests/linkrequest", m);
    } else {
      final CoinUser currentUser = SpringSecurity.getCurrentUser();
      final JiraTask task = new JiraTask.Builder().body(currentUser.getEmail() + ("\n\n" + linkrequest.getNotes()))
          .identityProvider(currentUser.getIdp()).serviceProvider(spEntityId).institution(currentUser.getInstitutionId())
          .issueType(JiraTask.Type.LINKREQUEST).status(JiraTask.Status.OPEN).build();
      try {
        final String issueKey = jiraService.create(task, currentUser);
        actionsService.registerJiraIssueCreation(issueKey, task, currentUser.getUid(), currentUser.getDisplayName());
        m.put("issueKey", issueKey);
        sessionStatus.setComplete();
        return new ModelAndView("requests/linkrequest-thanks", m);
      } catch (IOException e) {
        LOG.debug("Error while trying to create Jira issue. Will return to form view", e);
        m.put("jiraError", e.getMessage());
        return new ModelAndView("requests/linkrequest", m);
      }
    }
  }
  
  @RequestMapping(value = "requests-overview.shtml")
  public ModelAndView listActions(@ModelAttribute(value = "selectedidp") IdentityProvider selectedidp)
      throws IOException {
    Map<String, Object> model = new HashMap<String, Object>();

    actionsService.synchronizeWithJira(selectedidp.getId());
    final List<Action> actions = actionsService.getActions(selectedidp.getId());
    Collections.sort(actions, Collections.reverseOrder(Action.sortByDateAsc()));
    model.put("actionList", actions);
    return new ModelAndView("requests-overview", model);
  }

}
