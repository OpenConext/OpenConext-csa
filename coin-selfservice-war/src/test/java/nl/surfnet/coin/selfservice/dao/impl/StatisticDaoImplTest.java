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

package nl.surfnet.coin.selfservice.dao.impl;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.JdbcTemplate;

import nl.surfnet.coin.selfservice.domain.ChartSerie;
import nl.surfnet.coin.selfservice.domain.StatResult;

import static junit.framework.Assert.assertEquals;

/**
 * Test class for {@link StatisticDaoImpl}
 */
public class StatisticDaoImplTest {

  @InjectMocks
  private StatisticDaoImpl service = new StatisticDaoImpl();

  @Mock
  private JdbcTemplate ebJdbcTemplate;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(service);
  }

  @Test
  public void testConvertStatResultsToChartSeries() throws Exception {
    String sp1 = "sp1";
    String sp2 = "sp2";
    StatResult sp1day1 = new StatResult();
    sp1day1.setSpEntityId(sp1);
    sp1day1.setLogins(123);
    final Date sp1FirstDate = StatisticDaoImpl.convertFromMySqlString("2011-01-01");
    sp1day1.setDate(sp1FirstDate);
    StatResult sp1day3 = new StatResult();
    sp1day3.setSpEntityId(sp1);
    sp1day3.setLogins(654);
    sp1day3.setDate(StatisticDaoImpl.convertFromMySqlString("2011-01-03"));
    StatResult sp1day8 = new StatResult();
    sp1day8.setSpEntityId(sp1);
    sp1day8.setLogins(456);
    sp1day8.setDate(StatisticDaoImpl.convertFromMySqlString("2011-01-08"));

    StatResult sp2day1 = new StatResult();
    sp2day1.setSpEntityId(sp2);
    sp2day1.setLogins(324);
    final Date sp2FirstDate = StatisticDaoImpl.convertFromMySqlString("2011-02-27");
    sp2day1.setDate(sp2FirstDate);
    StatResult sp2day2 = new StatResult();
    sp2day2.setSpEntityId(sp2);
    sp2day2.setLogins(301);
    sp2day2.setDate(StatisticDaoImpl.convertFromMySqlString("2011-02-28"));

    final List<StatResult> statResults = Arrays.asList(sp1day1, sp1day3, sp1day8, sp2day1, sp2day2);
    final List<ChartSerie> chartSeries = service.convertStatResultsToChartSeries(statResults);
    assertEquals(2, chartSeries.size());

    final ChartSerie serie1 = chartSeries.get(0);
    assertEquals(sp1, serie1.getName());
    assertEquals(8, serie1.getData().size());
    assertEquals(sp1FirstDate, serie1.getPointStart());

    final ChartSerie serie2 = chartSeries.get(1);
    assertEquals(sp2, serie2.getName());
    assertEquals(2, serie2.getData().size());
    assertEquals(sp2FirstDate, serie2.getPointStart());
  }

  @Test
  public void testConvertFromMySqlString() throws Exception {
    String mySqlString = "2011-06-07";
    final Date date = StatisticDaoImpl.convertFromMySqlString(mySqlString);
    Calendar cal = Calendar.getInstance();
    cal.setTime(date);
    assertEquals(2011, cal.get(Calendar.YEAR));
    assertEquals(Calendar.JUNE, cal.get(Calendar.MONTH));
    assertEquals(7, cal.get(Calendar.DATE));
  }
}
