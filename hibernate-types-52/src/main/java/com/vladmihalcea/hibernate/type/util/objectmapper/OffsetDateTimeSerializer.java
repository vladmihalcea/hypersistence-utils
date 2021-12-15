package com.vladmihalcea.hibernate.type.util.objectmapper;

import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.time.OffsetDateTime;

/**
 * Jackson Json Serializer which transforms an java.time.OffsetDateTime to String in Format java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME .
 *
 * With this format the current timezone of OffsetDateTime is not lost.
 */
public class OffsetDateTimeSerializer extends JsonSerializer<OffsetDateTime> {

  @Override
  public void serialize(OffsetDateTime offsetDateTime, JsonGenerator jsonGenerator,
      SerializerProvider serializerProvider) throws IOException {
      if (offsetDateTime == null) {
          jsonGenerator.writeNull();
      } else {
          jsonGenerator.writeString(offsetDateTime.format(ISO_OFFSET_DATE_TIME));
      }
  }

  @Override
  public Class<OffsetDateTime> handledType() {
      return OffsetDateTime.class;
  }
}
