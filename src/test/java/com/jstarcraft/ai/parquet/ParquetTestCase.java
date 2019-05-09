package com.jstarcraft.ai.parquet;

import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.util.Utf8;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RawLocalFileSystem;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.GroupFactory;
import org.apache.parquet.example.data.simple.SimpleGroupFactory;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.example.GroupReadSupport;
import org.apache.parquet.hadoop.example.GroupWriteSupport;
import org.apache.parquet.hadoop.util.HadoopInputFile;
import org.apache.parquet.hadoop.util.HadoopOutputFile;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.MessageTypeParser;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParquetTestCase {

    private Logger logger = LoggerFactory.getLogger(ParquetTestCase.class);

    private Path path = new Path("data.parquet");
    private Configuration configuration = new Configuration();
    private FileSystem fileSystem;
    {
        try {
            fileSystem = RawLocalFileSystem.get(configuration);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    @Test
    public void testReadWriteSimple() throws Exception {
        fileSystem.delete(path, true);

        MessageType schema = MessageTypeParser.parseMessageType("message parquet { required binary left (UTF8); required binary right (UTF8); }");

        {
            GroupFactory factory = new SimpleGroupFactory(schema);
            Group group = factory.newGroup().append("left", "Left").append("right", "Right");

            GroupWriteSupport writeSupport = new GroupWriteSupport();
            writeSupport.setSchema(schema, configuration);
            ParquetWriter<Group> writer = new ParquetWriter<Group>(path, configuration, writeSupport);
            writer.write(group);
            writer.close();
        }

        {
            GroupReadSupport readSupport = new GroupReadSupport();
            ParquetReader<Group> reader = new ParquetReader<Group>(path, readSupport);

            Group group = reader.read();
            Assert.assertEquals("Left", group.getString("left", 0));
            Assert.assertEquals("Right", group.getString("right", 0));
            reader.close();
        }
    }

    @Test
    public void testReadWriteComplex() throws Exception {
        fileSystem.delete(path, true);

        MessageType schema = MessageTypeParser.parseMessageType("message parquet { required binary title (UTF8); required binary when (UTF8); repeated group where { required float longitude; required float latitude; } }");

        {
            GroupFactory factory = new SimpleGroupFactory(schema);
            Group group = factory.newGroup().append("title", "title").append("when", "2020-01-01 00:00:00");
            Group where = group.addGroup("where");
            where.append("longitude", 0F);
            where.append("latitude", 0F);

            GroupWriteSupport writeSupport = new GroupWriteSupport();
            writeSupport.setSchema(schema, configuration);
            ParquetWriter<Group> writer = new ParquetWriter<Group>(path, configuration, writeSupport);
            writer.write(group);
            writer.close();
        }

        {
            GroupReadSupport readSupport = new GroupReadSupport();
            ParquetReader<Group> reader = new ParquetReader<Group>(path, readSupport);

            Group group = reader.read();
            Group where = group.getGroup("where", 0);
            Assert.assertEquals("title", group.getString("title", 0));
            Assert.assertEquals("2020-01-01 00:00:00", group.getString("when", 0));
            Assert.assertEquals(0F, where.getFloat("longitude", 0), 0F);
            Assert.assertEquals(0F, where.getFloat("latitude", 0), 0F);
            reader.close();
        }
    }

    @Test
    public void testReadWriteArvo() throws Exception {
        fileSystem.delete(path, true);

        Schema schema = SchemaBuilder.record("parquet").fields().requiredString("title").requiredString("when").name("where").type(SchemaBuilder.record("where").fields().requiredFloat("longitude").requiredFloat("latitude").endRecord()).noDefault().endRecord();

        {
            GenericRecord parquet = new GenericData.Record(schema);
            parquet.put("title", "title");
            parquet.put("when", "2020-01-01 00:00:00");
            GenericRecord where = new GenericData.Record(schema.getField("where").schema());
            where.put("longitude", 0F);
            where.put("latitude", 0F);
            parquet.put("where", where);

            HadoopOutputFile output = HadoopOutputFile.fromPath(path, configuration);
            ParquetWriter writer = AvroParquetWriter.builder(output).withSchema(schema).build();
            writer.write(parquet);
            writer.close();
        }

        {
            HadoopInputFile input = HadoopInputFile.fromPath(path, configuration);
            ParquetReader reader = AvroParquetReader.builder(input).build();
            GenericRecord parquet = (GenericRecord) reader.read();
            GenericRecord where = (GenericRecord) parquet.get("where");

            Assert.assertEquals("title", Utf8.class.cast(parquet.get("title")).toString());
            Assert.assertEquals("2020-01-01 00:00:00", Utf8.class.cast(parquet.get("when")).toString());
            Assert.assertEquals(0F, where.get("longitude"));
            Assert.assertEquals(0F, where.get("latitude"));
            reader.close();
        }
    }

}