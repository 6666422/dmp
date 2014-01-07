package com.b5m.pig.udf;

import com.b5m.couchbase.CouchbaseConfiguration;
import com.b5m.couchbase.CouchbaseOutputFormat;
import com.b5m.pig.JsonSerializer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.OutputFormat;
import org.apache.hadoop.mapreduce.RecordWriter;

import org.apache.pig.ResourceSchema;
import org.apache.pig.ResourceSchema.ResourceFieldSchema;
import org.apache.pig.StoreFunc;
import org.apache.pig.data.DataType;
import org.apache.pig.data.Tuple;
import org.apache.pig.impl.util.UDFContext;
import org.apache.pig.impl.util.Utils;

import java.io.IOException;
import java.util.Properties;

/**
 * Pig output function to Couchbase.
 *
 * @author Paolo D'Apice
 */
public final class CouchbaseStorage extends StoreFunc {

    private final static Log log = LogFactory.getLog(CouchbaseStorage.class);
    private final static String SCHEMA_PROPERTY = "pig.couchbasestorage.schema";

    private final CouchbaseConfiguration conf;

    private String udfcSignature = null;
    private RecordWriter<Text, Text> writer = null;
    private ResourceFieldSchema[] fields = null;
    private JsonSerializer jsonSerializer = null;

    public CouchbaseStorage(String uris, String bucket, String password, String batchSize) {
        conf = new CouchbaseConfiguration(uris, bucket, password, batchSize);
    }

    @Override
    public OutputFormat getOutputFormat() throws IOException {
        return new CouchbaseOutputFormat<Text, Text>(conf);
    }

    @Override
    public String relToAbsPathForStoreLocation(String location, Path curDir)
    throws IOException {
        // no relative/absolute path conversion required since we are storing into Couchbase
        return conf.toString();
    }

    @Override
    public void setStoreLocation(String location, Job job) throws IOException {
        // nothing to do because we are storing into Couchbase
        // the location specified by the store command is basically ignored
    }

    @Override
    public void setStoreFuncUDFContextSignature(String signature) {
        // store the signature so we can use it later
        udfcSignature = signature;
    }

    @Override
    public void checkSchema(ResourceSchema schema) throws IOException {
        if (log.isDebugEnabled()) log.debug("schema: " + schema);
        ResourceFieldSchema[] fields = schema.getFields();

        if (fields.length != 2) {
            String message = String.format("Expected input tuple of size 2, received %d",
                                           fields.length);
            throw new IOException(message);
        }

        if (fields[0].getType() != DataType.CHARARRAY) {
            String message = String.format("Expected first value to be chararray, received %s",
                                           DataType.findTypeName(fields[0].getType()));
            throw new IOException(message);
        }

        // store the schema in UDF context
        UDFContext udfc = UDFContext.getUDFContext();
        Properties p = udfc.getUDFProperties(getClass(), new String[]{ udfcSignature });
        p.setProperty(SCHEMA_PROPERTY, schema.toString());

        log.info("stored schema into UDF context: " + schema);
    }

    /*
     * Here the RecordWriter will actually be an instance of
     * CouchbaseRecordWriter<K,V> as returned by
     * CouchbaseOutputFormat#getRecordWriter().
     * Hence we can safely suppress this warning.
     */
    @SuppressWarnings("unchecked")
    @Override
    public void prepareToWrite(RecordWriter writer) throws IOException {
        this.writer = writer;

        UDFContext udfc = UDFContext.getUDFContext();
        Properties p = udfc.getUDFProperties(getClass(), new String[]{ udfcSignature });
        String strSchema = p.getProperty(SCHEMA_PROPERTY);
        if (strSchema == null) {
            throw new IOException("Could not find schema in UDF context");
        }

        ResourceSchema schema = new ResourceSchema(Utils.getSchemaFromString(strSchema));
        fields = schema.getFields();
        if (log.isDebugEnabled()) log.info("loaded schema into UDF context: " + schema);

        jsonSerializer = new JsonSerializer();
    }

    @Override
    public void putNext(Tuple tuple) throws IOException {
        try {
            String key = (String) tuple.get(0);
            String value = jsonSerializer.toJson(tuple, fields);

            if (log.isDebugEnabled()) log.debug("key=" + key +" value=" + value);

            writer.write(new Text(key), new Text(value));
        } catch (InterruptedException e) {
            log.error("Interrupted", e);
            throw new IOException(e);
        }
        System.out.println(tuple.toString());
    }

    @Override
    public void cleanupOnFailure(String location, Job job) throws IOException {
        // data already stored can be deleted directly on Couchbase
    }

    // for tests only
    CouchbaseStorage() {
        conf = null;
    }
}

