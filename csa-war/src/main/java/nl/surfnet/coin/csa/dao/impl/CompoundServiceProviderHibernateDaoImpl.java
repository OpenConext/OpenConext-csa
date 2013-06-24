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

import nl.surfnet.coin.csa.dao.CompoundServiceProviderDao;
import nl.surfnet.coin.csa.domain.CompoundServiceProvider;
import nl.surfnet.coin.shared.service.GenericServiceHibernateImpl;
import org.hibernate.NonUniqueResultException;
import org.hibernate.criterion.Restrictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * CompoundServiceProviderHibernateDaoImpl.java
 */
@Repository
public class CompoundServiceProviderHibernateDaoImpl extends GenericServiceHibernateImpl<CompoundServiceProvider> implements
        CompoundServiceProviderDao {

  private static final Logger LOG = LoggerFactory.getLogger(CompoundServiceProviderHibernateDaoImpl.class);

  public CompoundServiceProviderHibernateDaoImpl() {
    super(CompoundServiceProvider.class);
  }

  @Override
  public CompoundServiceProvider findByEntityId(String entityId) {
    List<CompoundServiceProvider> serviceProviderList = findByCriteria(Restrictions.eq("serviceProviderEntityId", entityId));
    if (serviceProviderList.size() > 1) {
      LOG.error("More then one ('" + serviceProviderList.size() + "') CompoundServiceProvider found with entityId('" + entityId + "')");
      throw new NonUniqueResultException(serviceProviderList.size());
    } else if (serviceProviderList.size() == 0) {
      return null;
    }
    return serviceProviderList.get(0);
  }

  @Override
  public List<CompoundServiceProvider> findAll() {
    return super.findAll();
  }

}
