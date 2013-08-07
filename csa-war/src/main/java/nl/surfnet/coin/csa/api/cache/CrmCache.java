package nl.surfnet.coin.csa.api.cache;

import nl.surfnet.coin.csa.dao.LmngIdentifierDao;
import nl.surfnet.coin.csa.domain.Article;
import nl.surfnet.coin.csa.domain.IdentityProvider;
import nl.surfnet.coin.csa.domain.MappingEntry;
import nl.surfnet.coin.csa.model.License;
import nl.surfnet.coin.csa.model.Service;
import nl.surfnet.coin.csa.service.CrmService;
import org.apache.commons.lang.SerializationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CrmCache extends AbstractCache {

  private static final Logger LOG = LoggerFactory.getLogger(CrmCache.class);

  @Resource
  private CrmService crmService;

  @Resource
  private LmngIdentifierDao lmngIdentifierDao;

  /**
   * Cache of Licenses, keyed by Entry of Idp institutionId and spEntityId
   */
  private ConcurrentHashMap<MappingEntry, License> licenseCache = new ConcurrentHashMap<MappingEntry, License>();

  /**
   * Cache of Articles, keyed by spEntityId
   */
  private ConcurrentHashMap<String, Article> articleCache = new ConcurrentHashMap<String, Article>();

  private List<MappingEntry> idpToLmngId;
  private List<MappingEntry> spToLmngId;

  private final Object lock = new Object();


  @Override
  protected void doPopulateCache() {
    synchronized (lock) {
      populateMappings();
      populateLicenseCache();
      populateArticleCache();
    }
  }

  private void populateMappings() {
    idpToLmngId = lmngIdentifierDao.findAllIdentityProviders();
    spToLmngId = lmngIdentifierDao.findAllServiceProviders();

  }

  private void populateArticleCache() {

    // Here we only have to query the articles that have been mapped to an SP, luckily not the whole CRM database.
    for (MappingEntry spAndLmngId : spToLmngId) {
      String spEntityId = spAndLmngId.getKey();
      String lmngId = spAndLmngId.getValue();
      List<Article> articlesForServiceProviders = crmService.getArticlesForServiceProviders(Arrays.asList(spEntityId));

      if (articlesForServiceProviders.size() > 1) {
        LOG.info("Unexpected: list of articles for SP ({}) is larger than 1: {}", spEntityId, articlesForServiceProviders.size());
      }

      if (articlesForServiceProviders.size() >= 1) {
        articleCache.put(spEntityId, articlesForServiceProviders.get(0));
      } else {
        LOG.info("No article found for SP {}, with lmng id: {}", spEntityId, lmngId);
        articleCache.put(spEntityId, null);
      }
    }
  }

  private void populateLicenseCache() {

    // Nested loop to query the cartesian product of all SPs and all IdPs
    for (MappingEntry idpLmngEntry : idpToLmngId) {
      String idpInstitutionId = idpLmngEntry.getKey();
      for (MappingEntry spLmngEntry : spToLmngId) {
        String spEntityId = spLmngEntry.getKey();
        String spLmngId = spLmngEntry.getValue();

        IdentityProvider idp = new IdentityProvider("dummy", idpInstitutionId, "dummy");

        List<License> licensesForIdpAndSp = crmService.getLicensesForIdpAndSp(idp, spLmngId);
        if (licensesForIdpAndSp.size() > 1) {
          LOG.warn("Unexpected: list of licenses by IdP and SP ({} and {}) is larger than 1: {}", idpInstitutionId, spEntityId, licensesForIdpAndSp.size());
        }
        if (licensesForIdpAndSp.size() == 1) {
          LOG.debug("License found by IdP and SP ({} and {}): {}", idpInstitutionId, spEntityId, licensesForIdpAndSp.get(0));
          licenseCache.put(new MappingEntry(idpInstitutionId, spEntityId), licensesForIdpAndSp.get(0));
        } else {
          LOG.debug("No result found for licenses by IdP and SP ({} and {})", idpInstitutionId, spEntityId);
        }
      }
    }
  }

  public License getLicense(Service service, String idpInstitutionId) {
    MappingEntry entry = new MappingEntry(idpInstitutionId, service.getSpEntityId());
    License license = licenseCache.get(entry);
    LOG.debug("Looked for license for service {} and idpInstitutionId {}, and found: {}", service.getSpEntityId(), idpInstitutionId, license);
    return license;
  }

  public Article getArticle(Service service) {

    if (service.getSpEntityId() == null) {
      // This happens for 'crm only' services, with no reference to Service Registry's services.
      return null;
    }
    return (Article) SerializationUtils.clone(articleCache.get(service.getSpEntityId()));
  }

  @Override
  protected String getCacheName() {
    return "CRM Cache";
  }

  public void setCrmService(CrmService crmService) {
    this.crmService = crmService;
  }

  public void setLmngIdentifierDao(LmngIdentifierDao lmngIdentifierDao) {
    this.lmngIdentifierDao = lmngIdentifierDao;
  }
}