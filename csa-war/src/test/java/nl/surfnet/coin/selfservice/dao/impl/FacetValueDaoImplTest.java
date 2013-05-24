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
package nl.surfnet.coin.selfservice.dao.impl;

import nl.surfnet.coin.csa.model.Facet;
import nl.surfnet.coin.csa.model.FacetValue;
import nl.surfnet.coin.csa.model.LocalizedString;
import nl.surfnet.coin.selfservice.dao.CompoundServiceProviderDao;
import nl.surfnet.coin.selfservice.dao.FacetDao;
import nl.surfnet.coin.selfservice.dao.FacetValueDao;
import nl.surfnet.coin.selfservice.domain.Article;
import nl.surfnet.coin.selfservice.domain.CompoundServiceProvider;
import nl.surfnet.coin.selfservice.domain.InUseFacetValue;
import nl.surfnet.coin.selfservice.domain.ServiceProvider;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.transaction.TransactionConfiguration;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.LocaleResolver;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:coin-selfservice-context.xml",
        "classpath:coin-selfservice-properties-context.xml",
        "classpath:coin-shared-context.xml"})
@TransactionConfiguration(transactionManager = "selfServiceTransactionManager", defaultRollback = true)
@Transactional
public class FacetValueDaoImplTest implements LocaleResolver {

  @Autowired
  private FacetValueDao facetValueDao;

  @Autowired
  private FacetDao facetDao;

  @Autowired
  private CompoundServiceProviderDao compoundServiceProviderDao;

  private Locale currentLocale;

  private ObjectMapper mapper = new ObjectMapper().enable(DeserializationConfig.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY).
          setSerializationInclusion(JsonSerialize.Inclusion.NON_NULL);

  @Test
  public void testRetrieveFacetOnCompoundServicerProvider() {
    Facet facet = createFacetWithValue();
    CompoundServiceProvider csp = createCompoundServerProvider();

    csp.addFacetValue(facet.getFacetValues().first());
    compoundServiceProviderDao.saveOrUpdate(csp);

    csp = compoundServiceProviderDao.findById(csp.getId());
    assertEquals(facet.getName(), csp.getFacetValues().first().getFacet().getName());
  }

  @Test
  public void testLinkingFacetValueAndCsp() {
    Facet facet = createFacetWithValue();
    CompoundServiceProvider csp = createCompoundServerProvider();

    FacetValue cloud = facet.getFacetValues().first();
    FacetValue hosted = facet.getFacetValues().last();
    Long cspId = csp.getId();
    Long cloudId = cloud.getId();

    /*
     * Link the two FacetValue's belonging to one Facet to the Csp
     */
    facetValueDao.linkCspToFacetValue(cspId, cloudId);
    facetValueDao.linkCspToFacetValue(cspId, hosted.getId());

    /*
     * Test the finding of the InUseFacetValue for one FacetValue
     */
    List<InUseFacetValue> inUseFacetValues = facetValueDao.findInUseFacetValues(cloudId);
    assertEquals(1, inUseFacetValues.size());
    InUseFacetValue inUseFacetValue = inUseFacetValues.get(0);
    assertEquals(cloud.getValue(), inUseFacetValue.getFacetValueValue());
    assertEquals(csp.getServiceProviderEntityId(), inUseFacetValue.getCompoundServiceProviderName());

    /*
     * Test the finding of the two InUseFacetValue for one Facet (containing two FacetValue's)
     */
    inUseFacetValues = facetValueDao.findInUseFacet(facet.getId());
    assertEquals(2, inUseFacetValues.size());

    inUseFacetValue = inUseFacetValues.get(0);
    assertEquals(cloud.getValue(), inUseFacetValue.getFacetValueValue());
    assertEquals(csp.getServiceProviderEntityId(), inUseFacetValue.getCompoundServiceProviderName());

    inUseFacetValue = inUseFacetValues.get(1);
    assertEquals(hosted.getValue(), inUseFacetValue.getFacetValueValue());
    assertEquals(csp.getServiceProviderEntityId(), inUseFacetValue.getCompoundServiceProviderName());

    /*
     * Test the unlinking of the Csp from the FacetValue
     */
    facetValueDao.unlinkCspFromFacetValue(cspId, cloudId);

    inUseFacetValues = facetValueDao.findInUseFacetValues(cloudId);
    assertEquals(0, inUseFacetValues.size());

    /*
     * But the Csp is still linked to the other FacetValue of the Facet
     */
    inUseFacetValues = facetValueDao.findInUseFacet(facet.getId());
    assertEquals(1, inUseFacetValues.size());

    /*
     * Now link the Csp again, so there are two links from the Csp to all
     * the FacetValue's, and test the unlinking (e.g. deletion) of
     * all Csp's (actually only one) from the FacetValue
     */
    facetValueDao.linkCspToFacetValue(cspId, cloudId);
    facetValueDao.unlinkAllCspFromFacetValue(cloudId);
    inUseFacetValues = facetValueDao.findInUseFacetValues(cloudId);
    assertEquals(0, inUseFacetValues.size());

    /*
     * Finally we unlink all FacetValue's belonging to a Facet from all Csp's. Effectively nothing
     * is linked anymore
     */
    facetValueDao.unlinkAllCspFromFacet(facet.getId());
    inUseFacetValues = facetValueDao.findInUseFacet(facet.getId());
    assertEquals(0, inUseFacetValues.size());

  }

  @Test
  public void testLocale() {
    Facet facet = createFacetWithValue();

    facet.addName(new Locale("nl"), "nederlandse_naam");
    facetDao.saveOrUpdate(facet);
    /*
     * Set up the Locale in the Request (as Spring does)
     */
    HttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(DispatcherServlet.LOCALE_RESOLVER_ATTRIBUTE, this);
    ServletRequestAttributes sra = new ServletRequestAttributes(request);
    RequestContextHolder.setRequestAttributes(sra);
    this.setLocale(null, null, new Locale("nl"));

    assertEquals(facet.getName(), "nederlandse_naam");

  }

  @Test
  public void testJson() throws IOException {
    Facet facet = createFacetWithValue();

    String json = mapper.writeValueAsString(facet);
    facet = mapper.readValue(json, Facet.class);
    LocalizedString value = facet.getFacetValues().first().getMultilingualString().getLocalizedStrings().get("en");
    assertEquals("cloud", value.getValue());
  }

  private Facet createFacetWithValue() {
    Facet facet = new Facet();
    facet.setName("category");

    FacetValue cloud = new FacetValue();
    cloud.setValue("cloud");
    facet.addFacetValue(cloud);

    FacetValue hosted = new FacetValue();
    hosted.setValue("hosted");
    facet.addFacetValue(hosted);

    facetDao.saveOrUpdate(facet);

    return facet;
  }

  private CompoundServiceProvider createCompoundServerProvider() {
    CompoundServiceProvider provider = CompoundServiceProvider.builder(new ServiceProvider("sp-id"), new Article());
    compoundServiceProviderDao.saveOrUpdate(provider);
    return provider;
  }


  @Override
  public Locale resolveLocale(HttpServletRequest request) {
    return currentLocale;
  }

  @Override
  public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
    this.currentLocale = locale;
  }
}
