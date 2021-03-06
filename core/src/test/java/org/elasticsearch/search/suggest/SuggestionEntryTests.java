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

package org.elasticsearch.search.suggest;

import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.search.suggest.Suggest.Suggestion.Entry;
import org.elasticsearch.search.suggest.Suggest.Suggestion.Entry.Option;
import org.elasticsearch.search.suggest.completion.CompletionSuggestion;
import org.elasticsearch.search.suggest.phrase.PhraseSuggestion;
import org.elasticsearch.search.suggest.term.TermSuggestion;
import org.elasticsearch.test.ESTestCase;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.elasticsearch.common.xcontent.XContentHelper.toXContent;
import static org.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertToXContentEquivalent;

public class SuggestionEntryTests extends ESTestCase {

    private static final Map<Class<? extends Entry>, Function<XContentParser, ? extends Entry>> ENTRY_PARSERS = new HashMap<>();
    static {
        ENTRY_PARSERS.put(TermSuggestion.Entry.class, TermSuggestion.Entry::fromXContent);
        ENTRY_PARSERS.put(PhraseSuggestion.Entry.class, PhraseSuggestion.Entry::fromXContent);
        ENTRY_PARSERS.put(CompletionSuggestion.Entry.class, CompletionSuggestion.Entry::fromXContent);
    }

    /**
     * Create a randomized Suggestion.Entry
     */
    @SuppressWarnings("unchecked")
    public static <O extends Option> Entry<O> createTestItem(Class<? extends Entry> entryType) {
        Text entryText = new Text(randomAlphaOfLengthBetween(5, 15));
        int offset = randomInt();
        int length = randomInt();
        Entry entry;
        Supplier<Option> supplier;
        if (entryType == TermSuggestion.Entry.class) {
            entry = new TermSuggestion.Entry(entryText, offset, length);
            supplier = TermSuggestionOptionTests::createTestItem;
        } else if (entryType == PhraseSuggestion.Entry.class) {
            entry = new PhraseSuggestion.Entry(entryText, offset, length, randomDouble());
            supplier = SuggestionOptionTests::createTestItem;
        } else if (entryType == CompletionSuggestion.Entry.class) {
            entry = new CompletionSuggestion.Entry(entryText, offset, length);
            supplier = CompletionSuggestionOptionTests::createTestItem;
        } else {
            throw new UnsupportedOperationException("entryType not supported [" + entryType + "]");
        }
        int numOptions = randomIntBetween(0, 5);
        for (int i = 0; i < numOptions; i++) {
            entry.addOption(supplier.get());
        }
        return entry;
    }

    /*
    @SuppressWarnings("unchecked")
    public void testFromXContent() throws IOException {
        for (Class<? extends Entry> entryType : ENTRY_PARSERS.keySet()) {
            Entry<Option> entry = createTestItem(entryType);
            XContentType xContentType = randomFrom(XContentType.values());
            boolean humanReadable = randomBoolean();
            BytesReference originalBytes = toShuffledXContent(entry, xContentType, ToXContent.EMPTY_PARAMS, humanReadable);
            Entry<Option> parsed;
            try (XContentParser parser = createParser(xContentType.xContent(), originalBytes)) {
                ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser::getTokenLocation);
                parsed = ENTRY_PARSERS.get(entry.getClass()).apply(parser);
                assertEquals(XContentParser.Token.END_OBJECT, parser.currentToken());
                assertNull(parser.nextToken());
            }
            assertEquals(entry.getClass(), parsed.getClass());
            assertEquals(entry.getText(), parsed.getText());
            assertEquals(entry.getLength(), parsed.getLength());
            assertEquals(entry.getOffset(), parsed.getOffset());
            assertEquals(entry.getOptions().size(), parsed.getOptions().size());
            for (int i = 0; i < entry.getOptions().size(); i++) {
                assertEquals(entry.getOptions().get(i).getClass(), parsed.getOptions().get(i).getClass());
            }
            assertToXContentEquivalent(originalBytes, toXContent(parsed, xContentType, humanReadable), xContentType);
        }
    }
    */
    
    public void testToXContent() throws IOException {
        Option option = new Option(new Text("someText"), new Text("somethingHighlighted"), 1.3f, true);
        Entry<Option> entry = new Entry<>(new Text("entryText"), 42, 313);
        entry.addOption(option);
        BytesReference xContent = toXContent(entry, XContentType.JSON, randomBoolean());
        assertEquals(
                "{\"text\":\"entryText\","
                + "\"offset\":42,"
                + "\"length\":313,"
                + "\"options\":["
                    + "{\"text\":\"someText\","
                    + "\"highlighted\":\"somethingHighlighted\","
                    + "\"score\":1.3,"
                    + "\"collate_match\":true}"
                + "]}", xContent.utf8ToString());

        org.elasticsearch.search.suggest.term.TermSuggestion.Entry.Option termOption =
                new org.elasticsearch.search.suggest.term.TermSuggestion.Entry.Option(new Text("termSuggestOption"), 42, 3.13f);
        entry = new Entry<>(new Text("entryText"), 42, 313);
        entry.addOption(termOption);
        xContent = toXContent(entry, XContentType.JSON, randomBoolean());
        assertEquals(
                "{\"text\":\"entryText\","
                + "\"offset\":42,"
                + "\"length\":313,"
                + "\"options\":["
                    + "{\"text\":\"termSuggestOption\","
                    + "\"score\":3.13,"
                    + "\"freq\":42}"
                + "]}", xContent.utf8ToString());

        org.elasticsearch.search.suggest.completion.CompletionSuggestion.Entry.Option completionOption =
                new org.elasticsearch.search.suggest.completion.CompletionSuggestion.Entry.Option(-1, new Text("completionOption"),
                        3.13f, Collections.singletonMap("key", Collections.singleton("value")));
        entry = new Entry<>(new Text("entryText"), 42, 313);
        entry.addOption(completionOption);
        xContent = toXContent(entry, XContentType.JSON, randomBoolean());
        assertEquals(
                "{\"text\":\"entryText\","
                + "\"offset\":42,"
                + "\"length\":313,"
                + "\"options\":["
                    + "{\"text\":\"completionOption\","
                    + "\"score\":3.13,"
                    + "\"contexts\":{\"key\":[\"value\"]}"
                    + "}"
                + "]}", xContent.utf8ToString());
    }

}
