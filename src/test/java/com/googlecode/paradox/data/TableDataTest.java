package com.googlecode.paradox.data;

import java.sql.Driver;
import java.sql.DriverManager;
import java.util.ArrayList;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.googlecode.paradox.ParadoxConnection;
import com.googlecode.paradox.data.table.value.FieldValue;
import com.googlecode.paradox.metadata.ParadoxField;
import com.googlecode.paradox.metadata.ParadoxTable;
import com.googlecode.paradox.test.MainTest;

public class TableDataTest {

    private ParadoxConnection conn;

    @BeforeClass
    public static void setUp() throws Exception {
        Class.forName(Driver.class.getName());
    }

    @Before
    public void connect() throws Exception {
        conn = (ParadoxConnection) DriverManager.getConnection(MainTest.CONNECTION_STRING + "db");
    }

    @After
    public void closeConnection() throws Exception {
        if (conn != null) {
            conn.close();
        }
    }

    @Test
    public void testLoadAreaCodes() throws Exception {
        final ParadoxTable table = TableData.listTables(conn, "areacodes.db").get(0);
        final ArrayList<ArrayList<FieldValue>> data = TableData.loadData(conn, table, table.getFields());
        Assert.assertEquals(table.getRowCount(), data.size());
    }

    @Test
    public void testLoadContacts() throws Exception {
        final ParadoxTable table = TableData.listTables(conn, "contacts.db").get(0);
        final ArrayList<ParadoxField> fields = new ArrayList<ParadoxField>();
        fields.add(table.getFields().get(0));
        TableData.loadData(conn, table, fields);
    }

    @Test
    public void testLoadCustomer() throws Exception {
        final ParadoxTable table = TableData.listTables(conn, "customer.db").get(0);
        final ArrayList<ParadoxField> fields = new ArrayList<ParadoxField>();
        fields.add(table.getFields().get(0));
        TableData.loadData(conn, table, fields);
    }

    @Test
    public void testLoadHercules() throws Exception {
        final ParadoxTable table = TableData.listTables(conn, "hercules.db").get(0);
        TableData.loadData(conn, table, table.getFields());
    }

    @Test
    public void testLoadOrders() throws Exception {
        loadTable("orders.db");
    }
    
//    @Test
//    public void testUnclaimedFunds() throws Exception {
//        loadTable("ucfupload.db");
//    }

    @Test
    public void testLoadServer() throws Exception {
        final ParadoxTable table = TableData.listTables(conn, "server.db").get(0);
        final ArrayList<ParadoxField> fields = new ArrayList<ParadoxField>();
        fields.add(table.getFields().get(0));
        TableData.loadData(conn, table, fields);
    }
    
    private void loadTable(String tableName) throws Exception {
        final ParadoxTable table = TableData.listTables(conn, tableName).get(0);
        final ArrayList<ParadoxField> fields = new ArrayList<ParadoxField>();
        fields.addAll(table.getFields());
        System.out.println(tableName);
        System.out.print("Fields: ");
        for (ParadoxField field : fields) {
            System.out.print(field.getName() + " ");
        }
        System.out.println();
        fields.add(table.getFields().get(0));
        ArrayList<ArrayList<FieldValue>> fieldValues =TableData.loadData(conn, table, fields);
        for (int i = 0; i < fieldValues.size(); i++) {
            for (FieldValue fieldValue : fieldValues.get(i)) {
                System.out.print(fieldValue.getValue() + " ");
                
            }
            System.out.println();
        }
    }
}
