/*
 * Copyright 2013 SURFnet bv, The Netherlands
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package csa.api.control;


import csa.model.LicenseContactPerson;
import csa.util.LicenseContactPersonService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.List;


@Controller
@RequestMapping
public class LicenseContactPersonController extends BaseApiController {

  private static final Logger LOG = LoggerFactory.getLogger(LicenseContactPersonController.class);

  @Autowired
  private LicenseContactPersonService licenseContactPersonService;

  @RequestMapping(method = RequestMethod.GET, value = "/api/protected/licensecontactperson.json")
  public
  @ResponseBody
  LicenseContactPerson licenseContactPerson(@RequestParam(value = "identityProviderId") String identityProviderId, final HttpServletRequest request) {
    LOG.info("returning licenseContactPerson for CSA");
    List<LicenseContactPerson> licenseContactPersons = licenseContactPersonService.licenseContactPersons(identityProviderId);
    if (licenseContactPersons.isEmpty()) {
      throw new ResourceNotFoundException();
    }
    return licenseContactPersons.get(0);
  }
}
