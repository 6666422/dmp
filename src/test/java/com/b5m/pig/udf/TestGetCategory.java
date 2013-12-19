package com.b5m.pig.udf;

import static org.testng.Assert.*;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.apache.pig.data.Tuple;
import org.apache.pig.data.TupleFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;

public class TestGetCategory {

    private final TupleFactory tupleFactory = TupleFactory.getInstance();
    private final GetCategory func;

    public TestGetCategory() throws IOException {
        URL url =  getClass().getResource("/Model.txt");
        File file = new File(url.getFile());
        func = new GetCategory(file);
    }

    @DataProvider(name="titles")
    public Object[][] titles() {
        return new Object[][] {
            // input title, output category
            { "蔻玲2013冬新款女狐狸毛领羊绒呢子短款大衣寇玲原价1999专柜正品", "服装服饰" },
            { "深部条带煤柱长期稳定性基础实验研究 正版包邮", "图书音像" },
            // TODO { "", "" },
            // TODO { null, "" },
        };
    }

    @Test(dataProvider="titles")
    public void test(String title, String category) throws IOException {
        Tuple tuple = tupleFactory.newTuple(title);
        String output = func.exec(tuple);
        assertEquals(output, category);
    }

}

