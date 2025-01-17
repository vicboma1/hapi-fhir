package ca.uhn.fhir.jpa.search.autocomplete;

/*-
 * #%L
 * HAPI FHIR JPA Server
 * %%
 * Copyright (C) 2014 - 2022 Smile CDR, Inc.
 * %%
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
 * #L%
 */

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.jpa.dao.search.ExtendedLuceneClauseBuilder;
import ca.uhn.fhir.jpa.model.entity.ResourceTable;
import ca.uhn.fhir.model.api.IQueryParameterType;
import ca.uhn.fhir.rest.param.StringParam;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.search.backend.elasticsearch.ElasticsearchExtension;
import org.hibernate.search.engine.search.aggregation.AggregationKey;
import org.hibernate.search.engine.search.aggregation.SearchAggregation;
import org.hibernate.search.engine.search.predicate.dsl.BooleanPredicateClausesStep;
import org.hibernate.search.engine.search.predicate.dsl.SearchPredicateFactory;
import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.engine.search.query.dsl.SearchQueryOptionsStep;
import org.hibernate.search.mapper.orm.search.loading.dsl.SearchLoadingOptionsStep;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Use aggregations to implement a search of most-frequent token search params values.
 */
class TokenAutocompleteSearch {
	private static final Logger ourLog = LoggerFactory.getLogger(TokenAutocompleteSearch.class);
	private static final AggregationKey<JsonObject> AGGREGATION_KEY = AggregationKey.of("autocomplete");

	private final FhirContext myFhirContext;
	private final SearchSession mySession;

	public TokenAutocompleteSearch(FhirContext theFhirContext, SearchSession theSession) {
		myFhirContext = theFhirContext;
		mySession = theSession;
	}


	/**
	 * Search for tokens indexed by theSPName on theResourceType matching  theSearchText.
	 * @param theResourceType The resource type (e.g. Observation)
	 * @param theSPName The search param code (e.g. combo-code)
	 * @param theSearchText The search test (e.g. "bloo")
	 * @return A collection of Coding elements
	 */
	@Nonnull
	public List<TokenAutocompleteHit> search(String theResourceType, String theSPName, String theSearchText, String theSearchModifier, int theCount) {

		ourLog.trace("search: {}?{}:{}={}", theResourceType,theSPName, theSearchModifier, theSearchText);

		TokenAutocompleteAggregation tokenAutocompleteAggregation = new TokenAutocompleteAggregation(theSPName, theCount);

		// compose the query json
		SearchQueryOptionsStep<?, ?, SearchLoadingOptionsStep, ?, ?> query = mySession.search(ResourceTable.class)
			.where(f -> f.bool(b ->
				buildQueryPredicate(b, f, theResourceType, theSPName, theSearchModifier, theSearchText)))
			.aggregation(AGGREGATION_KEY, buildAggregation(tokenAutocompleteAggregation));

		// run the query, but with 0 results.  We only care about the aggregations.
		SearchResult<?> result = query.fetch(0);

		// extract the top-n results from the aggregation json.
		JsonObject resultAgg = result.aggregation(AGGREGATION_KEY);
		List<TokenAutocompleteHit> aggEntries = tokenAutocompleteAggregation.extractResults(resultAgg);

		return aggEntries;
	}

	void buildQueryPredicate(BooleanPredicateClausesStep<?> b, SearchPredicateFactory f, String theResourceType, String theSPName, String theSearchModifier, String theSearchText) {
		ExtendedLuceneClauseBuilder clauseBuilder = new ExtendedLuceneClauseBuilder(myFhirContext, b, f);

		if (isNotBlank(theResourceType)) {
			clauseBuilder.addResourceTypeClause(theResourceType);
		}

		String queryText = StringUtils.defaultString(theSearchText, "");
		if (StringUtils.isNotEmpty(queryText)) {
			switch (StringUtils.defaultString(theSearchModifier)) {
				case "text":
					// Add a wildcard to act like match_bool_prefix
					//  https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-match-bool-prefix-query.html
					queryText = queryText + "*";
					StringParam stringParam = new StringParam(queryText);
					List<List<IQueryParameterType>> andOrTerms = Collections.singletonList(Collections.singletonList(stringParam));
					clauseBuilder.addStringTextSearch(theSPName, andOrTerms);
					break;
				case "":
					b.must(
						// use wildcard to allow matching prefix of keyword indexed field.
						f.wildcard()
							.field(ExtendedLuceneClauseBuilder.getTokenCodeFieldPath(theSPName))
							.matching(queryText + "*")
							.toPredicate());
			break;
			default:
				throw new IllegalArgumentException(Msg.code(2034) + "Autocomplete only accepts text search for now.");
			}

		}
	}

	/**
	 * Hibernate-search doesn't support nested aggregations, so we use an extension to build what we need from raw JSON.
	 */
	SearchAggregation<JsonObject> buildAggregation(TokenAutocompleteAggregation tokenAutocompleteAggregation) {
		JsonObject jsonAggregation = tokenAutocompleteAggregation.toJsonAggregation();

		SearchAggregation<JsonObject> aggregation = mySession
			.scope( ResourceTable.class )
			.aggregation()
			.extension(ElasticsearchExtension.get())
			.fromJson(jsonAggregation)
			.toAggregation();

		return aggregation;
	}
}
