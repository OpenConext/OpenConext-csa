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

package nl.surfnet.coin.csa.service.impl;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

import nl.surfnet.coin.csa.dao.CompoundServiceProviderDao;
import nl.surfnet.coin.csa.dao.LmngIdentifierDao;
import nl.surfnet.coin.csa.domain.Article;
import nl.surfnet.coin.csa.domain.CompoundServiceProvider;
import nl.surfnet.coin.csa.domain.IdentityProvider;
import nl.surfnet.coin.csa.domain.ServiceProvider;
import nl.surfnet.coin.csa.service.CrmService;
import nl.surfnet.coin.csa.service.ServiceProviderService;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CompoundSPServiceTest {

  @InjectMocks
  private CompoundSPService cspService;

  @Mock
  private ServiceProviderService serviceProviderService;

  @Mock
  private CrmService licensingService;

  @Mock
  private CompoundServiceProviderDao compoundServiceProviderDao;

  @Mock
  private LmngIdentifierDao lmngIdentifierDao;

  @Before
  public void setUp() throws Exception {
    cspService = new CompoundSPService() ;
    MockitoAnnotations.initMocks(this);
  }

  /**
   * 
   * Get a list of all CSPs for a given IDP. This test checks that for SPs in
   * the ServiceProviderService that have no corresponding CSP yet, one will be
   * created.
   */
  @Test
  public void testGetCSPsByIdp() throws Exception {

    // This is the IDP for whom we want all csps.
    IdentityProvider idp = new IdentityProvider("idpId", "institutionid", "name");

    // Two SPs exist
    List<ServiceProvider> sps = new ArrayList<ServiceProvider>();
    ServiceProvider sp1 = new ServiceProvider("spId1");
    ServiceProvider sp2 = new ServiceProvider("spId2");
    sps.add(sp1);
    sps.add(sp2);

    // There is one existing CSP, belonging to sp1.
    List<CompoundServiceProvider> csps = new ArrayList<CompoundServiceProvider>();
    CompoundServiceProvider csp1 = CompoundServiceProvider.builder(sp1, new Article());
    csps.add(csp1);

    when(serviceProviderService.getServiceProvider("spId1")).thenReturn(sp1);
    when(serviceProviderService.getServiceProvider("spId2")).thenReturn(sp2);
    when(compoundServiceProviderDao.findAll()).thenReturn(csps);
    when(serviceProviderService.getAllServiceProviders("idpId")).thenReturn(sps);

    cspService.getCSPsByIdp(idp);

    // Check that the 'missing' CSP is saved
    verify(compoundServiceProviderDao, times(1)).saveOrUpdate(any(CompoundServiceProvider.class));
  }

  @Test
  public void testGetCSPById() throws Exception {
    List<ServiceProvider> sps = new ArrayList<ServiceProvider>();
    ServiceProvider sp1 = new ServiceProvider("spId1");
    ServiceProvider sp2 = new ServiceProvider("spId2");
    sps.add(sp1);
    sps.add(sp2);
    when(serviceProviderService.getAllServiceProviders("idpid")).thenReturn(sps);
    when(serviceProviderService.getServiceProvider("spId1")).thenReturn(sp1);

    CompoundServiceProvider csp = CompoundServiceProvider.builder(sp1, new Article());
    when(compoundServiceProviderDao.findById(1L)).thenReturn(csp);
    IdentityProvider idp = new IdentityProvider("idpid", "instid", "thename");
    CompoundServiceProvider gottenCSP = cspService.getCSPById(idp, 1L);
    assertTrue(csp == gottenCSP);
    assertTrue(sp1 == gottenCSP.getServiceProvider());
  }

}
