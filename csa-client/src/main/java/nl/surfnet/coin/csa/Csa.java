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

package nl.surfnet.coin.csa;

import nl.surfnet.coin.csa.model.Action;
import nl.surfnet.coin.csa.model.InstitutionIdentityProvider;
import nl.surfnet.coin.csa.model.Service;
import nl.surfnet.coin.csa.model.Taxonomy;
import nl.surfnet.coin.shared.oauth.OauthClient;

import java.util.List;

/**
 * Interface of CSA, the Cloud Services API.
 *
 */
public interface Csa {

  /**
   * Get a list of all services available to anyone.
   */
  List<Service> getPublicServices();

  /**
   * Get a list oof all protected services scoped by the Idp of the logged in person
   */
  List<Service> getProtectedServices();

    /**
     * Get a list of services, scoped by the given IDP entity ID
     */
  List<Service> getServicesForIdp(String idpEntityId);

  /**
   * Get a service's details, scoped by the given IDP entity ID
   * @param idpEntityId
   * @param serviceId
   * @return
   */
  Service getServiceForIdp(String idpEntityId, long serviceId);
  
  /**
   * Get a service's details, scoped by the given IDP entity ID and SP entity ID
   * @param idpEntityId idp entity ID
   * @param spEntityId sp entity ID
   * @return
   */
  Service getServiceForIdp(String idpEntityId, String spEntityId);

  /**
   * Setter for base location of CSA
   * @param location base URL of CSA
   */
  void setCsaBaseLocation(String location);

  Taxonomy getTaxonomy() ;

  List<Action> getJiraActions(String idpEntityId);

  Action createAction(Action action);

  List<InstitutionIdentityProvider> getInstitutionIdentityProviders(String identityProviderId);

  public void setOauthClient(OauthClient client);
}
