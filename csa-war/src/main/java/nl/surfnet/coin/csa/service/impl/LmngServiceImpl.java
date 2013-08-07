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

import nl.surfnet.coin.csa.dao.LmngIdentifierDao;
import nl.surfnet.coin.csa.domain.Account;
import nl.surfnet.coin.csa.domain.Article;
import nl.surfnet.coin.csa.domain.IdentityProvider;
import nl.surfnet.coin.csa.model.License;
import nl.surfnet.coin.csa.service.CrmService;
import nl.surfnet.coin.shared.domain.ErrorMail;
import nl.surfnet.coin.shared.service.ErrorMessageMailer;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.params.CoreProtocolPNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.*;

/**
 * Implementation of a licensing service that get's it information from a
 * webservice interface on LMNG
 */
public class LmngServiceImpl implements CrmService {

  private static final Logger log = LoggerFactory.getLogger(LmngServiceImpl.class);

  private static final String PATH_FETCH_QUERY_GET_INSTITUTION = "lmngqueries/lmngQueryGetInstitution.xml";

  @Autowired
  private LmngIdentifierDao lmngIdentifierDao;

  private CrmUtil lmngUtil = new LmngUtil();

  @Resource(name = "errorMessageMailer")
  private ErrorMessageMailer errorMessageMailer;

  private boolean debug;
  private String endpoint;

  private HttpClient httpclient;

  public LmngServiceImpl() {
        httpclient = new DefaultHttpClient(new PoolingClientConnectionManager());
        httpclient.getParams().setParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, Boolean.FALSE);
        httpclient.getParams().setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
        httpclient.getParams().setParameter(CoreProtocolPNames.HTTP_CONTENT_CHARSET, "UTF-8");
  }

  @Cacheable(value = "crm")
  @Override
  public List<License> getLicensesForIdpAndSp(IdentityProvider identityProvider, String articleIdentifier)
          throws LmngException {
    return getLicensesForIdpAndSps(identityProvider, Arrays.asList(articleIdentifier));
  }

  @Cacheable(value = "crm")
  @Override
  public List<License> getLicensesForIdpAndSps(IdentityProvider identityProvider, List<String> articleIdentifiers)
          throws LmngException {
    List<License> result = new ArrayList<License>();
    if (CollectionUtils.isEmpty(articleIdentifiers)) {
      return result;
    }
    try {
      String lmngInstitutionId = getLmngIdentityId(identityProvider);

      if (lmngInstitutionId == null || lmngInstitutionId.trim().length() == 0) {
        return result;
      }

      // get the file with the soap request
      String soapRequest = lmngUtil.getLmngSoapRequestForIdpAndSp(lmngInstitutionId, articleIdentifiers, new Date(), endpoint);
      if (debug) {
        lmngUtil.writeIO("lmngRequest", StringEscapeUtils.unescapeHtml(soapRequest));
      }

      // call the webservice
      String webserviceResult = getWebServiceResult(soapRequest);
      // read/parse the XML response to License objects
      result = lmngUtil.parseLicensesResult(webserviceResult, debug);
    } catch (Exception e) {
      String exceptionMessage = "Exception while retrieving licenses" + e.getMessage();
      log.error(exceptionMessage, e);
      sendErrorMail(identityProvider, "articleIdentifier", e.getMessage(), "getLicensesForIdpAndSp");
      throw new LmngException(exceptionMessage, e);
    }
    return result;
  }

  @Cacheable(value = "crm")
  @Override
  public List<Article> getArticlesForServiceProviders(List<String> serviceProvidersEntityIds) throws LmngException {
    List<Article> result = new ArrayList<Article>();
    try {
      Map<String, String> serviceIds = getLmngServiceIds(serviceProvidersEntityIds);

      // validation, we need at least one serviceId
      if (CollectionUtils.isEmpty(serviceIds)) {
        return result;
      }

      // get the file with the soap request
      String soapRequest = lmngUtil.getLmngSoapRequestForSps(serviceIds.keySet(), endpoint);
      if (debug) {
        lmngUtil.writeIO("lmngRequest", StringEscapeUtils.unescapeHtml(soapRequest));
      }

      // call the webservice
      String webserviceResult = getWebServiceResult(soapRequest);
      // read/parse the XML response to License objects
      List<Article> parsedArticles = lmngUtil.parseArticlesResult(webserviceResult, debug);

      for (Article article : parsedArticles) {
        article.setServiceProviderEntityId(serviceIds.get(article.getLmngIdentifier()));
        result.add(article);
      }
    } catch (Exception e) {
      String exceptionMessage = "Exception while retrieving articles:" + e.getMessage();
      log.error(exceptionMessage, e);
      sendErrorMail(serviceProvidersEntityIds, e.getMessage(), "getArticlesForServiceProviders");
      throw new LmngException(exceptionMessage, e);
    }
    return result;
  }

  @Cacheable(value = "crm")
  @Override
  public String getServiceName(String guid) {
    Article article = getService(guid);
    return article == null ? null : article.getArticleName();
  }

  @Cacheable(value = "crm")
  @Override
  public Article getService(final String guid) {
    Article result = null;
    try {
      // get the file with the soap request
      String soapRequest = lmngUtil.getLmngSoapRequestForSps(Arrays.asList(new String[]{guid}), endpoint);
      if (debug) {
        lmngUtil.writeIO("lmngRequest", StringEscapeUtils.unescapeHtml(soapRequest));
      }

      // call the webservice
      String webserviceResult = getWebServiceResult(soapRequest);
      // read/parse the XML response to License objects
      List<Article> resultList = lmngUtil.parseArticlesResult(webserviceResult, debug);
      if (resultList != null && resultList.size() > 0) {
        result = resultList.get(0);
      }
    } catch (Exception e) {
      log.error("Exception while retrieving article/license", e);
      sendErrorMail(guid, e.getMessage(), "getServiceName");
    }
    return result;
  }

  @Override
  public List<Account> getAccounts(boolean isInstitution) {
    List<Account> accounts = new ArrayList<Account>();
    try {
      // get the file with the soap request
      String soapRequest = lmngUtil.getLmngSoapRequestForAllAccount(isInstitution, endpoint);
      if (debug) {
        lmngUtil.writeIO("lmngRequest", StringEscapeUtils.unescapeHtml(soapRequest));
      }
      // call the webservice
      String webserviceResult = getWebServiceResult(soapRequest);
      // read/parse the XML response to Account objects
      accounts = lmngUtil.parseAccountsResult(webserviceResult, debug);
    } catch (Exception e) {
      log.error("Exception while retrieving article/license", e);
      sendErrorMail("n/a", e.getMessage(), "getAccounts");
    }
    return accounts;
  }

  @Override
  @Cacheable(value = "crm")
  public String getInstitutionName(String guid) {
    String result = null;
    try {
      ClassPathResource queryResource = new ClassPathResource(PATH_FETCH_QUERY_GET_INSTITUTION);

      // Get the soap/fetch envelope
      String soapRequest = lmngUtil.getLmngRequestEnvelope();

      InputStream inputStream;
      inputStream = queryResource.getInputStream();
      String query = IOUtils.toString(inputStream);
      query = query.replaceAll(LmngUtil.INSTITUTION_IDENTIFIER_PLACEHOLDER, guid);

      // html encode the string
      query = StringEscapeUtils.escapeHtml(query);

      // Insert the query in the envelope and add a UID in the envelope
      soapRequest = soapRequest.replaceAll(LmngUtil.QUERY_PLACEHOLDER, query);
      soapRequest = soapRequest.replaceAll(LmngUtil.ENDPOINT_PLACEHOLDER, endpoint);
      soapRequest = soapRequest.replaceAll(LmngUtil.UID_PLACEHOLDER, UUID.randomUUID().toString());

      if (debug) {
        lmngUtil.writeIO("lmngRequestInstitution", StringEscapeUtils.unescapeHtml(soapRequest));
      }

      String webserviceResult = getWebServiceResult(soapRequest);
      result = lmngUtil.parseResultInstitute(webserviceResult, debug);
    } catch (Exception e) {
      log.error("Exception while retrieving article/license", e);
      sendErrorMail(guid, e.getMessage(), "getInstitutionName");
    }
    return result;
  }

  /**
   * Get the response from the webservice call (using credentials and endpoint
   * address from this class settings) after execututing the given soapRequest
   * string.
   *
   * @param soapRequest A string representation of the soap request
   * @return an inputstream of the webservice response
   * @throws IOException
   * @throws KeyStoreException
   * @throws NoSuchAlgorithmException
   * @throws UnrecoverableKeyException
   * @throws KeyManagementException
   */
  protected String getWebServiceResult(final String soapRequest) throws IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {
    log.debug("Calling the LMNG proxy webservice, endpoint: {}", endpoint);

    HttpPost httppost = new HttpPost(endpoint);
    httppost.setHeader("Content-Type", "application/soap+xml");
    httppost.setEntity(new StringEntity(soapRequest));

    long beforeCall = System.currentTimeMillis();
    HttpResponse httpResponse = httpclient.execute(httppost);
    long afterCall = System.currentTimeMillis();
    log.info("LMNG proxy webservice called in {} ms. Http response: {}", afterCall - beforeCall, httpResponse);

    HttpEntity httpresponseEntity = httpResponse.getEntity();

    // Continue only if we have a successful response (code 200)
    int status = httpResponse.getStatusLine().getStatusCode();
    // Get String representation of response
    String stringResponse = IOUtils.toString(httpresponseEntity.getContent());

    if (debug) {
      lmngUtil.writeIO("lmngWsResponseStatus" + status, StringEscapeUtils.unescapeHtml(stringResponse));
    }


    if (status != 200) {
      log.debug("LMNG webservice response content is:\n{}", stringResponse);
      throw new RuntimeException("Invalid response from LMNG webservice. Http response " + httpResponse);
    }

    // Close the entity's InputStream, as prescribed.
    httpresponseEntity.getContent().close();

    return stringResponse;
  }

  /**
   * Get the LMNG identifier for the given IDP
   *
   */
  private String getLmngIdentityId(IdentityProvider identityProvider) {
    // currently institutionId can be null, so check first
    if (identityProvider != null && identityProvider.getInstitutionId() != null) {
      return lmngIdentifierDao.getLmngIdForIdentityProviderId(identityProvider.getInstitutionId());
    }
    return null;
  }

  /**
   * Get the LMNG identifier for the given SP
   *
   * @param serviceProviderEntityId
   */
  private String getLmngServiceId(String serviceProviderEntityId) {
    if (serviceProviderEntityId != null) {
      return lmngIdentifierDao.getLmngIdForServiceProviderId(serviceProviderEntityId);
    }
    return null;
  }

  /**
   * Get the LMNG identifiers for the given SP list
   *
   * @return a map with the LMNGID as key and serviceprovider entity ID as value
   */
  private Map<String, String> getLmngServiceIds(List<String> serviceProvidersEntityIds) {
    Map<String, String> result = new HashMap<String, String>();
    for (String spId : serviceProvidersEntityIds) {
      String serviceId = getLmngServiceId(spId);
      if (serviceId != null) {
        result.put(serviceId, spId);
      }
    }
    return result;
  }

  /*
   * Send a mail
   */
  private void sendErrorMail(IdentityProvider idp, String articleIdentifier, String message, String method) {
    String shortMessage = "Exception while retrieving article/license";

    String idpEntityId = idp == null ? "NULL" : idp.getId();
    String institutionId = idp == null ? "NULL" : idp.getInstitutionId();

    String formattedMessage = String.format(
            "LMNG call for Identity Provider '%s' with institution ID '%s' and Article with Id's '%s' failed with the following message: '%s'",
            idpEntityId, institutionId, articleIdentifier, message);
    ErrorMail errorMail = new ErrorMail(shortMessage, formattedMessage, formattedMessage, getHost(), "LMNG");
    errorMail.setLocation(this.getClass().getName() + "#get" + method);
    errorMessageMailer.sendErrorMail(errorMail);
  }

  /*
   * Send a mail
   */
  private void sendErrorMail(List<String> spIds, String message, String method) {
    String shortMessage = "Exception while retrieving article/license";
    String spEntityIds = "";
    if (spIds != null) {
      for (String spId : spIds) {
        spEntityIds += spId + ", ";
      }
    }

    String formattedMessage = String.format("LMNG call for Service Providers with id's '%s' failed with the following message: '%s'",
            spEntityIds, message);
    ErrorMail errorMail = new ErrorMail(shortMessage, formattedMessage, formattedMessage, getHost(), "LMNG");
    errorMail.setLocation(this.getClass().getName() + "#get" + method);
    errorMessageMailer.sendErrorMail(errorMail);
  }

  /*
   * Send a mail
   */
  private void sendErrorMail(String guid, String message, String method) {
    String shortMessage = "Exception while retrieving institute from LMNG";
    String formattedMessage = String.format("LMNG call for institute GUID '%s' failed with the following message: '%s'", guid, message);
    ErrorMail errorMail = new ErrorMail(shortMessage, formattedMessage, formattedMessage, getHost(), "LMNG");
    errorMail.setLocation(this.getClass().getName() + "#get" + method);
    errorMessageMailer.sendErrorMail(errorMail);
  }

  private String getHost() {
    try {
      return InetAddress.getLocalHost().toString();
    } catch (UnknownHostException e) {
      return "UNKNOWN";
    }
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  public void setDebug(boolean debug) {
    this.debug = debug;
  }

  public void setLmngIdentifierDao(LmngIdentifierDao lmngIdentifierDao) {
    this.lmngIdentifierDao = lmngIdentifierDao;
  }

  @Override
  public String performQuery(String rawQuery) {
    try {
      String soapRequest = lmngUtil.getLmngRequestEnvelope();
      String query = StringEscapeUtils.escapeHtml(rawQuery);
      soapRequest = soapRequest.replaceAll(LmngUtil.QUERY_PLACEHOLDER, query);
      soapRequest = soapRequest.replaceAll(LmngUtil.ENDPOINT_PLACEHOLDER, endpoint);
      soapRequest = soapRequest.replaceAll(LmngUtil.UID_PLACEHOLDER, UUID.randomUUID().toString());

      return getWebServiceResult(soapRequest);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  @CacheEvict(value = "crm", allEntries = true)
  public void evictCache() {
  }

  public void setLmngUtil(CrmUtil lmngUtil) {
    this.lmngUtil = lmngUtil;
  }
}
