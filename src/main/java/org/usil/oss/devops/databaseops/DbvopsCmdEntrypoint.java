package org.usil.oss.devops.databaseops;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import org.apache.commons.cli.CommandLine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.usil.oss.common.ascii.TableAscciHelper;
import org.usil.oss.common.cli.ArgumentsHelper;
import org.usil.oss.common.database.DatabaseHelper;
import org.usil.oss.common.exception.ExceptionHelper;
import org.usil.oss.common.file.ClassPathProperties;
import org.usil.oss.common.file.FileHelper;
import org.usil.oss.common.logger.LoggerHelper;
import org.usil.oss.common.model.ExecutionMetadata;

public class DbvopsCmdEntrypoint {

  private final Logger logger = LogManager.getLogger(DbvopsCmdEntrypoint.class);

  private DatabaseHelper databaseHelper = new DatabaseHelper();

  public ExecutionMetadata perform(String[] args) throws Exception {
    ArgumentsHelper argumentsHelper = new ArgumentsHelper();
    CommandLine commandLine = argumentsHelper.getArguments(args);

    HashMap<String, String> databaseParams = getDatabaseParametersFromCli(commandLine);

    String host = databaseParams.get("database_host");
    int port = Integer.parseInt(databaseParams.get("database_port"));
    String name = databaseParams.get("database_name");
    String user = databaseParams.get("database_user");
    String password = databaseParams.get("database_password");

    String scriptsFolder = commandLine.getOptionValue("scripts_folder");
    String engine = commandLine.getOptionValue("engine");

    if (commandLine.hasOption("verbose_log")) {
      LoggerHelper.setDebugLevel();
    }

    ArrayList<String> queries = FileHelper.readFilesAtRoot(new File(scriptsFolder), ".sql$");
    logger.info("scripts");
    logger.info(queries);

    ArrayList<String> rollbacks = FileHelper.readFilesAtRoot(new File(scriptsFolder), ".rollback$");
    logger.info("rollbacks");
    logger.info(rollbacks);

    FileHelper.detectRequiredPairs(queries, rollbacks, ".rollback");

    String sqlShowErrors = null;
    ArrayList<?> beforeErrors = new ArrayList<>();

    ArrayList<ArrayList<?>> successOutputs = new ArrayList<>();
    ArrayList<ArrayList<?>> errorOutputs = new ArrayList<>();
    ArrayList<?> afterErrors = new ArrayList<>();

    if (ClassPathProperties.hasProperty(engine + ".errorQueryFile")) {
      sqlShowErrors = FileHelper.getFileAsStringFromClasspath(
          ClassPathProperties.getProperty(engine + ".errorQueryFile"));

      beforeErrors = databaseHelper.executeSimpleScriptString(engine, host, port, name, user,
          password, sqlShowErrors);

      logger.info("Database errors before the scripts execution: " + beforeErrors.size());
      logger.info(TableAscciHelper.createSimpleTable(beforeErrors));
    }

    ArrayList<String> executedQueries = new ArrayList<String>();
    ArrayList<String> executedRollbacks = new ArrayList<String>();
    String currentScript = null;
    try {
      for (String scriptPath : queries) {
        currentScript = scriptPath;

        ArrayList<?> scriptOutput = databaseHelper.executeSimpleScriptFile(engine, host, port, name,
            user, password, currentScript);
        logger.info(String.format("script: %s , status: success",
            currentScript.replace(scriptsFolder, "")));
        executedQueries.add(currentScript);
        successOutputs.add(scriptOutput);
      }
      if (ClassPathProperties.hasProperty(engine + ".errorQueryFile")) {
        // detect if errors increased
        afterErrors = databaseHelper.executeSimpleScriptString(engine, host, port, name, user,
            password, sqlShowErrors);
        if (afterErrors.size() > beforeErrors.size()) {
          logger.info("scripts could caused new errors.");
          logger.info(TableAscciHelper.createSimpleTable(beforeErrors));
        }
      }
    } catch (Exception e) {
      String errorMessage =
          String.format("script: %s , status: error", currentScript.replace(scriptsFolder, ""));
      if (commandLine.hasOption("verbose_log")) {
        logger.error(errorMessage, e);
      } else {
        logger.error(errorMessage);
        logger.error(ExceptionHelper.summarizeTrace(e, true));
      }

      logger.info("Rollback scripts will be executed");
      if (executedQueries.size() < 1) {
        logger.info("Rollback is not required because error was throwed in first script");
      } else {
        Collections.reverse(executedQueries);

        for (String executedScript : executedQueries) {
          ArrayList<?> scriptOutput = databaseHelper.executeSimpleScriptFile(engine, host, port,
              name, user, password, executedScript + ".rollback");
          logger.info(String.format("rollback: %s , status: success",
              currentScript.replace(scriptsFolder, "")));
          errorOutputs.add(scriptOutput);
          executedRollbacks.add(executedScript + ".rollback");
        }
      }
    }

    logger.info("By JRichardsz");

    ExecutionMetadata executionMetadata = new ExecutionMetadata();
    executionMetadata.setAfterErrors(afterErrors);
    executionMetadata.setBeforeErrors(beforeErrors);
    executionMetadata.setQueryScripts(queries);
    executionMetadata.setRollbackScripts(rollbacks);
    executionMetadata.setExecutedQueryScripts(executedQueries);
    executionMetadata.setExecutedRollbackScripts(executedRollbacks);
    executionMetadata.setSuccessOutputs(successOutputs);
    executionMetadata.setErrorOutputs(errorOutputs);
    return executionMetadata;
  }

  private HashMap<String, String> getDatabaseParametersFromCli(CommandLine commandLine) {
    HashMap<String, String> params = new HashMap<String, String>();
    params.put("database_host", commandLine.getOptionValue("database_host"));
    params.put("database_port", commandLine.getOptionValue("database_port"));
    params.put("database_name", commandLine.getOptionValue("database_name"));
    params.put("database_user", commandLine.getOptionValue("database_user"));
    params.put("database_password", commandLine.getOptionValue("database_password"));
    return params;
  }
  
  
}
