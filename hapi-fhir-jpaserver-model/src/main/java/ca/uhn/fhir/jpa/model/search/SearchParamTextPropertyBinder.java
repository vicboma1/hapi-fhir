package ca.uhn.fhir.jpa.model.search;

/*-
 * #%L
 * HAPI FHIR JPA Server
 * %%
 * Copyright (C) 2014 - 2021 Smile CDR, Inc.
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

import org.hibernate.search.engine.backend.document.DocumentElement;
import org.hibernate.search.engine.backend.document.IndexObjectFieldReference;
import org.hibernate.search.engine.backend.document.model.dsl.IndexSchemaElement;
import org.hibernate.search.engine.backend.document.model.dsl.IndexSchemaObjectField;
import org.hibernate.search.engine.backend.types.ObjectStructure;
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.dsl.IndexFieldTypeFactory;
import org.hibernate.search.engine.backend.types.dsl.StringIndexFieldTypeOptionsStep;
import org.hibernate.search.mapper.pojo.bridge.PropertyBridge;
import org.hibernate.search.mapper.pojo.bridge.binding.PropertyBindingContext;
import org.hibernate.search.mapper.pojo.bridge.mapping.programmatic.PropertyBinder;
import org.hibernate.search.mapper.pojo.bridge.runtime.PropertyBridgeWriteContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Allows hibernate search to index
 *
 * CodeableConcept.text
 * Coding.display
 * Identifier.type.text
 *
 */
public class SearchParamTextPropertyBinder implements PropertyBinder, PropertyBridge<ExtendedLuceneIndexData> {

	public static final String SEARCH_PARAM_TEXT_PREFIX = "text-";
	private static final Logger ourLog = LoggerFactory.getLogger(SearchParamTextPropertyBinder.class);

	@Override
	public void bind(PropertyBindingContext thePropertyBindingContext) {
		// TODO Is it safe to use object identity of the Map to track dirty?
		thePropertyBindingContext.dependencies().use("mySearchParamTexts");

		defineIndexingTemplate(thePropertyBindingContext);

		thePropertyBindingContext.bridge(ExtendedLuceneIndexData.class, this);
	}

	private void defineIndexingTemplate(PropertyBindingContext thePropertyBindingContext) {
		IndexSchemaElement indexSchemaElement = thePropertyBindingContext.indexSchemaElement();

		//In order to support dynamic fields, we have to use field templates. We _must_ define the template at bootstrap time and cannot
		//create them adhoc. https://docs.jboss.org/hibernate/search/6.0/reference/en-US/html_single/#mapper-orm-bridge-index-field-dsl-dynamic
		//I _think_ im doing the right thing here by indicating that everything matching this template uses this analyzer.
		IndexFieldTypeFactory indexFieldTypeFactory = thePropertyBindingContext.typeFactory();
		StringIndexFieldTypeOptionsStep<?> textType =
			indexFieldTypeFactory.asString()
				// wip mb where do we do unicode normalization?  Java-side, or in the analyzer?
				.analyzer("autocompleteWordEdgeAnalyzer")
				.projectable(Projectable.NO);
		IndexSchemaObjectField spfield = indexSchemaElement.objectField("sp", ObjectStructure.FLATTENED);
		IndexObjectFieldReference sp = spfield.toReference();


		// Note: the lucene/elastic independent api is hurting a bit here.
		// For lucene, we need a separate field for each analyzer.  So we'll add string (for :exact), and text (for :text).
		// They aren't marked stored, so there's no space cost beyond the index for each.
		// But for elastic, I'd rather have a single field defined, with multi-field sub-fields.  The index cost is the same,
		// but elastic will actually store all fields in the source document.
		spfield.objectFieldTemplate("stringIndex", ObjectStructure.FLATTENED).matchingPathGlob("*.string");
		spfield.fieldTemplate("text", textType).matchingPathGlob("*.string.text");
		indexSchemaElement
			.fieldTemplate("SearchParamText", textType)
			.matchingPathGlob(SEARCH_PARAM_TEXT_PREFIX + "*");

		// last, since the globs are matched in declaration order, and * matches even nested nodes.
		spfield.objectFieldTemplate("spObject", ObjectStructure.FLATTENED).matchingPathGlob("*");

	}

	@Override
	public void write(DocumentElement theDocument, ExtendedLuceneIndexData theIndexData, PropertyBridgeWriteContext thePropertyBridgeWriteContext) {
		if (theIndexData != null) {
			ourLog.trace("Writing index data for {}", theIndexData);
			theIndexData.writeIndexElements(theDocument);
		}
	}

}
