/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.avro.specific;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;

import org.apache.avro.Schema;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.junit.Test;

public class TestSpecificDatumReaderTrustedPackages {

  /** A class with a String constructor in a package that is not trusted. */
  public static class NotTrusted {
    private final String value;

    public NotTrusted(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return value;
    }
  }

  // The trust check is in SpecificDatumReader; FastReaderBuilder (enabled for
  // this module's tests by org.apache.avro.fastread) does not go through it.
  private static SpecificData data() {
    SpecificData data = new SpecificData();
    data.setFastReaderEnabled(false);
    return data;
  }

  private static SpecificDatumReader<Object> reader(Schema schema) {
    return new SpecificDatumReader<>(schema, schema, data());
  }

  private static Schema stringSchema(String javaClass) {
    Schema schema = Schema.create(Schema.Type.STRING);
    schema.addProp(SpecificData.CLASS_PROP, javaClass);
    return schema;
  }

  private static byte[] encodeString(String s) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
    encoder.writeString(s);
    encoder.flush();
    return out.toByteArray();
  }

  private static Object read(SpecificDatumReader<Object> reader, byte[] bytes) throws IOException {
    return reader.read(null, DecoderFactory.get().binaryDecoder(bytes, null));
  }

  @Test
  public void trustedJavaClassIsRead() throws IOException {
    Schema schema = stringSchema(BigDecimal.class.getName());
    Object value = read(reader(schema), encodeString("12.5"));
    assertEquals(new BigDecimal("12.5"), value);
  }

  @Test
  public void trustedJavaClassIsReadWithSpecificDataConstructor() throws IOException {
    Schema schema = stringSchema(BigDecimal.class.getName());
    SpecificDatumReader<Object> reader = new SpecificDatumReader<>(data());
    reader.setSchema(schema);
    assertEquals(new BigDecimal("12.5"), read(reader, encodeString("12.5")));
  }

  @Test
  public void untrustedJavaClassIsRejected() throws IOException {
    Schema schema = stringSchema(NotTrusted.class.getName());
    try {
      Object value = read(reader(schema), encodeString("x"));
      fail("Expected SecurityException, read " + value.getClass().getName());
    } catch (SecurityException expected) {
      assertTrue(expected.getMessage().contains(NotTrusted.class.getName()));
    }
  }

  @Test
  public void untrustedJavaKeyClassIsRejected() throws IOException {
    Schema schema = Schema.createMap(Schema.create(Schema.Type.INT));
    schema.addProp(SpecificData.KEY_CLASS_PROP, NotTrusted.class.getName());
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
    encoder.writeMapStart();
    encoder.setItemCount(1);
    encoder.startItem();
    encoder.writeString("k");
    encoder.writeInt(1);
    encoder.writeMapEnd();
    encoder.flush();
    try {
      Object value = read(reader(schema), out.toByteArray());
      fail("Expected SecurityException, read " + value);
    } catch (SecurityException expected) {
      assertTrue(expected.getMessage().contains(NotTrusted.class.getName()));
    }
  }

  @Test
  public void javaClassWithoutPackageIsRejected() throws IOException {
    Schema schema = stringSchema("NoPackage");
    try {
      read(reader(schema), encodeString("x"));
      fail("Expected SecurityException");
    } catch (SecurityException expected) {
      assertTrue(expected.getMessage().contains("NoPackage"));
    }
  }
}
