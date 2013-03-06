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

package nl.surfnet.sab;

import java.util.GregorianCalendar;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class SabClientTest {

  @Test
  public void testHasRoleForOrganisation() {
    String userId = "foo";
    String organisation = "SURFNET";
    String role = "Infraverantwoordelijke";

    SabClient sabClient = new SabClient();
    sabClient.setTransport(new LocalFileTransport("/response.xml"));

    assertTrue(sabClient.hasRoleForOrganisation(userId, role, organisation));
  }

  @Test
  public void createRequest() {
    String request = new SabClient().createRequest("userid", "234567890");
//    System.out.println(request);
    assertTrue(request.contains("<saml:NameID Format=\"urn:oasis:names:tc:SAML:2.0:nameid-format:unspecified\">userid</saml:NameID>"));
    assertTrue(request.contains("ID=\"234567890\""));
    assertTrue(request.contains("IssueInstant=\"" + new GregorianCalendar().get(GregorianCalendar.YEAR)));
  }
}
