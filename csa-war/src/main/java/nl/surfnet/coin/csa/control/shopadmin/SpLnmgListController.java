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

package nl.surfnet.coin.csa.control.shopadmin;

import nl.surfnet.coin.csa.command.LmngServiceBinding;
import nl.surfnet.coin.csa.control.BaseController;
import nl.surfnet.coin.csa.dao.CompoundServiceProviderDao;
import nl.surfnet.coin.csa.dao.LmngIdentifierDao;
import nl.surfnet.coin.csa.domain.CompoundServiceProvider;
import nl.surfnet.coin.csa.domain.ServiceProvider;
import nl.surfnet.coin.csa.service.CrmService;
import nl.surfnet.coin.csa.service.ExportService;
import nl.surfnet.coin.csa.service.ServiceProviderService;
import nl.surfnet.coin.csa.service.impl.CompoundSPService;
import nl.surfnet.coin.csa.service.impl.LmngUtil;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

@Controller
@RequestMapping(value = "/shopadmin/*")
public class SpLnmgListController extends BaseController {

  private static final Logger log = LoggerFactory.getLogger(SpLnmgListController.class);

  @Resource(name = "providerService")
  private ServiceProviderService providerService;

  @Resource
  private CrmService licensingService;

  @Autowired
  private LmngIdentifierDao lmngIdentifierDao;

  @Resource
  private CompoundSPService compoundSPService;

  @Autowired
  private CompoundServiceProviderDao compoundServiceProviderDao;
  
  @Autowired
  private ExportService exportService;

  private LmngUtil lmngUtil = new LmngUtil();

  @RequestMapping(value = "/all-spslmng")
  public ModelAndView listAllSpsLmng(Map<String, Object> model) {
    List<LmngServiceBinding> lmngServiceBindings = getAllBindings();
    model.put("bindings", lmngServiceBindings);
    List<LmngServiceBinding> cspOrphans = getOrphans(lmngServiceBindings);
    model.put("orphans", cspOrphans);
    return new ModelAndView("shopadmin/sp-overview", model);
  }

  private List<LmngServiceBinding> getOrphans(List<LmngServiceBinding> lmngServiceBindings) {
    Set<String> spEntitySet = createSpEntitySet(lmngServiceBindings);
    List<CompoundServiceProvider> csps = compoundServiceProviderDao.findAll();
    Iterator<CompoundServiceProvider> cspIter = csps.iterator();
    while (cspIter.hasNext()) {
      CompoundServiceProvider current = cspIter.next();
      if (spEntitySet.contains(current.getServiceProviderEntityId())) {
        cspIter.remove();
      }
    }
    
    List<LmngServiceBinding> result = new ArrayList<LmngServiceBinding>();
    if (csps.size() > 0) {
      for (CompoundServiceProvider current : csps) {
        result.add(new LmngServiceBinding(current.getLmngId(), current.getServiceProvider(), current));
      }
    }
    return result;
  }

  private Set<String> createSpEntitySet(List<LmngServiceBinding> lmngServiceBindings) {
    Set<String> result = new HashSet<>();
    for (LmngServiceBinding current : lmngServiceBindings) {
      if (null != current.getCompoundServiceProvider()) {
        result.add(current.getCompoundServiceProvider().getServiceProviderEntityId());
      }
    }
    return result;
  }

  private List<LmngServiceBinding> getAllBindings() {
    List<LmngServiceBinding> lmngServiceBindings = new ArrayList<LmngServiceBinding>();
    for (ServiceProvider serviceProvider : providerService.getAllServiceProviders(false)) {
      String lmngIdentifier = lmngIdentifierDao.getLmngIdForServiceProviderId(serviceProvider.getId());
      CompoundServiceProvider compoundServiceProvider = compoundSPService.getCSPByServiceProvider(serviceProvider);
      lmngServiceBindings.add(new LmngServiceBinding(lmngIdentifier, serviceProvider, compoundServiceProvider));
    }
    return lmngServiceBindings;
  }
  
  @RequestMapping(value = "/export/csv")
  public @ResponseBody String exportToCSV(HttpServletRequest request, HttpServletResponse response, @RequestParam(value="type", required=false) String type) throws URISyntaxException {
    String result = null;
    List<LmngServiceBinding> lmngServiceBindings = getAllBindings();
    String baseUrl = getBaseUrl(request);
    
    if (StringUtils.isEmpty(type)) {
      result = exportService.exportServiceBindingsCsv(lmngServiceBindings, baseUrl);
    } else if (type.equalsIgnoreCase("orphans")) {
      List<LmngServiceBinding> cspOrphans = getOrphans(lmngServiceBindings);
      result = exportService.exportServiceBindingsCsv(cspOrphans, baseUrl);
    } else {
      throw new IllegalArgumentException("Unknown type given: " + type);
    }
    
    // set content headers for CSV
    response.setHeader("Content-Disposition", "attachment; filename=\"spEntityExport.csv\"");
    response.setContentType("text/csv");
    response.setContentLength(result.length());
    return result;
  }
  
  private String getBaseUrl(HttpServletRequest request) throws URISyntaxException {
    String result = "";
    
    URI myUri = new URI(request.getRequestURL().toString());
    result += myUri.getScheme()+"://"+myUri.getHost();
    if (myUri.getPort() > 0) {
      result += ":"+myUri.getPort();
    }
    
    return result;
  }

  @RequestMapping(value = "/save-splmng", method = RequestMethod.POST)
  public ModelAndView saveLmngServices(HttpServletRequest req) {
    Map<String, Object> model = new HashMap<String, Object>();

    String spId = req.getParameter("spIdentifier");
    String lmngId = req.getParameter("lmngIdentifier");
    Integer index = Integer.valueOf(req.getParameter("index"));

    String isClearPressed = req.getParameter("clearbutton");
    if (StringUtils.isBlank(lmngId) || StringUtils.isNotBlank(isClearPressed)) {
      log.debug("Clearing lmng identifier for ServiceProvider with ID " + spId);
      lmngId = null;
    } else {
      // extra validation (also done in frontend/jquery)
      if (!lmngUtil.isValidGuid(lmngId)) {
        model.put("errorMessage", "jsp.lmng_binding_overview.wrong.guid");
        model.put("messageIndex", index);
        return listAllSpsLmng(model);
      }

      String serviceLmngName = licensingService.getServiceName(lmngId);
      if (serviceLmngName == null) {
        model.put("errorMessage", "jsp.lmng_binding_overview.unknown.guid");
        model.put("messageIndex", index);
      } else {
        model.put("infoMessage", serviceLmngName);
        model.put("messageIndex", index);
      }
      log.debug("Storing lmng identifier " + lmngId + " for ServiceProvider with ID " + spId);
    }
    lmngIdentifierDao.saveOrUpdateLmngIdForServiceProviderId(spId, lmngId);

    return listAllSpsLmng(model);
  }

  @RequestMapping(value = "/update-enduser-visible/{cspId}/{newValue}", method = RequestMethod.PUT)
  public
  @ResponseBody
  String updateCspPublicApi(@PathVariable("cspId") Long cspId, @PathVariable("newValue") boolean newValue) {
    CompoundServiceProvider csp = compoundServiceProviderDao.findById(cspId);
    csp.setAvailableForEndUser(newValue);
    compoundServiceProviderDao.saveOrUpdate(csp);
    log.info("Updated CompoundServiceProvider(" + cspId + ") to be available for end users:" + newValue);
    return "ok";
  }
  
  @RequestMapping(value = "/delete-csp.shtml", method = RequestMethod.POST)
  public void deleteCompoundServiceProvider(@RequestParam("cspId") String postedCspId, HttpServletResponse response) throws IOException {
    log.info("deleting compound service provider with ID " + postedCspId);
    Long cspId = Long.parseLong(postedCspId);
    CompoundServiceProvider csp = compoundServiceProviderDao.findById(cspId);
    compoundServiceProviderDao.delete(csp);
    
    //redirect to services page
    response.sendRedirect("all-spslmng.shtml");
  }
}
