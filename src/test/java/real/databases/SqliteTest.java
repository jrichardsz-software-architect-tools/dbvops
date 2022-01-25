package real.databases;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.usil.oss.common.model.ExecutionMetadata;
import org.usil.oss.devops.databaseops.DatabaseOps;
import org.usil.oss.devops.databaseops.DatabaseOpsCmdEntrypoint;

public class SqliteTest {

  private static final Logger logger = LoggerFactory.getLogger(SqliteTest.class);

  public String getDatabaseFileLocation() throws Exception {
    String basePath = new File("").getAbsolutePath();
    String sqliteDbFilePath =
        basePath + File.separator + "src/test/resources/real.databases/sqlite/chinook.db";

    String dbForTest = System.getProperty("java.io.tmpdir") + File.separator
        + UUID.randomUUID().toString() + ".db";
    Files.copy(new File(sqliteDbFilePath).toPath(), new File(dbForTest).toPath(),
        StandardCopyOption.REPLACE_EXISTING);

    return dbForTest;
  }

  @Test
  public void successForSeveralObjects() throws Exception {
    String basePath = new File("").getAbsolutePath();

    String sqliteDbFilePath = getDatabaseFileLocation();
    String engine = "sqlite";
    String host = "";
    int port = 0;
    String sid = sqliteDbFilePath;
    String user = "";
    String password = "";

    // create two tables
    int tablesCountBefore =
        SQLiteJdbc.exec("SELECT * FROM sqlite_master WHERE type='table'", sqliteDbFilePath).size();

    String tablesScriptsFolder =
        basePath + File.separator + "src/test/resources/real.databases/sqlite/ddl_tables";

    String tablesCmdArguments = String.format(
        "--database_host=%s --database_port=%s "
            + "--database_name=%s --database_user=%s --database_password=%s "
            + "--scripts_folder=%s --engine=%s",
        host, port, sid, user, password, tablesScriptsFolder, engine);

    String[] tablesArgs = tablesCmdArguments.split("\\s+");
    DatabaseOpsCmdEntrypoint.main(tablesArgs);

    int tablesCountAfter =
        SQLiteJdbc.exec("SELECT * FROM sqlite_master WHERE type='table'", sqliteDbFilePath).size();

    assertEquals("two new tables were expected", tablesCountBefore + 2, tablesCountAfter);

    // create two views
    int viewsCountBefore =
        SQLiteJdbc.exec("SELECT * FROM sqlite_master WHERE type='view'", sqliteDbFilePath).size();

    String viewsScriptsFolder =
        basePath + File.separator + "src/test/resources/real.databases/sqlite/ddl_views";

    String viewsCmdArguments = String.format(
        "--database_host=%s --database_port=%s "
            + "--database_name=%s --database_user=%s --database_password=%s "
            + "--scripts_folder=%s --engine=%s --verbose_log",
        host, port, sid, user, password, viewsScriptsFolder, engine);

    String[] viewsArgs = viewsCmdArguments.split("\\s+");
    DatabaseOpsCmdEntrypoint.main(viewsArgs);

    int viewsCountAfter =
        SQLiteJdbc.exec("SELECT * FROM sqlite_master WHERE type='view'", sqliteDbFilePath).size();

    assertEquals("two new views were expected", viewsCountBefore + 2, viewsCountAfter);

  }

  @Test
  public void successForMixObjectsAndExpectExectionOrder() throws Exception {
    String basePath = new File("").getAbsolutePath();

    String sqliteDbFilePath = getDatabaseFileLocation();
    String engine = "sqlite";
    String host = "";
    int port = 0;
    String sid = sqliteDbFilePath;
    String user = "";
    String password = "";

    // create two tables
    int objectsCountBefore = SQLiteJdbc
        .exec("SELECT * FROM sqlite_master where type in('view','table')", sqliteDbFilePath).size();

    String objectsScriptsFolder =
        basePath + File.separator + "src/test/resources/real.databases/sqlite/mix-ddl-dml";

    String cmdArguments = String.format(
        "--database_host=%s --database_port=%s "
            + "--database_name=%s --database_user=%s --database_password=%s "
            + "--scripts_folder=%s --engine=%s",
        host, port, sid, user, password, objectsScriptsFolder, engine);

    String[] tablesArgs = cmdArguments.split("\\s+");
    DatabaseOps databaseOps = new DatabaseOps();
    ExecutionMetadata executionMetadata = databaseOps.perform(tablesArgs);

    int objectsCountAfter = SQLiteJdbc
        .exec("SELECT * FROM sqlite_master where type in('view','table')", sqliteDbFilePath).size();

    assertEquals("four new objects were expected", objectsCountBefore + 4, objectsCountAfter);
    assertTrue("001.table1.sql should be the script #1",
        executionMetadata.getExecutedQueryScripts().get(0).endsWith("001.table1.sql"));
    assertTrue("002.table2.sql should be the script #2",
        executionMetadata.getExecutedQueryScripts().get(1).endsWith("002.table2.sql"));
    assertTrue("003.view1.sql should be the script #3",
        executionMetadata.getExecutedQueryScripts().get(2).endsWith("003.view1.sql"));
    assertTrue("004.view2.sql should be the script #4",
        executionMetadata.getExecutedQueryScripts().get(3).endsWith("004.view2.sql"));
  }

  @Test
  public void shouldFailIfRollbacksAreMissing() throws Exception {
    String basePath = new File("").getAbsolutePath();

    String sqliteDbFilePath = getDatabaseFileLocation();
    String engine = "sqlite";
    String host = "";
    int port = 0;
    String sid = sqliteDbFilePath;
    String user = "";
    String password = "";

    String objectsScriptsFolder = basePath + File.separator
        + "src/test/resources/real.databases/sqlite/without-rollback-files";

    String cmdArguments = String.format(
        "--database_host=%s --database_port=%s "
            + "--database_name=%s --database_user=%s --database_password=%s "
            + "--scripts_folder=%s --engine=%s",
        host, port, sid, user, password, objectsScriptsFolder, engine);

    String[] tablesArgs = cmdArguments.split("\\s+");
    DatabaseOps databaseOps = new DatabaseOps();

    Exception e = null;
    try {
      databaseOps.perform(tablesArgs);
    } catch (Exception ex) {
      e = ex;
    }
    assertNotNull("An error was expected because rollbacks are missing", e);
  }

  @Test
  public void executeRollbackIfAnErrorOcurred() throws Exception {
    String basePath = new File("").getAbsolutePath();

    String sqliteDbFilePath = getDatabaseFileLocation();
    String engine = "sqlite";
    String host = "";
    int port = 0;
    String sid = sqliteDbFilePath;
    String user = "";
    String password = "";

    String objectsScriptsFolder = basePath + File.separator
        + "src/test/resources/real.databases/sqlite/error_on_third_script";

    String cmdArguments = String.format(
        "--database_host=%s --database_port=%s "
            + "--database_name=%s --database_user=%s --database_password=%s "
            + "--scripts_folder=%s --engine=%s",
        host, port, sid, user, password, objectsScriptsFolder, engine);

    String[] tablesArgs = cmdArguments.split("\\s+");
    DatabaseOps databaseOps = new DatabaseOps();

    int objectsCountBefore = SQLiteJdbc
        .exec("SELECT * FROM sqlite_master where type in('view','table')", sqliteDbFilePath).size();

    databaseOps.perform(tablesArgs);

    int objectsCountAfter = SQLiteJdbc
        .exec("SELECT * FROM sqlite_master where type in('view','table')", sqliteDbFilePath).size();

    assertEquals(
        "thanks to rollbacks, the database objects count should be the same as before execution",
        objectsCountBefore, objectsCountAfter);

    // tables should not exist
    // I'm use PRAGMA to get details from table : https://stackoverflow.com/a/7679086/3957754
    int table1Existence = SQLiteJdbc.exec("PRAGMA table_info(table_1);", sqliteDbFilePath).size();
    assertEquals("table1 should not exist thanks to rollback", 0, table1Existence);
    int table2Existence = SQLiteJdbc.exec("PRAGMA table_info(table_2);", sqliteDbFilePath).size();
    assertEquals("table2 should not exist thanks to rollback", 0, table2Existence);

  }

  @Test
  public void tablesShouldExistIfRollbackFails() throws Exception {
    String basePath = new File("").getAbsolutePath();

    String sqliteDbFilePath = getDatabaseFileLocation();
    System.out.println(sqliteDbFilePath);
    String engine = "sqlite";
    String host = "";
    int port = 0;
    String sid = sqliteDbFilePath;
    String user = "";
    String password = "";

    String objectsScriptsFolder =
        basePath + File.separator + "src/test/resources/real.databases/sqlite/wrong-002-rollback";

    String cmdArguments = String.format(
        "--database_host=%s --database_port=%s "
            + "--database_name=%s --database_user=%s --database_password=%s "
            + "--scripts_folder=%s --engine=%s",
        host, port, sid, user, password, objectsScriptsFolder, engine);

    String[] tablesArgs = cmdArguments.split("\\s+");
    DatabaseOps databaseOps = new DatabaseOps();

    int objectsCountBefore = SQLiteJdbc
        .exec("SELECT * FROM sqlite_master where type in('view','table')", sqliteDbFilePath).size();

    databaseOps.perform(tablesArgs);

    int objectsCountAfter = SQLiteJdbc
        .exec("SELECT * FROM sqlite_master where type in('view','table')", sqliteDbFilePath).size();

    assertEquals(
        "due to rollback fails, the database objects count are not the same as before execution",
        objectsCountBefore + 2, objectsCountAfter);

    // tables should exist
    // I'm use PRAGMA to get details from table : https://stackoverflow.com/a/7679086/3957754
    int table1Existence = SQLiteJdbc.exec("PRAGMA table_info(table_1);", sqliteDbFilePath).size();
    assertTrue("table1 should exist because rollback failed", table1Existence > 0);
    int table2Existence = SQLiteJdbc.exec("PRAGMA table_info(table_2);", sqliteDbFilePath).size();
    assertTrue("table2 should exist because rollback failed", table2Existence > 0);

  }


}