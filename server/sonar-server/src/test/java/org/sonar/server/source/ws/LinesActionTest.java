/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.source.ws;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang.StringEscapeUtils;
import org.elasticsearch.common.collect.Lists;
import org.elasticsearch.common.collect.Maps;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.search.BaseNormalizer;
import org.sonar.server.source.HtmlSourceDecorator;
import org.sonar.server.source.index.SourceLineDoc;
import org.sonar.server.source.index.SourceLineIndex;
import org.sonar.server.source.index.SourceLineIndexDefinition;
import org.sonar.server.ws.WsTester;

import java.util.Date;
import java.util.Map;

import static com.google.common.collect.Lists.newArrayList;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class LinesActionTest {

  @Mock
  SourceLineIndex sourceLineIndex;

  @Mock
  HtmlSourceDecorator htmlSourceDecorator;

  WsTester tester;

  @Before
  public void setUp() throws Exception {
    tester = new WsTester(
      new SourcesWs(
        mock(ShowAction.class),
        mock(RawAction.class),
        mock(ScmAction.class),
        new LinesAction(sourceLineIndex, htmlSourceDecorator),
        mock(HashAction.class)
      )
    );
    when(htmlSourceDecorator.getDecoratedSourceAsHtml(anyString(), anyString())).thenAnswer(new Answer<String>() {
      @Override
      public String answer(InvocationOnMock invocation) throws Throwable {
        return "<span class=\"" + invocation.getArguments()[1] + "\">" +
          StringEscapeUtils.escapeHtml((String) invocation.getArguments()[0]) +
            "</span>";
      }
    });
  }

  @Test
  public void show_source() throws Exception {
    String componentUuid = "efgh";
    Date updatedAt = new Date();
    String scmDate = "2014-01-01T12:34:56.789Z";
    SourceLineDoc line1 = new SourceLineDoc(ImmutableMap.<String, Object>builder()
      .put(SourceLineIndexDefinition.FIELD_PROJECT_UUID, "abcd")
      .put(SourceLineIndexDefinition.FIELD_FILE_UUID, "efgh")
      .put(SourceLineIndexDefinition.FIELD_LINE, 1)
      .put(SourceLineIndexDefinition.FIELD_SCM_REVISION, "cafebabe")
      .put(SourceLineIndexDefinition.FIELD_SCM_DATE, scmDate)
      .put(SourceLineIndexDefinition.FIELD_SCM_AUTHOR, "polop")
      .put(SourceLineIndexDefinition.FIELD_SOURCE, "class Polop {")
      .put(SourceLineIndexDefinition.FIELD_HIGHLIGHTING, "h1")
      .put(BaseNormalizer.UPDATED_AT_FIELD, updatedAt)
      .build());
    SourceLineDoc line2 = new SourceLineDoc(ImmutableMap.<String, Object>builder()
      .put(SourceLineIndexDefinition.FIELD_PROJECT_UUID, "abcd")
      .put(SourceLineIndexDefinition.FIELD_FILE_UUID, "efgh")
      .put(SourceLineIndexDefinition.FIELD_LINE, 2)
      .put(SourceLineIndexDefinition.FIELD_SCM_REVISION, "cafebabe")
      .put(SourceLineIndexDefinition.FIELD_SCM_DATE, scmDate)
      .put(SourceLineIndexDefinition.FIELD_SCM_AUTHOR, "polop")
      .put(SourceLineIndexDefinition.FIELD_SOURCE, "  // Empty")
      .put(SourceLineIndexDefinition.FIELD_HIGHLIGHTING, "h2")
      .put(BaseNormalizer.UPDATED_AT_FIELD, updatedAt)
      .build());
    SourceLineDoc line3 = new SourceLineDoc(ImmutableMap.<String, Object>builder()
      .put(SourceLineIndexDefinition.FIELD_PROJECT_UUID, "abcd")
      .put(SourceLineIndexDefinition.FIELD_FILE_UUID, "efgh")
      .put(SourceLineIndexDefinition.FIELD_LINE, 3)
      .put(SourceLineIndexDefinition.FIELD_SCM_REVISION, "cafebabe")
      .put(SourceLineIndexDefinition.FIELD_SCM_DATE, scmDate)
      .put(SourceLineIndexDefinition.FIELD_SCM_AUTHOR, "polop")
      .put(SourceLineIndexDefinition.FIELD_SOURCE, "}")
      .put(SourceLineIndexDefinition.FIELD_HIGHLIGHTING, "h3")
      .put(BaseNormalizer.UPDATED_AT_FIELD, updatedAt)
      .build());
    when(sourceLineIndex.getLines(eq(componentUuid), anyInt(), anyInt())).thenReturn(newArrayList(
      line1,
      line2,
      line3
    ));

    WsTester.TestRequest request = tester.newGetRequest("api/sources", "lines").setParam("uuid", componentUuid);
    // Using non-strict match b/c of dates
    request.execute().assertJson(getClass(), "show_source.json", false);
  }

  @Test
  public void fail_to_show_source_if_no_source_found() throws Exception {
    String componentKey = "src/Foo.java";
    when(sourceLineIndex.getLines(anyString(), anyInt(), anyInt())).thenReturn(Lists.<SourceLineDoc>newArrayList());

    try {
      WsTester.TestRequest request = tester.newGetRequest("api/sources", "lines").setParam("uuid", componentKey);
      request.execute();
      fail();
    } catch (Exception e) {
      assertThat(e).isInstanceOf(NotFoundException.class);
    }
  }

  @Test
  public void show_source_with_from_and_to_params() throws Exception {
    String fileKey = "src/Foo.java";
    Map<String, Object> fieldMap = Maps.newHashMap();
    fieldMap.put(SourceLineIndexDefinition.FIELD_PROJECT_UUID, "abcd");
    fieldMap.put(SourceLineIndexDefinition.FIELD_FILE_UUID, "efgh");
    fieldMap.put(SourceLineIndexDefinition.FIELD_LINE, 3);
    fieldMap.put(SourceLineIndexDefinition.FIELD_SCM_REVISION, "cafebabe");
    fieldMap.put(SourceLineIndexDefinition.FIELD_SCM_DATE, null);
    fieldMap.put(SourceLineIndexDefinition.FIELD_SCM_AUTHOR, "polop");
    fieldMap.put(SourceLineIndexDefinition.FIELD_SOURCE, "}");
    fieldMap.put(SourceLineIndexDefinition.FIELD_HIGHLIGHTING, "");
    fieldMap.put(BaseNormalizer.UPDATED_AT_FIELD, new Date());
    when(sourceLineIndex.getLines(fileKey, 3, 3)).thenReturn(newArrayList(
      new SourceLineDoc(fieldMap)
    ));
    WsTester.TestRequest request = tester
      .newGetRequest("api/sources", "lines")
      .setParam("uuid", fileKey)
      .setParam("from", "3")
      .setParam("to", "3");
    request.execute().assertJson(getClass(), "show_source_with_params_from_and_to.json");
  }
}
