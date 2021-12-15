package com.vladmihalcea.hibernate.type.util.objectmapper;

import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.OffsetDateTime;

public class OffsetDateTimeDeserializer extends JsonDeserializer<OffsetDateTime> {

  @Override
  public OffsetDateTime deserialize(JsonParser jsonParser,
      DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
      if (jsonParser.getText() != null) {
          return OffsetDateTime.parse(jsonParser.getText(), ISO_OFFSET_DATE_TIME);
      }
      return null;
  }

  @Override
  public Class<OffsetDateTime> handledType() {
    return OffsetDateTime.class;
  }
}
