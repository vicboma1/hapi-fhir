package ca.uhn.fhir.jpa.provider.r4;

import ca.uhn.fhir.util.TestUtil;
import ca.uhn.fhir.util.UrlUtil;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static ca.uhn.fhir.jpa.provider.GraphQLR4ProviderTest.DATA_PREFIX;
import static ca.uhn.fhir.jpa.provider.GraphQLR4ProviderTest.DATA_SUFFIX;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class GraphQLR4Test extends BaseResourceProviderR4Test {
	public static final String INTROSPECTION_QUERY = "{\"query\":\"\\n    query IntrospectionQuery {\\n      __schema {\\n        queryType { name }\\n        mutationType { name }\\n        subscriptionType { name }\\n        types {\\n          ...FullType\\n        }\\n        directives {\\n          name\\n          description\\n          locations\\n          args {\\n            ...InputValue\\n          }\\n        }\\n      }\\n    }\\n\\n    fragment FullType on __Type {\\n      kind\\n      name\\n      description\\n      fields(includeDeprecated: true) {\\n        name\\n        description\\n        args {\\n          ...InputValue\\n        }\\n        type {\\n          ...TypeRef\\n        }\\n        isDeprecated\\n        deprecationReason\\n      }\\n      inputFields {\\n        ...InputValue\\n      }\\n      interfaces {\\n        ...TypeRef\\n      }\\n      enumValues(includeDeprecated: true) {\\n        name\\n        description\\n        isDeprecated\\n        deprecationReason\\n      }\\n      possibleTypes {\\n        ...TypeRef\\n      }\\n    }\\n\\n    fragment InputValue on __InputValue {\\n      name\\n      description\\n      type { ...TypeRef }\\n      defaultValue\\n    }\\n\\n    fragment TypeRef on __Type {\\n      kind\\n      name\\n      ofType {\\n        kind\\n        name\\n        ofType {\\n          kind\\n          name\\n          ofType {\\n            kind\\n            name\\n            ofType {\\n              kind\\n              name\\n              ofType {\\n                kind\\n                name\\n                ofType {\\n                  kind\\n                  name\\n                  ofType {\\n                    kind\\n                    name\\n                  }\\n                }\\n              }\\n            }\\n          }\\n        }\\n      }\\n    }\\n  \",\"operationName\":\"IntrospectionQuery\"}";
	private Logger ourLog = LoggerFactory.getLogger(GraphQLR4Test.class);
	private IIdType myPatientId0;

	@Test
	public void testIntrospectRead_Patient() throws IOException {
		initTestPatients();

		String uri = ourServerBase + "/Patient/1/$graphql";
		HttpPost httpGet = new HttpPost(uri);
		httpGet.setEntity(new StringEntity(INTROSPECTION_QUERY, ContentType.APPLICATION_JSON));

		// Repeat a couple of times to make sure it doesn't fail after the first one. At one point
		// the generator polluted the structure userdata and failed the second time
		for (int i = 0; i < 3; i++) {
			try (CloseableHttpResponse response = ourHttpClient.execute(httpGet)) {
				String resp = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
				ourLog.info(resp);
				assertEquals(200, response.getStatusLine().getStatusCode());
				assertThat(resp, containsString("{\"kind\":\"OBJECT\",\"name\":\"Query\",\"fields\":[{\"name\":\"Patient\""));
				assertThat(resp, not(containsString("\"PatientList\"")));
			}
		}
	}

	@Test
	public void testIntrospectSearch_Patient() throws IOException {
		initTestPatients();

		String uri = ourServerBase + "/Patient/$graphql";
		HttpPost httpGet = new HttpPost(uri);
		httpGet.setEntity(new StringEntity(INTROSPECTION_QUERY, ContentType.APPLICATION_JSON));

		// Repeat a couple of times to make sure it doesn't fail after the first one. At one point
		// the generator polluted the structure userdata and failed the second time
		for (int i = 0; i < 3; i++) {
			try (CloseableHttpResponse response = ourHttpClient.execute(httpGet)) {
				String resp = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
				ourLog.info(resp);
				assertEquals(200, response.getStatusLine().getStatusCode());
				assertThat(resp, containsString("{\"kind\":\"OBJECT\",\"name\":\"Patient\","));
				assertThat(resp, not(containsString("{\"kind\":\"OBJECT\",\"name\":\"Observation\",")));
				assertThat(resp, not(containsString("{\"kind\":\"OBJECT\",\"name\":\"Query\",\"fields\":[{\"name\":\"Patient\"")));
				assertThat(resp, containsString("{\"kind\":\"OBJECT\",\"name\":\"Query\",\"fields\":[{\"name\":\"PatientList\""));
				assertThat(resp, not(containsString("{\"kind\":\"OBJECT\",\"name\":\"Query\",\"fields\":[{\"name\":\"ObservationList\"")));
			}
		}
	}

	@Test
	public void testIntrospectSearch_Observation() throws IOException {
		initTestPatients();

		String uri = ourServerBase + "/Observation/$graphql";
		HttpPost httpGet = new HttpPost(uri);
		httpGet.setEntity(new StringEntity(INTROSPECTION_QUERY, ContentType.APPLICATION_JSON));

		// Repeat a couple of times to make sure it doesn't fail after the first one. At one point
		// the generator polluted the structure userdata and failed the second time
		for (int i = 0; i < 3; i++) {
			try (CloseableHttpResponse response = ourHttpClient.execute(httpGet)) {
				String resp = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
				ourLog.info(resp);
				assertEquals(200, response.getStatusLine().getStatusCode());
				assertThat(resp, not(containsString("{\"kind\":\"OBJECT\",\"name\":\"Patient\",")));
				assertThat(resp, containsString("{\"kind\":\"OBJECT\",\"name\":\"Observation\","));
				assertThat(resp, not(containsString("{\"kind\":\"OBJECT\",\"name\":\"Query\",\"fields\":[{\"name\":\"PatientList\"")));
				assertThat(resp, containsString("{\"kind\":\"OBJECT\",\"name\":\"Query\",\"fields\":[{\"name\":\"ObservationList\""));
			}
		}
	}

	@Test
	public void testInstanceSimpleRead() throws IOException {
		initTestPatients();

		String query = "{name{family,given}}";
		HttpGet httpGet = new HttpGet(ourServerBase + "/Patient/" + myPatientId0.getIdPart() + "/$graphql?query=" + UrlUtil.escapeUrlParam(query));

		try (CloseableHttpResponse response = ourHttpClient.execute(httpGet)) {
			String resp = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
			ourLog.info(resp);
			assertEquals(TestUtil.stripWhitespace(DATA_PREFIX + "{\n" +
				"  \"name\":[{\n" +
				"    \"family\":\"FAM\",\n" +
				"    \"given\":[\"GIVEN1\",\"GIVEN2\"]\n" +
				"  },{\n" +
				"    \"given\":[\"GivenOnly1\",\"GivenOnly2\"]\n" +
				"  }]\n" +
				"}" + DATA_SUFFIX), TestUtil.stripWhitespace(resp));
		}

	}

	@Test
	public void testSearch_Patient() throws IOException {
		initTestPatients();

		String query = "{PatientList(given:\"given\"){name{family,given}}}";
		HttpGet httpGet = new HttpGet(ourServerBase + "/$graphql?query=" + UrlUtil.escapeUrlParam(query));

		try (CloseableHttpResponse response = ourHttpClient.execute(httpGet)) {
			String resp = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
			ourLog.info(resp);
			assertEquals(TestUtil.stripWhitespace(DATA_PREFIX + "{\n" +
				"  \"PatientList\":[{\n" +
				"    \"name\":[{\n" +
				"      \"family\":\"FAM\",\n" +
				"      \"given\":[\"GIVEN1\",\"GIVEN2\"]\n" +
				"    },{\n" +
				"      \"given\":[\"GivenOnly1\",\"GivenOnly2\"]\n" +
				"    }]\n" +
				"  },{\n" +
				"    \"name\":[{\n" +
				"      \"given\":[\"GivenOnlyB1\",\"GivenOnlyB2\"]\n" +
				"    }]\n" +
				"  }]\n" +
				"}" + DATA_SUFFIX), TestUtil.stripWhitespace(resp));
		}

	}
	@Test
	public void testSearch_Observation() throws IOException {
		initTestPatients();

		String query = "{ObservationList(date: \"2022\") {id}}";
		HttpGet httpGet = new HttpGet(ourServerBase + "/$graphql?query=" + UrlUtil.escapeUrlParam(query));

		myCaptureQueriesListener.clear();
		try (CloseableHttpResponse response = ourHttpClient.execute(httpGet)) {
			String resp = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
			ourLog.info(resp);
		}
		myCaptureQueriesListener.logSelectQueries();
	}

	private void initTestPatients() {
		Patient p = new Patient();
		p.addName()
			.setFamily("FAM")
			.addGiven("GIVEN1")
			.addGiven("GIVEN2");
		p.addName()
			.addGiven("GivenOnly1")
			.addGiven("GivenOnly2");
		myPatientId0 = myClient.create().resource(p).execute().getId().toUnqualifiedVersionless();

		p = new Patient();
		p.addName()
			.addGiven("GivenOnlyB1")
			.addGiven("GivenOnlyB2");
		myClient.create().resource(p).execute();
	}


}