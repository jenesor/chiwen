package com.databps.bigdaf.chiwen;

import java.io.IOException;
import java.net.ServerSocket;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.NamespaceDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.protobuf.generated.HBaseProtos;
import org.apache.hadoop.hbase.protobuf.generated.HBaseProtos.SnapshotDescription;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.security.UserGroupInformation;
import org.junit.Assert;
import org.junit.Test;
import org.apache.hadoop.hbase.HBaseTestingUtility;

/**
 * @author merlin
 * @create 2017-11-04 下午3:50
 */
public class HBaseChiWenAuthorizationTest {
  private static final Log LOG = LogFactory.getLog(HBaseChiWenAuthorizationTest.class.getName());

  private static int port=2181;

  private static HBaseTestingUtility utility;


  @org.junit.BeforeClass
  public static void setup() throws Exception {
    port = getFreePort();

    utility = new HBaseTestingUtility();
    utility.getConfiguration().set("test.hbase.zookeeper.property.clientPort", "" + port);
    utility.getConfiguration().set("hbase.master.port", "" + getFreePort());
    utility.getConfiguration().set("hbase.master.info.port", "" + getFreePort());
    utility.getConfiguration().set("hbase.regionserver.port", "" + getFreePort());
    utility.getConfiguration().set("hbase.regionserver.info.port", "" + getFreePort());
    utility.getConfiguration().set("zookeeper.znode.parent", "/hbase-unsecure");

    // Enable authorization
    utility.getConfiguration().set("hbase.security.authorization", "true");
    utility.getConfiguration().set("hbase.coprocessor.master.classes",
        "com.databps.bigdaf.chiwen.plugin.ChiWenAuthorizationCoprocessor");
    utility.getConfiguration().set("hbase.coprocessor.region.classes",
        "com.databps.bigdaf.chiwen.plugin.ChiWenAuthorizationCoprocessor");

    utility.startMiniCluster();

    // Create a table as "admin"
    final Configuration conf = HBaseConfiguration.create();
    conf.set("hbase.zookeeper.quorum", "localhost");
    conf.set("hbase.zookeeper.property.clientPort", "" + port);
    conf.set("zookeeper.znode.parent", "/hbase-unsecure");

    // Create a table
    Connection conn = ConnectionFactory.createConnection(conf);
    Admin admin = conn.getAdmin();

    // Create a table
    if (!admin.tableExists(TableName.valueOf("temp"))) {
      HTableDescriptor tableDescriptor = new HTableDescriptor(TableName.valueOf("temp"));

      // Adding column families to table descriptor
      tableDescriptor.addFamily(new HColumnDescriptor("colfam1"));
      tableDescriptor.addFamily(new HColumnDescriptor("colfam2"));

      admin.createTable(tableDescriptor);
    }

    // Add a new row
    Put put = new Put(Bytes.toBytes("row1"));
    put.addColumn(Bytes.toBytes("colfam1"), Bytes.toBytes("col1"), Bytes.toBytes("val1"));
    Table table = conn.getTable(TableName.valueOf("temp"));
    table.put(put);

    put = new Put(Bytes.toBytes("row1"));
    put.addColumn(Bytes.toBytes("colfam2"), Bytes.toBytes("col1"), Bytes.toBytes("val2"));
    table.put(put);

    // Create a namespace
    NamespaceDescriptor ns = NamespaceDescriptor.create("test_namespace").build();
    admin.createNamespace(ns);

    // Create a table
    if (!admin.tableExists(TableName.valueOf("test_namespace", "temp"))) {
      HTableDescriptor tableDescriptor = new HTableDescriptor(TableName.valueOf("test_namespace", "temp"));

      // Adding column families to table descriptor
      tableDescriptor.addFamily(new HColumnDescriptor("colfam1"));
      tableDescriptor.addFamily(new HColumnDescriptor("colfam2"));

      admin.createTable(tableDescriptor);
    }

    table = conn.getTable(TableName.valueOf("test_namespace", "temp"));

    // Add a new row
    put = new Put(Bytes.toBytes("row1"));
    put.addColumn(Bytes.toBytes("colfam1"), Bytes.toBytes("col1"), Bytes.toBytes("val1"));
    table.put(put);

    put = new Put(Bytes.toBytes("row1"));
    put.addColumn(Bytes.toBytes("colfam2"), Bytes.toBytes("col1"), Bytes.toBytes("val2"));
    table.put(put);

    conn.close();
  }

  @org.junit.AfterClass
  public static void cleanup() throws Exception {
    utility.shutdownMiniCluster();
  }

  @Test
  public void testReadTablesAsProcessOwner() throws Exception {
    final Configuration conf = HBaseConfiguration.create();
    conf.set("hbase.zookeeper.quorum", "localhost");
    conf.set("hbase.zookeeper.property.clientPort", "" + port);
    conf.set("zookeeper.znode.parent", "/hbase-unsecure");

    Connection conn = ConnectionFactory.createConnection(conf);
    Admin admin = conn.getAdmin();

    HTableDescriptor[] tableDescriptors = admin.listTables();
    for (HTableDescriptor desc : tableDescriptors) {
      LOG.info("Found table:[" + desc.getTableName().getNameAsString() + "]");
    }
    Assert.assertEquals(2, tableDescriptors.length);

    conn.close();
  }

  // This should fail as the "IT" group only has read privileges, not admin privileges, on the table "temp"
  @Test
  public void testReadTablesAsGroupIT() throws Exception {
    final Configuration conf = HBaseConfiguration.create();
    conf.set("hbase.zookeeper.quorum", "localhost");
    conf.set("hbase.zookeeper.property.clientPort", "" + port);
    conf.set("zookeeper.znode.parent", "/hbase-unsecure");

    String user = "IT";

    UserGroupInformation ugi = UserGroupInformation.createUserForTesting(user, new String[] {"IT"});
    ugi.doAs(new PrivilegedExceptionAction<Void>() {
      public Void run() throws Exception {
        Connection conn = ConnectionFactory.createConnection(conf);
        Admin admin = conn.getAdmin();

        HTableDescriptor[] tableDescriptors = admin.listTables();
        for (HTableDescriptor desc : tableDescriptors) {
          LOG.info("Found table:[" + desc.getTableName().getNameAsString() + "]");
        }
        Assert.assertEquals(0, tableDescriptors.length);

        conn.close();
        return null;
      }
    });
  }

  @Test
  public void testCreateAndDropTables() throws Exception {
    final Configuration conf = HBaseConfiguration.create();
    conf.set("hbase.zookeeper.quorum", "localhost");
    conf.set("hbase.zookeeper.property.clientPort", "" + port);
    conf.set("zookeeper.znode.parent", "/hbase-unsecure");

    Connection conn = ConnectionFactory.createConnection(conf);
    Admin admin = conn.getAdmin();

    // Create a new table as process owner
    HTableDescriptor tableDescriptor = new HTableDescriptor(TableName.valueOf("temp2"));

    // Adding column families to table descriptor
    tableDescriptor.addFamily(new HColumnDescriptor("colfam1"));
    tableDescriptor.addFamily(new HColumnDescriptor("colfam2"));

    admin.createTable(tableDescriptor);

    conn.close();

    // Try to disable + delete the table as the "IT" group
    String user = "IT";

    UserGroupInformation ugi = UserGroupInformation.createUserForTesting(user, new String[] {"IT"});
    ugi.doAs(new PrivilegedExceptionAction<Void>() {
      public Void run() throws Exception {
        Connection conn = ConnectionFactory.createConnection(conf);
        Admin admin = conn.getAdmin();

        try {
          admin.disableTable(TableName.valueOf("temp2"));
          admin.deleteTable(TableName.valueOf("temp2"));
          Assert.fail("Failure expected on an unauthorized user");
        } catch (IOException ex) {
          // expected
        }

        conn.close();
        return null;
      }
    });

    // Now disable and delete as process owner
    conn = ConnectionFactory.createConnection(conf);
    admin = conn.getAdmin();
    admin.disableTable(TableName.valueOf("temp2"));
    admin.deleteTable(TableName.valueOf("temp2"));

    conn.close();
  }

  @Test
  public void testReadRowAsProcessOwner() throws Exception {
    final Configuration conf = HBaseConfiguration.create();
    conf.set("hbase.zookeeper.quorum", "localhost");
    conf.set("hbase.zookeeper.property.clientPort", "" + port);
    conf.set("zookeeper.znode.parent", "/hbase-unsecure");

    Connection conn = ConnectionFactory.createConnection(conf);
    Table table = conn.getTable(TableName.valueOf("temp"));

    // Read a row
    Get get = new Get(Bytes.toBytes("row1"));
    Result result = table.get(get);
    byte[] valResult = result.getValue(Bytes.toBytes("colfam1"), Bytes.toBytes("col1"));
    Assert.assertTrue(Arrays.equals(valResult, Bytes.toBytes("val1")));

    conn.close();
  }

  @Test
  public void testReadRowAsGroupIT() throws Exception {
    final Configuration conf = HBaseConfiguration.create();
    conf.set("hbase.zookeeper.quorum", "localhost");
    conf.set("hbase.zookeeper.property.clientPort", "" + port);
    conf.set("zookeeper.znode.parent", "/hbase-unsecure");

    String user = "IT";

    UserGroupInformation ugi = UserGroupInformation.createUserForTesting(user, new String[] {"IT"});
    ugi.doAs(new PrivilegedExceptionAction<Void>() {
      public Void run() throws Exception {
        Connection conn = ConnectionFactory.createConnection(conf);
        Table table = conn.getTable(TableName.valueOf("temp"));

        // Read a row
        Get get = new Get(Bytes.toBytes("row1"));
        Result result = table.get(get);
        byte[] valResult = result.getValue(Bytes.toBytes("colfam1"), Bytes.toBytes("col1"));
        Assert.assertTrue(Arrays.equals(valResult, Bytes.toBytes("val1")));

        conn.close();
        return null;
      }
    });
  }

  // This should fail as "public" doesn't have the right to read the table
  @Test
  public void testReadRowAsGroupPublic() throws Exception {
    final Configuration conf = HBaseConfiguration.create();
    conf.set("hbase.zookeeper.quorum", "localhost");
    conf.set("hbase.zookeeper.property.clientPort", "" + port);
    conf.set("zookeeper.znode.parent", "/hbase-unsecure");

    String user = "public";

    UserGroupInformation ugi = UserGroupInformation.createUserForTesting(user, new String[] {"public"});
    ugi.doAs(new PrivilegedExceptionAction<Void>() {
      public Void run() throws Exception {
        Connection conn = ConnectionFactory.createConnection(conf);
        Table table = conn.getTable(TableName.valueOf("temp"));

        // Read a row
        try {
          Get get = new Get(Bytes.toBytes("row1"));
          table.get(get);
          Assert.fail("Failure expected on an unauthorized user");
        } catch (IOException ex) {
          // expected
        }

        conn.close();
        return null;
      }
    });
  }

  @Test
  public void testReadRowFromColFam2AsProcessOwner() throws Exception {
    final Configuration conf = HBaseConfiguration.create();
    conf.set("hbase.zookeeper.quorum", "localhost");
    conf.set("hbase.zookeeper.property.clientPort", "" + port);
    conf.set("zookeeper.znode.parent", "/hbase-unsecure");

    Connection conn = ConnectionFactory.createConnection(conf);
    Table table = conn.getTable(TableName.valueOf("temp"));

    // Read a row
    Get get = new Get(Bytes.toBytes("row1"));
    Result result = table.get(get);
    byte[] valResult = result.getValue(Bytes.toBytes("colfam2"), Bytes.toBytes("col1"));
    Assert.assertTrue(Arrays.equals(valResult, Bytes.toBytes("val2")));

    conn.close();
  }

  @Test
  public void testReadRowFromColFam2AsGroupIT() throws Exception {
    final Configuration conf = HBaseConfiguration.create();
    conf.set("hbase.zookeeper.quorum", "localhost");
    conf.set("hbase.zookeeper.property.clientPort", "" + port);
    conf.set("zookeeper.znode.parent", "/hbase-unsecure");

    String user = "public";

    UserGroupInformation ugi = UserGroupInformation.createUserForTesting(user, new String[] {"IT"});
    ugi.doAs(new PrivilegedExceptionAction<Void>() {
      public Void run() throws Exception {
        Connection conn = ConnectionFactory.createConnection(conf);
        Table table = conn.getTable(TableName.valueOf("temp"));

        // Read a row
        Get get = new Get(Bytes.toBytes("row1"));
        Result result = table.get(get);
        byte[] valResult = result.getValue(Bytes.toBytes("colfam2"), Bytes.toBytes("col1"));
        Assert.assertNull(valResult);

        conn.close();
        return null;
      }
    });
  }

  @Test
  public void testWriteRowAsProcessOwner() throws Exception {
    final Configuration conf = HBaseConfiguration.create();
    conf.set("hbase.zookeeper.quorum", "localhost");
    conf.set("hbase.zookeeper.property.clientPort", "" + port);
    conf.set("zookeeper.znode.parent", "/hbase-unsecure");

    Connection conn = ConnectionFactory.createConnection(conf);
    Table table = conn.getTable(TableName.valueOf("temp"));

    // Add a new row
    Put put = new Put(Bytes.toBytes("row2"));
    put.addColumn(Bytes.toBytes("colfam1"), Bytes.toBytes("col1"), Bytes.toBytes("val2"));
    table.put(put);

    conn.close();
  }

  @Test
  public void testWriteRowAsGroupIT() throws Exception {
    final Configuration conf = HBaseConfiguration.create();
    conf.set("hbase.zookeeper.quorum", "localhost");
    conf.set("hbase.zookeeper.property.clientPort", "" + port);
    conf.set("zookeeper.znode.parent", "/hbase-unsecure");

    String user = "IT";

    UserGroupInformation ugi = UserGroupInformation.createUserForTesting(user, new String[] {"IT"});
    ugi.doAs(new PrivilegedExceptionAction<Void>() {
      public Void run() throws Exception {
        Connection conn = ConnectionFactory.createConnection(conf);
        Table table = conn.getTable(TableName.valueOf("temp"));

        // Add a new row
        Put put = new Put(Bytes.toBytes("row3"));
        put.addColumn(Bytes.toBytes("colfam1"), Bytes.toBytes("col1"), Bytes.toBytes("val2"));
        table.put(put);

        conn.close();
        return null;
      }
    });
  }

  @Test
  public void testWriteRowAsGroupPublic() throws Exception {
    final Configuration conf = HBaseConfiguration.create();
    conf.set("hbase.zookeeper.quorum", "localhost");
    conf.set("hbase.zookeeper.property.clientPort", "" + port);
    conf.set("zookeeper.znode.parent", "/hbase-unsecure");

    String user = "public";

    UserGroupInformation ugi = UserGroupInformation.createUserForTesting(user, new String[] {"public"});
    ugi.doAs(new PrivilegedExceptionAction<Void>() {
      public Void run() throws Exception {
        Connection conn = ConnectionFactory.createConnection(conf);
        Table table = conn.getTable(TableName.valueOf("temp"));

        // Add a new row
        try {
          Put put = new Put(Bytes.toBytes("row3"));
          put.addColumn(Bytes.toBytes("colfam1"), Bytes.toBytes("col1"), Bytes.toBytes("val2"));
          table.put(put);
          Assert.fail("Failure expected on an unauthorized user");
        } catch (IOException ex) {
          // expected
        }

        conn.close();
        return null;
      }
    });
  }

  @Test
  public void testWriteRowInColFam2AsGroupIT() throws Exception {
    final Configuration conf = HBaseConfiguration.create();
    conf.set("hbase.zookeeper.quorum", "localhost");
    conf.set("hbase.zookeeper.property.clientPort", "" + port);
    conf.set("zookeeper.znode.parent", "/hbase-unsecure");

    String user = "IT";

    UserGroupInformation ugi = UserGroupInformation.createUserForTesting(user, new String[] {"IT"});
    ugi.doAs(new PrivilegedExceptionAction<Void>() {
      public Void run() throws Exception {
        Connection conn = ConnectionFactory.createConnection(conf);
        Table table = conn.getTable(TableName.valueOf("temp"));

        // Add a new row
        try {
          Put put = new Put(Bytes.toBytes("row3"));
          put.addColumn(Bytes.toBytes("colfam2"), Bytes.toBytes("col1"), Bytes.toBytes("val2"));
          table.put(put);
          Assert.fail("Failure expected on an unauthorized user");
        } catch (IOException ex) {
          // expected
        }

        conn.close();
        return null;
      }
    });
  }

  @Test
  public void testReadRowInAnotherTable() throws Exception {
    final Configuration conf = HBaseConfiguration.create();
    conf.set("hbase.zookeeper.quorum", "localhost");
    conf.set("hbase.zookeeper.property.clientPort", "" + port);
    conf.set("zookeeper.znode.parent", "/hbase-unsecure");

    Connection conn = ConnectionFactory.createConnection(conf);
    Admin admin = conn.getAdmin();

    // Create a new table as process owner
    HTableDescriptor tableDescriptor = new HTableDescriptor(TableName.valueOf("temp4"));

    // Adding column families to table descriptor
    tableDescriptor.addFamily(new HColumnDescriptor("colfam1"));
    tableDescriptor.addFamily(new HColumnDescriptor("colfam2"));

    admin.createTable(tableDescriptor);

    // Write a value
    Put put = new Put(Bytes.toBytes("row1"));
    put.addColumn(Bytes.toBytes("colfam1"), Bytes.toBytes("col1"), Bytes.toBytes("val1"));
    Table table = conn.getTable(TableName.valueOf("temp4"));
    table.put(put);

    // Read a row
    Get get = new Get(Bytes.toBytes("row1"));
    Result result = table.get(get);
    byte[] valResult = result.getValue(Bytes.toBytes("colfam2"), Bytes.toBytes("col1"));
    Assert.assertNull(valResult);

    conn.close();

    // Now try to read the row as group "IT" - it should fail as "IT" can only read from table "temp"
    String user = "IT";

    UserGroupInformation ugi = UserGroupInformation.createUserForTesting(user, new String[] {"IT"});
    ugi.doAs(new PrivilegedExceptionAction<Void>() {
      public Void run() throws Exception {
        Connection conn = ConnectionFactory.createConnection(conf);
        Table table = conn.getTable(TableName.valueOf("temp4"));

        // Read a row
        try {
          Get get = new Get(Bytes.toBytes("row1"));
          table.get(get);
          Assert.fail("Failure expected on an unauthorized user");
        } catch (IOException ex) {
          // expected
        }

        conn.close();
        return null;
      }
    });

    // Now disable and delete as process owner
    conn = ConnectionFactory.createConnection(conf);
    admin = conn.getAdmin();
    admin.disableTable(TableName.valueOf("temp4"));
    admin.deleteTable(TableName.valueOf("temp4"));

    conn.close();
  }

  @Test
  public void testDeleteRowAsProcessOwner() throws Exception {
    final Configuration conf = HBaseConfiguration.create();
    conf.set("hbase.zookeeper.quorum", "localhost");
    conf.set("hbase.zookeeper.property.clientPort", "" + port);
    conf.set("zookeeper.znode.parent", "/hbase-unsecure");

    Connection conn = ConnectionFactory.createConnection(conf);
    Table table = conn.getTable(TableName.valueOf("temp"));

    // Add a new row
    Put put = new Put(Bytes.toBytes("row4"));
    put.addColumn(Bytes.toBytes("colfam1"), Bytes.toBytes("col1"), Bytes.toBytes("val2"));
    table.put(put);

    // Delete the new row
    Delete delete = new Delete(Bytes.toBytes("row4"));
    table.delete(delete);

    conn.close();
  }

  @Test
  public void testDeleteRowAsGroupIT() throws Exception {
    final Configuration conf = HBaseConfiguration.create();
    conf.set("hbase.zookeeper.quorum", "localhost");
    conf.set("hbase.zookeeper.property.clientPort", "" + port);
    conf.set("zookeeper.znode.parent", "/hbase-unsecure");

    Connection conn = ConnectionFactory.createConnection(conf);
    Table table = conn.getTable(TableName.valueOf("temp"));

    // Add a new row (as process owner)
    Put put = new Put(Bytes.toBytes("row5"));
    put.addColumn(Bytes.toBytes("colfam1"), Bytes.toBytes("col1"), Bytes.toBytes("val2"));
    table.put(put);

    String user = "IT";

    UserGroupInformation ugi = UserGroupInformation.createUserForTesting(user, new String[] {"IT"});
    ugi.doAs(new PrivilegedExceptionAction<Void>() {
      public Void run() throws Exception {
        Connection conn = ConnectionFactory.createConnection(conf);
        Table table = conn.getTable(TableName.valueOf("temp"));

        try {
          // Delete the new row
          Delete delete = new Delete(Bytes.toBytes("row5"));
          table.delete(delete);
          Assert.fail("Failure expected on an unauthorized user");
        } catch (IOException ex) {
          // expected
        }

        conn.close();
        return null;
      }
    });

    // Delete the new row (as process owner)
    Delete delete = new Delete(Bytes.toBytes("row5"));
    table.delete(delete);

    conn.close();
  }

  @Test
  public void testCloneSnapshotAsGroupQA() throws Exception {
    final Configuration conf = HBaseConfiguration.create();
    conf.set("hbase.zookeeper.quorum", "localhost");
    conf.set("hbase.zookeeper.property.clientPort", "" + port);
    conf.set("zookeeper.znode.parent", "/hbase-unsecure");

    Connection conn = ConnectionFactory.createConnection(conf);
    Admin admin = conn.getAdmin();

    List<SnapshotDescription> snapshots = admin.listSnapshots("test_snapshot");
    if (CollectionUtils.isNotEmpty(snapshots)) {
      admin.deleteSnapshot("test_snapshot");
    }
    String user = "QA";

    UserGroupInformation ugi = UserGroupInformation.createUserForTesting(user, new String[]{"QA"});

    ugi.doAs(new PrivilegedExceptionAction<Void>() {
      public Void run() throws Exception {
        Connection conn = ConnectionFactory.createConnection(conf);
        Admin admin = conn.getAdmin();
        Table table = conn.getTable(TableName.valueOf("test_namespace", "temp"));
        TableName tableName = table.getName();

        admin.disableTable(tableName);

        // Create a snapshot
        admin.snapshot("test_snapshot", tableName);

        // Clone snapshot
        HTableDescriptor tableDescriptor = new HTableDescriptor(TableName.valueOf("test_namespace", "temp_cloned"));
        TableName newTableName = tableDescriptor.getTableName();
        admin.cloneSnapshot("test_snapshot", newTableName);
        admin.disableTable(newTableName);
        admin.deleteTable(newTableName);

        admin.enableTable(tableName);

        conn.close();
        return null;
      }
    });

    snapshots = admin.listSnapshots("test_snapshot");
    if (CollectionUtils.isNotEmpty(snapshots)) {
      admin.deleteSnapshot("test_snapshot");
    }
  }

  @Test
  public void testCloneSnapshotAsNonQAGroup() throws Exception {
    final Configuration conf = HBaseConfiguration.create();
    conf.set("hbase.zookeeper.quorum", "localhost");
    conf.set("hbase.zookeeper.property.clientPort", "" + port);
    conf.set("zookeeper.znode.parent", "/hbase-unsecure");

    Connection conn = ConnectionFactory.createConnection(conf);
    Admin admin = conn.getAdmin();
    TableName tableName = conn.getTable(TableName.valueOf("test_namespace", "temp")).getName();

    admin.disableTable(tableName);

    // Create a snapshot
    List<HBaseProtos.SnapshotDescription> snapshots = admin.listSnapshots("test_snapshot");
    if (CollectionUtils.isEmpty(snapshots)) {
      admin.snapshot("test_snapshot", tableName);
    }

    admin.enableTable(tableName);

    String user = "public";

    UserGroupInformation ugi = UserGroupInformation.createUserForTesting(user, new String[] {"public"});

    ugi.doAs(new PrivilegedExceptionAction<Void>() {
      public Void run() throws Exception {
        Connection conn = ConnectionFactory.createConnection(conf);
        Admin admin = conn.getAdmin();

        try {
          TableName clone = TableName.valueOf("test_namespace", "temp_cloned_public");
          if (admin.tableExists(clone)) {
            // Delete it
            admin.deleteTable(clone);
          }
          // Clone snapshot
          admin.cloneSnapshot("test_snapshot", clone);
          Assert.fail("Failure expected on an unauthorized group public");
        } catch(Exception e) {
          // Expected
        }
        conn.close();
        return null;
      }
    });
    TableName clone = TableName.valueOf("test_namespace", "temp_cloned_public");

    if (admin.tableExists(clone)) {
      admin.deleteTable(clone);
    }
    admin.deleteSnapshot("test_snapshot");
  }

  @Test
  public void testTagBasedTablePolicy() throws Exception {
    final Configuration conf = HBaseConfiguration.create();
    conf.set("hbase.zookeeper.quorum", "localhost");
    conf.set("hbase.zookeeper.property.clientPort", "" + port);
    conf.set("zookeeper.znode.parent", "/hbase-unsecure");

    final HTableDescriptor tableDescriptor = new HTableDescriptor(TableName.valueOf("temp3"));

    // Adding column families to table descriptor
    tableDescriptor.addFamily(new HColumnDescriptor("colfam1"));
    tableDescriptor.addFamily(new HColumnDescriptor("colfam2"));

    // Try to create a "temp3" table as the "IT" group - this should fail
    String user = "IT";

    // Try to create the table as the "IT" group - this should fail
    UserGroupInformation ugi = UserGroupInformation.createUserForTesting(user, new String[] {"IT"});
    ugi.doAs(new PrivilegedExceptionAction<Void>() {
      public Void run() throws Exception {
        Connection conn = ConnectionFactory.createConnection(conf);
        Admin admin = conn.getAdmin();

        try {
          admin.createTable(tableDescriptor);
          Assert.fail("Failure expected on an unauthorized user");
        } catch (IOException ex) {
          // expected
        }

        conn.close();
        return null;
      }
    });

    // Now try to create the table as the "dev" group - this should work
    ugi = UserGroupInformation.createUserForTesting("dev", new String[] {"dev"});
    ugi.doAs(new PrivilegedExceptionAction<Void>() {
      public Void run() throws Exception {
        Connection conn = ConnectionFactory.createConnection(conf);
        Admin admin = conn.getAdmin();

        admin.createTable(tableDescriptor);

        conn.close();
        return null;
      }
    });

    // Drop the table
    Connection conn = ConnectionFactory.createConnection(conf);
    Admin admin = conn.getAdmin();

    admin.disableTable(TableName.valueOf("temp3"));
    admin.deleteTable(TableName.valueOf("temp3"));

    conn.close();
  }

  @Test
  public void testTagBasedColumnFamilyPolicy() throws Exception {
    final Configuration conf = HBaseConfiguration.create();
    conf.set("hbase.zookeeper.quorum", "localhost");
    conf.set("hbase.zookeeper.property.clientPort", "" + port);
    conf.set("zookeeper.znode.parent", "/hbase-unsecure");

    // Create a new table as process owner
    final HTableDescriptor tableDescriptor = new HTableDescriptor(TableName.valueOf("temp3"));

    // Adding column families to table descriptor
    tableDescriptor.addFamily(new HColumnDescriptor("colfam1"));
    tableDescriptor.addFamily(new HColumnDescriptor("colfam2"));

    Connection conn = ConnectionFactory.createConnection(conf);
    Admin admin = conn.getAdmin();

    admin.createTable(tableDescriptor);

    // Add a new row
    Put put = new Put(Bytes.toBytes("row1"));
    put.addColumn(Bytes.toBytes("colfam1"), Bytes.toBytes("col1"), Bytes.toBytes("val1"));
    Table table = conn.getTable(TableName.valueOf("temp3"));
    table.put(put);

    put = new Put(Bytes.toBytes("row1"));
    put.addColumn(Bytes.toBytes("colfam2"), Bytes.toBytes("col1"), Bytes.toBytes("val2"));
    table.put(put);

    conn.close();

    String user = "dev";
    UserGroupInformation ugi = UserGroupInformation.createUserForTesting(user, new String[] {"dev"});
    ugi.doAs(new PrivilegedExceptionAction<Void>() {
      public Void run() throws Exception {
        Connection conn = ConnectionFactory.createConnection(conf);
        Table table = conn.getTable(TableName.valueOf("temp3"));

        // Try to read the "colfam1" of the "temp3" table as the "dev" group - this should work
        Get get = new Get(Bytes.toBytes("row1"));
        Result result = table.get(get);
        byte[] valResult = result.getValue(Bytes.toBytes("colfam1"), Bytes.toBytes("col1"));
        Assert.assertTrue(Arrays.equals(valResult, Bytes.toBytes("val1")));

        // Now try to read the "colfam2" column family of the temp3 table - this should fail
        get = new Get(Bytes.toBytes("row1"));
        result = table.get(get);
        valResult = result.getValue(Bytes.toBytes("colfam2"), Bytes.toBytes("col1"));
        Assert.assertNull(valResult);

        conn.close();
        return null;
      }
    });

    // Now try to read colfam1 as the "IT" group - this should fail
    ugi = UserGroupInformation.createUserForTesting("IT", new String[] {"IT"});
    ugi.doAs(new PrivilegedExceptionAction<Void>() {
      public Void run() throws Exception {
        Connection conn = ConnectionFactory.createConnection(conf);
        Table table = conn.getTable(TableName.valueOf("temp3"));

        Get get = new Get(Bytes.toBytes("row1"));
        try {
          table.get(get);
          Assert.fail("Failure expected on an unauthorized user");
        } catch (IOException ex) {
          // expected
        }

        return null;
      }
    });

    // Drop the table
    conn = ConnectionFactory.createConnection(conf);
    admin = conn.getAdmin();

    admin.disableTable(TableName.valueOf("temp3"));
    admin.deleteTable(TableName.valueOf("temp3"));

    conn.close();
  }

  @Test
  public void testTagBasedColumnPolicy() throws Exception {
    final Configuration conf = HBaseConfiguration.create();
    conf.set("hbase.zookeeper.quorum", "localhost");
    conf.set("hbase.zookeeper.property.clientPort", "" + port);
    conf.set("zookeeper.znode.parent", "/hbase-unsecure");

    // Create a new table as process owner
    final HTableDescriptor tableDescriptor = new HTableDescriptor(TableName.valueOf("temp3"));

    // Adding column families to table descriptor
    tableDescriptor.addFamily(new HColumnDescriptor("colfam1"));
    tableDescriptor.addFamily(new HColumnDescriptor("colfam2"));

    Connection conn = ConnectionFactory.createConnection(conf);
    Admin admin = conn.getAdmin();

    admin.createTable(tableDescriptor);

    // Add a new row
    Put put = new Put(Bytes.toBytes("row1"));
    put.addColumn(Bytes.toBytes("colfam1"), Bytes.toBytes("col1"), Bytes.toBytes("val1"));
    Table table = conn.getTable(TableName.valueOf("temp3"));
    table.put(put);

    put = new Put(Bytes.toBytes("row1"));
    put.addColumn(Bytes.toBytes("colfam2"), Bytes.toBytes("col1"), Bytes.toBytes("val2"));
    table.put(put);

    conn.close();

    String user = "dev";
    UserGroupInformation ugi = UserGroupInformation.createUserForTesting(user, new String[] {"dev"});
    ugi.doAs(new PrivilegedExceptionAction<Void>() {
      public Void run() throws Exception {
        Connection conn = ConnectionFactory.createConnection(conf);
        Table table = conn.getTable(TableName.valueOf("temp3"));

        // Try to write something to the "col1" column of the "colfam1" of the "temp3" table as the "dev" group
        // - this should work
        Put put = new Put(Bytes.toBytes("row3"));
        put.addColumn(Bytes.toBytes("colfam1"), Bytes.toBytes("col1"), Bytes.toBytes("val2"));
        table.put(put);

        // Try to write something to the "col2" column of the "colfam1" of the "temp3" table as the "dev" group
        // - this should fail
        put = new Put(Bytes.toBytes("row3"));
        put.addColumn(Bytes.toBytes("colfam1"), Bytes.toBytes("col2"), Bytes.toBytes("val2"));
        try {
          table.put(put);
          Assert.fail("Failure expected on an unauthorized user");
        } catch (IOException ex) {
          // expected
        }

        conn.close();
        return null;
      }
    });

    ugi = UserGroupInformation.createUserForTesting("IT", new String[] {"IT"});
    ugi.doAs(new PrivilegedExceptionAction<Void>() {
      public Void run() throws Exception {
        Connection conn = ConnectionFactory.createConnection(conf);
        Table table = conn.getTable(TableName.valueOf("temp3"));

        // Try to write something to the "col1" column of the "colfam1" of the "temp3" table as the "IT" group
        // - this should fail
        Put put = new Put(Bytes.toBytes("row3"));
        put.addColumn(Bytes.toBytes("colfam1"), Bytes.toBytes("col1"), Bytes.toBytes("val2"));
        try {
          table.put(put);
          Assert.fail("Failure expected on an unauthorized user");
        } catch (IOException ex) {
          // expected
        }

        conn.close();
        return null;
      }
    });

    // Drop the table
    conn = ConnectionFactory.createConnection(conf);
    admin = conn.getAdmin();

    admin.disableTable(TableName.valueOf("temp3"));
    admin.deleteTable(TableName.valueOf("temp3"));

    conn.close();
  }

  private static int getFreePort() throws IOException {
    ServerSocket serverSocket = new ServerSocket(0);
    int port = serverSocket.getLocalPort();
    serverSocket.close();
    return port;
  }

}
