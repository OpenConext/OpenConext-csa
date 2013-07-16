/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package nl.surfnet.coin.csa.api.cache;

import nl.surfnet.coin.csa.domain.IdentityProvider;
import nl.surfnet.coin.csa.service.IdentityProviderService;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static junit.framework.Assert.assertEquals;
import static org.hibernate.validator.util.Contracts.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProviderCacheTest {

  public static final String IDP_ID = "http://mock-idp";
  private ProviderCache cache;
  private IdentityProviderService service;

  @Test
  public void testGetServiceProviderIdentifiers() throws Exception {
    List<String> sps = getSPs();

    when(service.getLinkedServiceProviderIDs(IDP_ID)).thenReturn(sps);

    List<String> serviceProviderIdentifiers = cache.getServiceProviderIdentifiers(IDP_ID);
    assertEquals(1, serviceProviderIdentifiers.size());

    sps = getSPs();
    sps.add("sp2");
    when(service.getLinkedServiceProviderIDs(IDP_ID)).thenReturn(sps);

    serviceProviderIdentifiers = cache.getServiceProviderIdentifiers(IDP_ID);
    assertEquals(1, serviceProviderIdentifiers.size());

    //now wait for the cache to be updated
    Thread.sleep(1250);

    serviceProviderIdentifiers = cache.getServiceProviderIdentifiers(IDP_ID);
    assertEquals(2, serviceProviderIdentifiers.size());
  }

  @Test
  public void tesGetIdentityProvider() {
    String idpEntityId = "unknown-idp";
    IdentityProvider identityProvider = cache.getIdentityProvider(idpEntityId);
    assertNull(identityProvider);

    when(service.getIdentityProvider(idpEntityId)).thenReturn(new IdentityProvider(idpEntityId, "institution", "idp1"));
    identityProvider = cache.getIdentityProvider(idpEntityId);
    assertNotNull(identityProvider);
  }

  @Test
  public void testGetIdentityProvider() throws InterruptedException {
    IdentityProvider idp1 = new IdentityProvider("idp1", "institution", "idp1");
    IdentityProvider idp2 = new IdentityProvider("idp2", "institution", "idp2");
    IdentityProvider idp3 = new IdentityProvider("idp3", "institution", "idp3");

    List<IdentityProvider> listWithTwoIdps = Arrays.asList(idp1, idp2);
    List<IdentityProvider> listWithThreeIdps = Arrays.asList(idp1, idp2, idp3);

    when(service.getAllIdentityProviders()).thenReturn(listWithTwoIdps);
    Thread.sleep(1250);
    assertNotNull(cache.getIdentityProvider("idp1"));
    assertNotNull(cache.getIdentityProvider("idp2"));
    assertNull(cache.getIdentityProvider("idp3"));
    when(service.getAllIdentityProviders()).thenReturn(listWithThreeIdps);
    Thread.sleep(1250);
    assertNotNull(cache.getIdentityProvider("idp1"));
    assertNotNull(cache.getIdentityProvider("idp2"));
    assertNotNull(cache.getIdentityProvider("idp3"));
  }

  private List<String> getSPs() {
    List<String> sps = new ArrayList<String>();
    sps.add("sp1");
    return sps;
  }

  @Before
  public void setUp() throws Exception {
    cache = new ProviderCache();
    cache.setDelay(0);
    cache.setDuration(1000);
    service = mock(IdentityProviderService.class);
    cache.setIdpService(service);

    cache.afterPropertiesSet();
    Thread.sleep(100);
  }
}
