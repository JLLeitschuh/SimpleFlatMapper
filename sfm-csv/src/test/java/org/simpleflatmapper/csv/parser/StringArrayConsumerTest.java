package org.simpleflatmapper.csv.parser;

import org.junit.Test;
import org.simpleflatmapper.util.CheckedConsumer;

import static org.junit.Assert.assertEquals;

public class StringArrayConsumerTest {

    static class MyCheckedConsumer implements CheckedConsumer<String[]> {

        private String[] strings;

        @Override
        public void accept(String[] strings) throws Exception {
            this.strings = strings;
        }
    }
    @Test
    public void testNewCell() throws Exception {
        StringArrayConsumer<MyCheckedConsumer> consumer = StringArrayConsumer.<MyCheckedConsumer>newInstance(new MyCheckedConsumer());
        for(int i = 0; i < 20; i ++) {
            char[] chars = Integer.toString(i).toCharArray();
            consumer.newCell(chars, 0 , chars.length);
        }

        consumer.end();

        String[] str = consumer.handler().strings;
        assertEquals(20, str.length);
        for(int i = 0; i < 20; i ++) {
            assertEquals(Integer.toString(i), str[i]);
        }
    }
}