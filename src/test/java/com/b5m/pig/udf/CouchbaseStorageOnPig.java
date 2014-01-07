package com.b5m.pig.udf;

import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.apache.pig.pigunit.PigTest;
import org.apache.commons.io.FileUtils;

import org.codehaus.jackson.map.ObjectMapper;

import com.couchbase.client.CouchbaseClient;

import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class CouchbaseStorageOnPig {

    private final static String BUCKET = "default";
    private final static String PASSWORD = "";
    private final static String HOST = "http://127.0.0.1:8091/pools";

    private static CouchbaseClient client;
    private static List<String> expected;
    private static ObjectMapper mapper = new ObjectMapper();

    @BeforeClass
    public static void connect() throws Exception {
        List<URI> hosts = Arrays.asList(new URI(HOST));
        client = new CouchbaseClient(hosts, BUCKET, PASSWORD);
    }

    @BeforeClass
    private static void getExpected() throws Exception {
        expected = FileUtils.readLines(new File("src/test/data/couchbase-expected.txt"));
    }

    @Test
    public void test() throws Exception {
        String[] args = {
            "hosts=" + HOST,
            "bucket=" + BUCKET,
            "input=src/test/data/pig-output.txt",
        };

        PigTest test = new PigTest("src/test/pig/couchbaseStorage.pig", args);

        test.unoverride("STORE"); // enables write to couchbase
        test.runScript();

        for (String json : expected) {
            String key = parseKey(json);
            assertEquals(client.get(key), json);
        }
    }

    @AfterClass
    public static void cleanup() throws Exception {
        for (String json : expected) {
            String key = parseKey(json);
            assertTrue(client.delete(key).get());
        }
        client.shutdown();
    }

    private static String parseKey(String json) throws Exception {
        return mapper.readValue(json, Expected.class).uuid;
    }
}

class Expected {
    String uuid;
    Map<String, Integer> categories;

    void setUuid(String uuid) { this.uuid = uuid; }
    void setCategories(Map<String, Integer> categories) { this.categories = categories; }
}
