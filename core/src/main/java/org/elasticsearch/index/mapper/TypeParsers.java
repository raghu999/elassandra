/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.index.mapper;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.index.IndexOptions;
import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.Version;
import org.elasticsearch.common.joda.FormatDateTimeFormatter;
import org.elasticsearch.common.joda.Joda;
import org.elasticsearch.common.logging.DeprecationLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.xcontent.support.XContentMapValues;
import org.elasticsearch.index.analysis.NamedAnalyzer;
import org.elasticsearch.index.mapper.Mapper.CqlCollection;
import org.elasticsearch.index.mapper.Mapper.CqlStruct;
import org.elasticsearch.index.similarity.SimilarityProvider;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static org.elasticsearch.common.xcontent.support.XContentMapValues.isArray;
import static org.elasticsearch.common.xcontent.support.XContentMapValues.nodeFloatValue;
import static org.elasticsearch.common.xcontent.support.XContentMapValues.nodeIntegerValue;
import static org.elasticsearch.common.xcontent.support.XContentMapValues.nodeMapValue;
import static org.elasticsearch.common.xcontent.support.XContentMapValues.nodeStringValue;

/**
 *
 */
public class TypeParsers {

    protected static final DeprecationLogger deprecationLogger = new DeprecationLogger(Loggers.getLogger(TypeParsers.class));

    public static final String DOC_VALUES = "doc_values";
    public static final String INDEX_OPTIONS_DOCS = "docs";
    public static final String INDEX_OPTIONS_FREQS = "freqs";
    public static final String INDEX_OPTIONS_POSITIONS = "positions";
    public static final String INDEX_OPTIONS_OFFSETS = "offsets";

    public static final String CQL_COLLECTION = "cql_collection";
    public static final String CQL_STRUCT = "cql_struct";
    public static final String CQL_UDT_NAME = "cql_udt_name";
    public static final String CQL_MANDATORY = "cql_mandatory";
    public static final String CQL_STATIC_COLUMN = "cql_static_column";
    public static final String CQL_PARTITION_KEY = "cql_partition_key";
    public static final String CQL_PRIMARY_KEY_ORDER = "cql_primary_key_order";

    private static final DeprecationLogger DEPRECATION_LOGGER = new DeprecationLogger(Loggers.getLogger(TypeParsers.class));

    public static boolean nodeBooleanValue(String fieldName, String propertyName, Object node) {
        return XContentMapValues.lenientNodeBooleanValue(node, fieldName + "." + propertyName);
    }

    @Deprecated // for legacy ints only
    public static void parseNumberField(LegacyNumberFieldMapper.Builder builder, String name, Map<String, Object> numberNode, Mapper.TypeParser.ParserContext parserContext) {
        parseField(builder, name, numberNode, parserContext);
        for (Iterator<Map.Entry<String, Object>> iterator = numberNode.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<String, Object> entry = iterator.next();
            String propName = entry.getKey();
            Object propNode = entry.getValue();
            if (propName.equals("precision_step")) {
                builder.precisionStep(nodeIntegerValue(propNode));
                iterator.remove();
            } else if (propName.equals("ignore_malformed")) {
                builder.ignoreMalformed(nodeBooleanValue(name, "ignore_malformed", propNode));
                iterator.remove();
            } else if (propName.equals("coerce")) {
                builder.coerce(nodeBooleanValue(name, "coerce", propNode));
                iterator.remove();
            } else if (propName.equals("similarity")) {
                SimilarityProvider similarityProvider = resolveSimilarity(parserContext, name, propNode.toString());
                builder.similarity(similarityProvider);
                iterator.remove();
            } else if (parseMultiField(builder, name, parserContext, propName, propNode)) {
                iterator.remove();
            }
        }
    }

    private static void parseAnalyzersAndTermVectors(FieldMapper.Builder builder, String name, Map<String, Object> fieldNode,
                                                     Mapper.TypeParser.ParserContext parserContext) {
        NamedAnalyzer indexAnalyzer = null;
        NamedAnalyzer searchAnalyzer = null;
        NamedAnalyzer searchQuoteAnalyzer = null;

        for (Iterator<Map.Entry<String, Object>> iterator = fieldNode.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<String, Object> entry = iterator.next();
            final String propName = entry.getKey();
            final Object propNode = entry.getValue();
            if (propName.equals("term_vector")) {
                parseTermVector(name, propNode.toString(), builder);
                iterator.remove();
            } else if (propName.equals("store_term_vectors")) {
                builder.storeTermVectors(nodeBooleanValue(name, "store_term_vectors", propNode));
                iterator.remove();
            } else if (propName.equals("store_term_vector_offsets")) {
                builder.storeTermVectorOffsets(nodeBooleanValue(name, "store_term_vector_offsets", propNode));
                iterator.remove();
            } else if (propName.equals("store_term_vector_positions")) {
                builder.storeTermVectorPositions(
                    nodeBooleanValue(name, "store_term_vector_positions", propNode));
                iterator.remove();
            } else if (propName.equals("store_term_vector_payloads")) {
                builder.storeTermVectorPayloads(nodeBooleanValue(name,"store_term_vector_payloads", propNode));
                iterator.remove();
            } else if (propName.equals(CQL_STRUCT)) {
                switch(StringUtils.lowerCase(propNode.toString())) {
                case "map" : builder.cqlStruct(CqlStruct.MAP); break;
                case "udt" : builder.cqlStruct(CqlStruct.UDT); break;
                case "tuple" : builder.cqlStruct(CqlStruct.TUPLE); break;
                }
                iterator.remove();
            } else if (propName.equals(CQL_MANDATORY)) {
                builder.cqlPartialUpdate(nodeBooleanValue(name, CQL_MANDATORY, propNode));
                iterator.remove();
            } else if (propName.equals(CQL_PARTITION_KEY)) {
                builder.cqlPartitionKey(nodeBooleanValue(name, CQL_PARTITION_KEY, propNode));
                iterator.remove();
            } else if (propName.equals(CQL_STATIC_COLUMN)) {
                builder.cqlStaticColumn(nodeBooleanValue(name, CQL_STATIC_COLUMN, propNode));
                iterator.remove();
            } else if (propName.equals(CQL_PRIMARY_KEY_ORDER)) {
                builder.cqlPrimaryKeyOrder(nodeIntegerValue(propNode));
                iterator.remove();
            } else if (propName.equals("analyzer")) {
                NamedAnalyzer analyzer = parserContext.getIndexAnalyzers().get(propNode.toString());
                if (analyzer == null) {
                    throw new MapperParsingException("analyzer [" + propNode.toString() + "] not found for field [" + name + "]");
                }
                indexAnalyzer = analyzer;
                iterator.remove();
            } else if (propName.equals("search_analyzer")) {
                NamedAnalyzer analyzer = parserContext.getIndexAnalyzers().get(propNode.toString());
                if (analyzer == null) {
                    throw new MapperParsingException("analyzer [" + propNode.toString() + "] not found for field [" + name + "]");
                }
                searchAnalyzer = analyzer;
                iterator.remove();
            } else if (propName.equals("search_quote_analyzer")) {
                NamedAnalyzer analyzer = parserContext.getIndexAnalyzers().get(propNode.toString());
                if (analyzer == null) {
                    throw new MapperParsingException("analyzer [" + propNode.toString() + "] not found for field [" + name + "]");
                }
                searchQuoteAnalyzer = analyzer;
                iterator.remove();
            }
        }

        if (indexAnalyzer == null && searchAnalyzer != null) {
            throw new MapperParsingException("analyzer on field [" + name + "] must be set when search_analyzer is set");
        }

        if (searchAnalyzer == null && searchQuoteAnalyzer != null) {
            throw new MapperParsingException("analyzer and search_analyzer on field [" + name +
                "] must be set when search_quote_analyzer is set");
        }

        if (searchAnalyzer == null) {
            searchAnalyzer = indexAnalyzer;
        }

        if (searchQuoteAnalyzer == null) {
            searchQuoteAnalyzer = searchAnalyzer;
        }

        if (indexAnalyzer != null) {
            builder.indexAnalyzer(indexAnalyzer);
        }
        if (searchAnalyzer != null) {
            builder.searchAnalyzer(searchAnalyzer);
        }
        if (searchQuoteAnalyzer != null) {
            builder.searchQuoteAnalyzer(searchQuoteAnalyzer);
        }
    }

    public static boolean parseNorms(FieldMapper.Builder builder, String fieldName, String propName, Object propNode,
                                     Mapper.TypeParser.ParserContext parserContext) {
        if (propName.equals("norms")) {
            if (propNode instanceof Map) {
                final Map<String, Object> properties = nodeMapValue(propNode, "norms");
                for (Iterator<Entry<String, Object>> propsIterator = properties.entrySet().iterator(); propsIterator.hasNext(); ) {
                    Entry<String, Object> entry2 = propsIterator.next();
                    final String propName2 = entry2.getKey();
                    final Object propNode2 = entry2.getValue();
                    if (propName2.equals("enabled")) {
                        builder.omitNorms(nodeBooleanValue(fieldName, "enabled", propNode2) == false);
                        propsIterator.remove();
                    } else if (propName2.equals("loading")) {
                        // ignore for bw compat
                        propsIterator.remove();
                    }
                }
                DocumentMapperParser.checkNoRemainingFields(propName, properties, parserContext.indexVersionCreated());
                DEPRECATION_LOGGER.deprecated("The [norms{enabled:true/false}] way of specifying norms is deprecated, please use " +
                    "[norms:true/false] instead");
            } else {
                builder.omitNorms(nodeBooleanValue(fieldName,"norms", propNode) == false);
            }
            return true;
        } else if (propName.equals("omit_norms")) {
            builder.omitNorms(nodeBooleanValue(fieldName,"norms", propNode));
            DEPRECATION_LOGGER.deprecated("[omit_norms] is deprecated, please use [norms] instead with the opposite boolean value");
            return true;
        } else {
            return false;
        }
    }

    /**
     * Parse text field attributes. In addition to {@link #parseField common attributes}
     * this will parse analysis and term-vectors related settings.
     */
    public static void parseTextField(FieldMapper.Builder builder, String name, Map<String, Object> fieldNode,
                                      Mapper.TypeParser.ParserContext parserContext) {
        parseField(builder, name, fieldNode, parserContext);
        parseAnalyzersAndTermVectors(builder, name, fieldNode, parserContext);
        for (Iterator<Map.Entry<String, Object>> iterator = fieldNode.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<String, Object> entry = iterator.next();
            final String propName = entry.getKey();
            final Object propNode = entry.getValue();
            if (parseNorms(builder, name, propName, propNode, parserContext)) {
                iterator.remove();
            }
        }
    }

    /**
     * Parse common field attributes such as {@code doc_values} or {@code store}.
     */
    public static void parseField(FieldMapper.Builder builder, String name, Map<String, Object> fieldNode, Mapper.TypeParser.ParserContext parserContext) {
        Version indexVersionCreated = parserContext.indexVersionCreated();
        for (Iterator<Map.Entry<String, Object>> iterator = fieldNode.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<String, Object> entry = iterator.next();
            final String propName = entry.getKey();
            final Object propNode = entry.getValue();
            if (false == propName.equals("null_value") && propNode == null) {
                /*
                 * No properties *except* null_value are allowed to have null. So we catch it here and tell the user something useful rather
                 * than send them a null pointer exception later.
                 */
                throw new MapperParsingException("[" + propName + "] must not have a [null] value");
            }
            if (propName.equals("store")) {
                builder.store(nodeBooleanValue(name, propName, propNode));
                iterator.remove();
            } else if (propName.equals("index")) {
                builder.index(parseIndex(name, propNode.toString()));
                iterator.remove();
            } else if (propName.equals(DOC_VALUES)) {
                builder.docValues(nodeBooleanValue(name, DOC_VALUES, propNode));
                iterator.remove();
            } else if (propName.equals("boost")) {
                builder.boost(nodeFloatValue(propNode));
                iterator.remove();
            } else if (parserContext.indexVersionCreated().before(Version.V_5_0_0_alpha1)
                && parseNorms(builder, name, propName, propNode, parserContext)) {
                iterator.remove();
            } else if (propName.equals("index_options")) {
                builder.indexOptions(nodeIndexOptionValue(propNode));
                iterator.remove();
            } else if (propName.equals("include_in_all")) {
                if (parserContext.isWithinMultiField()) {
                    deprecationLogger.deprecated("include_in_all in multi fields is deprecated "
                            + "because it doesn't do anything. Found the include_in_all in field "
                            + "[{}] which is within a multi field.", name);
                } else {
                    deprecationLogger.deprecated("field [include_in_all] is deprecated, as [_all] is deprecated, " +
                                    "and will be disallowed in 6.0, use [copy_to] instead.");
                    builder.includeInAll(nodeBooleanValue(name, "include_in_all", propNode));
                }
                iterator.remove();
            } else if (propName.equals("similarity")) {
                SimilarityProvider similarityProvider = resolveSimilarity(parserContext, name, propNode.toString());
                builder.similarity(similarityProvider);
                iterator.remove();
            } else if (propName.equals("fielddata")
                    && propNode instanceof Map
                    && parserContext.indexVersionCreated().before(Version.V_5_0_0_alpha1)) {
                // ignore for bw compat
                iterator.remove();
            } else if (parseMultiField(builder, name, parserContext, propName, propNode)) {
                iterator.remove();
            } else if (propName.equals("copy_to")) {
                if (parserContext.isWithinMultiField()) {
                    if (indexVersionCreated.after(Version.V_2_1_0) ||
                        (indexVersionCreated.after(Version.V_2_0_1) && indexVersionCreated.before(Version.V_2_1_0))) {
                        throw new MapperParsingException("copy_to in multi fields is not allowed. Found the copy_to in field [" + name + "] which is within a multi field.");
                    } else {
                        ESLoggerFactory.getLogger("mapping [" + parserContext.type() + "]").warn("Found a copy_to in field [{}] which is within a multi field. This feature has been removed and the copy_to will be removed from the mapping.", name);
                    }
                } else {
                    parseCopyFields(propNode, builder);
                }
                iterator.remove();
            } else if (propName.equals(TypeParsers.CQL_MANDATORY)) {
                builder.cqlPartialUpdate((boolean)propNode);
                iterator.remove();
            } else if (propName.equals(TypeParsers.CQL_PARTITION_KEY)) {
                builder.cqlPartitionKey((boolean)propNode);
                iterator.remove();
            } else if (propName.equals(TypeParsers.CQL_STATIC_COLUMN)) {
                builder.cqlStaticColumn((Boolean)propNode);
                iterator.remove();
            } else if (propName.equals(TypeParsers.CQL_PRIMARY_KEY_ORDER)) {
                builder.cqlPrimaryKeyOrder((Integer)propNode);
                iterator.remove();
            } else if (propName.equals(TypeParsers.CQL_COLLECTION)) {
                String value = StringUtils.lowerCase(propNode.toString());
                switch (value) {
                case "list": builder.cqlCollection(CqlCollection.LIST); break;
                case "set": builder.cqlCollection(CqlCollection.SET); break;
                case "singleton": builder.cqlCollection(CqlCollection.SINGLETON); break;
                }
                iterator.remove();
            } else if (propName.equals(TypeParsers.CQL_STRUCT)) {
                String value = StringUtils.lowerCase(propNode.toString());
                switch (value) {
                case "tuple": builder.cqlStruct(CqlStruct.TUPLE); break;
                case "map": builder.cqlStruct(CqlStruct.MAP); break;
                case "udt": builder.cqlStruct(CqlStruct.UDT); break;
                }
                iterator.remove();
            }
        }
        if (indexVersionCreated.before(Version.V_2_2_0)) {
            // analyzer, search_analyzer, term_vectors were accepted on all fields
            // before 2.2, even though it made little sense
            parseAnalyzersAndTermVectors(builder, name, fieldNode, parserContext);
        }
    }

    public static boolean parseMultiField(FieldMapper.Builder builder, String name, Mapper.TypeParser.ParserContext parserContext,
                                          String propName, Object propNode) {
        parserContext = parserContext.createMultiFieldContext(parserContext);
        if (propName.equals("fields")) {

            final Map<String, Object> multiFieldsPropNodes;

            if (propNode instanceof List && ((List<?>) propNode).isEmpty()) {
                multiFieldsPropNodes = Collections.emptyMap();
            } else if (propNode instanceof Map) {
                multiFieldsPropNodes = (Map<String, Object>) propNode;
            } else {
                throw new MapperParsingException("expected map for property [fields] on field [" + propNode + "] or " +
                    "[" + propName + "] but got a " + propNode.getClass());
            }

            for (Map.Entry<String, Object> multiFieldEntry : multiFieldsPropNodes.entrySet()) {
                String multiFieldName = multiFieldEntry.getKey();
                if (multiFieldName.contains(".")) {
                    throw new MapperParsingException("Field name [" + multiFieldName + "] which is a multi field of [" + name + "] cannot" +
                        " contain '.'");
                }
                if (!(multiFieldEntry.getValue() instanceof Map)) {
                    throw new MapperParsingException("illegal field [" + multiFieldName + "], only fields can be specified inside fields");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> multiFieldNodes = (Map<String, Object>) multiFieldEntry.getValue();

                String type;
                Object typeNode = multiFieldNodes.get("type");
                if (typeNode != null) {
                    type = typeNode.toString();
                } else {
                    throw new MapperParsingException("no type specified for property [" + multiFieldName + "]");
                }
                if (type.equals(ObjectMapper.CONTENT_TYPE) || type.equals(ObjectMapper.NESTED_CONTENT_TYPE)) {
                    throw new MapperParsingException("Type [" + type + "] cannot be used in multi field");
                }

                Mapper.TypeParser typeParser = parserContext.typeParser(type);
                if (typeParser == null) {
                    throw new MapperParsingException("no handler for type [" + type + "] declared on field [" + multiFieldName + "]");
                }
                builder.addMultiField(typeParser.parse(multiFieldName, multiFieldNodes, parserContext));
                multiFieldNodes.remove("type");
                DocumentMapperParser.checkNoRemainingFields(propName, multiFieldNodes, parserContext.indexVersionCreated());
            }
            return true;
        }
        return false;
    }

    private static IndexOptions nodeIndexOptionValue(final Object propNode) {
        final String value = propNode.toString();
        if (INDEX_OPTIONS_OFFSETS.equalsIgnoreCase(value)) {
            return IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS;
        } else if (INDEX_OPTIONS_POSITIONS.equalsIgnoreCase(value)) {
            return IndexOptions.DOCS_AND_FREQS_AND_POSITIONS;
        } else if (INDEX_OPTIONS_FREQS.equalsIgnoreCase(value)) {
            return IndexOptions.DOCS_AND_FREQS;
        } else if (INDEX_OPTIONS_DOCS.equalsIgnoreCase(value)) {
            return IndexOptions.DOCS;
        } else {
            throw new ElasticsearchParseException("failed to parse index option [{}]", value);
        }
    }

    public static FormatDateTimeFormatter parseDateTimeFormatter(Object node) {
        return Joda.forPattern(node.toString());
    }

    public static void parseTermVector(String fieldName, String termVector, FieldMapper.Builder builder) throws MapperParsingException {
        if ("no".equals(termVector)) {
            builder.storeTermVectors(false);
        } else if ("yes".equals(termVector)) {
            builder.storeTermVectors(true);
        } else if ("with_offsets".equals(termVector)) {
            builder.storeTermVectorOffsets(true);
        } else if ("with_positions".equals(termVector)) {
            builder.storeTermVectorPositions(true);
        } else if ("with_positions_offsets".equals(termVector)) {
            builder.storeTermVectorPositions(true);
            builder.storeTermVectorOffsets(true);
        } else if ("with_positions_payloads".equals(termVector)) {
            builder.storeTermVectorPositions(true);
            builder.storeTermVectorPayloads(true);
        } else if ("with_positions_offsets_payloads".equals(termVector)) {
            builder.storeTermVectorPositions(true);
            builder.storeTermVectorOffsets(true);
            builder.storeTermVectorPayloads(true);
        } else {
            throw new MapperParsingException("wrong value for termVector [" + termVector + "] for field [" + fieldName + "]");
        }
    }

    private static boolean parseIndex(String fieldName, String index) throws MapperParsingException {
        switch (index) {
        case "true":
            return true;
        case "false":
            return false;
        case "not_analyzed":
        case "analyzed":
        case "no":
            DEPRECATION_LOGGER.deprecated("Expected a boolean [true/false] for property [index] but got [{}]", index);
            return "no".equals(index) == false;
        default:
            throw new IllegalArgumentException("Can't parse [index] value [" + index + "] for field [" + fieldName + "], expected [true] or [false]");
        }
    }

    @SuppressWarnings("unchecked")
    public static void parseCopyFields(Object propNode, FieldMapper.Builder builder) {
        FieldMapper.CopyTo.Builder copyToBuilder = new FieldMapper.CopyTo.Builder();
        if (isArray(propNode)) {
            for (Object node : (List<Object>) propNode) {
                copyToBuilder.add(nodeStringValue(node, null));
            }
        } else {
            copyToBuilder.add(nodeStringValue(propNode, null));
        }
        builder.copyTo(copyToBuilder.build());
    }

    private static SimilarityProvider resolveSimilarity(Mapper.TypeParser.ParserContext parserContext, String name, String value) {
        if (parserContext.indexVersionCreated().before(Version.V_5_0_0_alpha1) &&
            "default".equals(value) &&
            // check if "default" similarity is overridden
            parserContext.getSimilarity("default") == null) {
            // "default" similarity has been renamed into "classic" in 5.x.
            value = "classic";
        }
        SimilarityProvider similarityProvider = parserContext.getSimilarity(value);
        if (similarityProvider == null) {
            throw new MapperParsingException("Unknown Similarity type [" + value + "] for field [" + name + "]");
        }
        return similarityProvider;
    }
}
