import javafx.application.Application;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class FxDb extends Application {

    //<editor-fold desc="Class Fields">
    private Stage primaryStage;
    private ListView<String> tableListView;
    private TableView<ObservableList<String>> dataTableView;
    private TextArea logArea;
    private Label currentTableLabel;
    private VBox insertForm;
    private TabPane actionTabPane;
    private TextField updateSetField, updateWhereField;
    private TextField deleteWhereField;
    private TextArea customSqlArea;
    private TableView<ColumnDefinition> createTableDefView;
    private TextField newTableNameField;
    private ComboBox<String> dropColumnComboBox;
    private Button dropColumnButton;
    private TextField addColumnNameField;
    private ComboBox<String> addColumnTypeComboBox;
    private TextField addColumnSizeField;
    private Button addColumnButton;

    private final Map<ObservableList<String>, SimpleBooleanProperty> rowSelectionMap = new HashMap<>();
    private final DatabaseHelper dbHelper = new DatabaseHelper();

    private StackPane centerStackPane;
    private VBox centerVBox;
    private ProgressIndicator progressIndicator;
    //</editor-fold>

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        primaryStage.setTitle("JavaFX Dynamic DB Manager - Enhanced");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        root.setLeft(createLeftPanel());
        root.setCenter(createCenterPanel());
        root.setRight(createRightPanel());

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(120);
        logArea.setText("Welcome! Connect to the database and select a table to begin.\n");
        root.setBottom(logArea);

        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        double sceneWidth = screenBounds.getWidth() * 0.85;
        double sceneHeight = screenBounds.getHeight() * 0.85;

        Scene scene = new Scene(root, sceneWidth, sceneHeight);
        primaryStage.setScene(scene);
        primaryStage.show();

        refreshTableList();
    }

    /**
     * A helper method to run database operations on a background thread.
     * It shows a progress indicator, disables the UI, and handles success/failure.
     */
    private <T> void runBackgroundTask(Supplier<T> backgroundAction, Consumer<T> successConsumer) {
        progressIndicator.setVisible(true);
        centerVBox.setDisable(true); // Disable main content

        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return backgroundAction.get();
            }
        };

        task.setOnSucceeded(event -> {
            successConsumer.accept(task.getValue());
            progressIndicator.setVisible(false);
            centerVBox.setDisable(false);
        });

        task.setOnFailed(event -> {
            Throwable e = task.getException();
            showError("Background Task Error", "An operation failed to complete.", e.getMessage());
            log("Error during background task: " + e.getMessage());
            e.printStackTrace();
            progressIndicator.setVisible(false);
            centerVBox.setDisable(false);
        });

        new Thread(task).start();
    }

    @SuppressWarnings("unchecked")
    private void loadTableData(String tableName) {
        runBackgroundTask(
                () -> {
                    try {
                        return dbHelper.getTableData(tableName);
                    } catch (SQLException e) {
                        throw new RuntimeException("Failed to get table data: " + e.getMessage(), e);
                    }
                },
                tableData -> {
                    dataTableView.getColumns().clear();
                    dataTableView.getItems().clear();
                    rowSelectionMap.clear();

                    TableColumn<ObservableList<String>, Boolean> selectCol = new TableColumn<>("Select");
                    selectCol.setCellValueFactory(cd -> rowSelectionMap.computeIfAbsent(cd.getValue(), k -> new SimpleBooleanProperty(false)));
                    selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
                    selectCol.setEditable(true);
                    selectCol.setPrefWidth(50);
                    dataTableView.getColumns().add(selectCol);

                    for (int i = 0; i < tableData.headers().size(); i++) {
                        final int colIndex = i;
                        final String headerName = tableData.headers().get(i);
                        TableColumn<ObservableList<String>, String> column = new TableColumn<>(headerName);
                        column.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().get(colIndex)));

                        if (i > 0) {
                            column.setCellFactory(TextFieldTableCell.forTableColumn());
                            column.setOnEditCommit(event -> {
                                String pkValue = event.getRowValue().get(0);
                                runBackgroundTask(
                                        () -> {
                                            try {
                                                dbHelper.updateCellValue(tableName, headerName, event.getNewValue(), tableData.headers().get(0), pkValue);
                                                return true; // Success
                                            } catch (SQLException e) {
                                                throw new RuntimeException("Failed to update cell: " + e.getMessage(), e);
                                            }
                                        },
                                        success -> {
                                            log("Updated cell in '" + tableName + "'.");
                                            event.getRowValue().set(colIndex, event.getNewValue());
                                        }
                                );
                            });
                        }
                        column.setPrefWidth(120);
                        dataTableView.getColumns().add(column);
                    }
                    dataTableView.setItems(tableData.rows());
                    log("Displayed data for table '" + tableName + "'. Found " + tableData.rows().size() + " rows.");
                }
        );
    }

    //<editor-fold desc="UI Creation Methods">
    private StackPane createCenterPanel() {
        currentTableLabel = new Label("No Table Selected");
        currentTableLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Button refreshDataButton = new Button("Refresh");
        refreshDataButton.setOnAction(e -> {
            String selectedTable = getSelectedTable();
            if (selectedTable != null) {
                loadTableData(selectedTable);
            }
        });

        Button deleteSelectedButton = new Button("Delete Selected Rows");
        deleteSelectedButton.setOnAction(e -> handleDeleteSelectedRows());
        deleteSelectedButton.setStyle("-fx-background-color: #ff8c8c; -fx-text-fill: white; -fx-font-weight: bold;");

        Button duplicateRowButton = new Button("Duplicate Selected Row");
        duplicateRowButton.setOnAction(e -> handleDuplicateRow());
        duplicateRowButton.setStyle("-fx-background-color: #a9d18e;");

        HBox topBar = new HBox(15, currentTableLabel, refreshDataButton, duplicateRowButton, deleteSelectedButton);
        topBar.setAlignment(Pos.CENTER_LEFT);

        dataTableView = new TableView<>();
        dataTableView.setEditable(true);
        dataTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        dataTableView.setPlaceholder(new Label("Select a table from the list on the left to view its data."));

        centerVBox = new VBox(10, topBar, dataTableView);
        centerVBox.setPadding(new Insets(10));
        VBox.setVgrow(dataTableView, Priority.ALWAYS);

        progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);
        progressIndicator.setMaxSize(100, 100);

        centerStackPane = new StackPane(centerVBox, progressIndicator);
        return centerStackPane;
    }

    private VBox createLeftPanel() {
        Label label = new Label("Database Tables");
        label.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        tableListView = new ListView<>();
        Button refreshBtn = new Button("Refresh List");
        refreshBtn.setMaxWidth(Double.MAX_VALUE);
        refreshBtn.setOnAction(e -> refreshTableList());
        VBox leftPanel = new VBox(10, label, tableListView, refreshBtn);
        tableListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadTableData(newVal);
                updateActionPanelForTable(newVal);
            }
        });
        leftPanel.setPadding(new Insets(10));
        leftPanel.setPrefWidth(200);
        VBox.setVgrow(tableListView, Priority.ALWAYS);
        return leftPanel;
    }

    private TabPane createRightPanel() {
        actionTabPane = new TabPane();
        actionTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Tab createTab = new Tab("Create Table", createCreateTableTab());
        Tab insertTab = new Tab("Insert", createInsertTab());
        Tab updateTab = new Tab("Update", createUpdateTab());
        Tab deleteTab = new Tab("Delete", createDeleteTab());
        Tab structureTab = new Tab("Structure", createStructureTab());
        Tab sqlTab = new Tab("Execute SQL", createSqlTab());
        actionTabPane.getTabs().addAll(createTab, insertTab, updateTab, deleteTab, structureTab, sqlTab);
        actionTabPane.setPrefWidth(550); // Increased width for more columns
        return actionTabPane;
    }
    //</editor-fold>

    //<editor-fold desc="Tab Creation Methods">
    @SuppressWarnings("unchecked")
    private VBox createCreateTableTab() {
        VBox container = new VBox(15);
        container.setPadding(new Insets(15));
        Label title = new Label("Create New Table");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        newTableNameField = new TextField();
        HBox tableNameBox = new HBox(10, new Label("Table Name:"), newTableNameField);
        tableNameBox.setAlignment(Pos.CENTER_LEFT);

        createTableDefView = new TableView<>();
        createTableDefView.setEditable(true);
        createTableDefView.setPrefHeight(350);

        // Updated default item to include the new properties
        createTableDefView.setItems(FXCollections.observableArrayList(
                new ColumnDefinition("id", "INT", "11", true, true, true, true, false, "", "Primary Key Identifier")
        ));

        ObservableList<String> dataTypes = FXCollections.observableArrayList("INT", "VARCHAR", "TEXT", "DATE", "DATETIME", "DECIMAL", "BOOLEAN", "DOUBLE", "TIMESTAMP", "BIGINT", "FLOAT", "BLOB");

        // --- Table Columns for Definition View ---
        TableColumn<ColumnDefinition, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setCellFactory(TextFieldTableCell.forTableColumn());
        nameCol.setOnEditCommit(e -> e.getRowValue().setName(e.getNewValue()));

        TableColumn<ColumnDefinition, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("dataType"));
        typeCol.setCellFactory(ComboBoxTableCell.forTableColumn(dataTypes));
        typeCol.setOnEditCommit(e -> e.getRowValue().setDataType(e.getNewValue()));

        TableColumn<ColumnDefinition, String> sizeCol = new TableColumn<>("Size/Len");
        sizeCol.setCellValueFactory(new PropertyValueFactory<>("size"));
        sizeCol.setCellFactory(TextFieldTableCell.forTableColumn());
        sizeCol.setOnEditCommit(e -> e.getRowValue().setSize(e.getNewValue()));

        TableColumn<ColumnDefinition, Boolean> unsignedCol = new TableColumn<>("Unsigned");
        unsignedCol.setCellValueFactory(cellData -> cellData.getValue().unsignedProperty());
        unsignedCol.setCellFactory(CheckBoxTableCell.forTableColumn(unsignedCol));

        TableColumn<ColumnDefinition, Boolean> nnCol = new TableColumn<>("Not Null");
        nnCol.setCellValueFactory(cellData -> cellData.getValue().notNullProperty());
        nnCol.setCellFactory(CheckBoxTableCell.forTableColumn(nnCol));

        TableColumn<ColumnDefinition, Boolean> aiCol = new TableColumn<>("Auto Incr");
        aiCol.setCellValueFactory(cellData -> cellData.getValue().autoIncrementProperty());
        aiCol.setCellFactory(CheckBoxTableCell.forTableColumn(aiCol));

        TableColumn<ColumnDefinition, Boolean> pkCol = new TableColumn<>("PK");
        pkCol.setCellValueFactory(cellData -> cellData.getValue().primaryKeyProperty());
        pkCol.setCellFactory(CheckBoxTableCell.forTableColumn(pkCol));

        TableColumn<ColumnDefinition, Boolean> uniqueCol = new TableColumn<>("Unique");
        uniqueCol.setCellValueFactory(cellData -> cellData.getValue().uniqueProperty());
        uniqueCol.setCellFactory(CheckBoxTableCell.forTableColumn(uniqueCol));

        TableColumn<ColumnDefinition, String> defaultCol = new TableColumn<>("Default");
        defaultCol.setCellValueFactory(new PropertyValueFactory<>("defaultValue"));
        defaultCol.setCellFactory(TextFieldTableCell.forTableColumn());
        defaultCol.setOnEditCommit(e -> e.getRowValue().setDefaultValue(e.getNewValue()));

        TableColumn<ColumnDefinition, String> commentCol = new TableColumn<>("Comment");
        commentCol.setCellValueFactory(new PropertyValueFactory<>("comment"));
        commentCol.setCellFactory(TextFieldTableCell.forTableColumn());
        commentCol.setOnEditCommit(e -> e.getRowValue().setComment(e.getNewValue()));

        createTableDefView.getColumns().addAll(nameCol, typeCol, sizeCol, unsignedCol, nnCol, aiCol, pkCol, uniqueCol, defaultCol, commentCol);
        createTableDefView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        Button addColBtn = new Button("Add Column");
        // Updated the "Add Column" button to use the new constructor
        addColBtn.setOnAction(e -> createTableDefView.getItems().add(new ColumnDefinition("", "VARCHAR", "255", false, true, false, false, false, "", "")));
        Button removeColBtn = new Button("Remove Selected");
        removeColBtn.setOnAction(e -> {
            ColumnDefinition selected = createTableDefView.getSelectionModel().getSelectedItem();
            if (selected != null) createTableDefView.getItems().remove(selected);
        });
        HBox colButtons = new HBox(10, addColBtn, removeColBtn);

        Button executeCreateBtn = new Button("Execute CREATE TABLE");
        executeCreateBtn.setMaxWidth(Double.MAX_VALUE);
        executeCreateBtn.setStyle("-fx-font-weight: bold;");
        executeCreateBtn.setOnAction(e -> handleCreateTable());

        container.getChildren().addAll(title, tableNameBox, createTableDefView, colButtons, new Separator(), executeCreateBtn);
        return container;
    }

    private VBox createStructureTab() {
        VBox container = new VBox(20);
        container.setPadding(new Insets(15));
        container.setAlignment(Pos.TOP_LEFT);

        // --- Add Column Section ---
        Label addTitle = new Label("Add New Column");
        addTitle.setStyle("-fx-font-weight: bold;");
        addColumnNameField = new TextField();
        addColumnNameField.setPromptText("New column name (e.g., 'email')");
        addColumnTypeComboBox = new ComboBox<>(FXCollections.observableArrayList(
                "VARCHAR", "INT", "TEXT", "DATE", "DATETIME", "DECIMAL", "BOOLEAN", "DOUBLE"
        ));
        addColumnTypeComboBox.setPromptText("Data Type");
        addColumnTypeComboBox.getSelectionModel().select("VARCHAR");
        addColumnSizeField = new TextField();
        addColumnSizeField.setPromptText("Size/Length (e.g., '255')");
        addColumnButton = new Button("Add New Column");
        addColumnButton.setMaxWidth(Double.MAX_VALUE);
        addColumnButton.setOnAction(e -> handleAddColumn());
        GridPane addGrid = new GridPane();
        addGrid.setVgap(5);
        addGrid.setHgap(5);
        addGrid.add(new Label("Name:"), 0, 0);
        addGrid.add(addColumnNameField, 1, 0);
        addGrid.add(new Label("Type:"), 0, 1);
        addGrid.add(addColumnTypeComboBox, 1, 1);
        addGrid.add(new Label("Size:"), 0, 2);
        addGrid.add(addColumnSizeField, 1, 2);
        VBox addBox = new VBox(10, addTitle, addGrid, addColumnButton);

        // --- Drop Column Section ---
        Label dropTitle = new Label("Drop Column");
        dropTitle.setStyle("-fx-font-weight: bold;");
        dropColumnComboBox = new ComboBox<>();
        dropColumnComboBox.setPromptText("Select column to drop");
        dropColumnComboBox.setMaxWidth(Double.MAX_VALUE);
        dropColumnButton = new Button("Drop Selected Column");
        dropColumnButton.setMaxWidth(Double.MAX_VALUE);
        dropColumnButton.setStyle("-fx-background-color: #ff6666; -fx-text-fill: white;");
        dropColumnButton.setOnAction(e -> handleDropColumn());
        VBox dropBox = new VBox(10, dropTitle, dropColumnComboBox, dropColumnButton);

        container.getChildren().addAll(addBox, new Separator(), dropBox);
        return container;
    }

    private VBox createInsertTab() {
        insertForm = new VBox(10);
        insertForm.setPadding(new Insets(15));
        insertForm.setAlignment(Pos.TOP_LEFT);
        insertForm.getChildren().add(new Label("Select a table to see insert form."));
        return insertForm;
    }

    private VBox createUpdateTab() {
        Label title = new Label("Update Rows (by WHERE clause)");
        title.setStyle("-fx-font-weight: bold;");
        updateSetField = new TextField();
        updateWhereField = new TextField();
        GridPane grid = new GridPane();
        grid.setVgap(10);
        grid.setHgap(10);
        grid.add(new Label("SET:"), 0, 0);
        grid.add(updateSetField, 1, 0);
        grid.add(new Label("WHERE:"), 0, 1);
        grid.add(updateWhereField, 1, 1);
        updateSetField.setPromptText("e.g., name = 'New Name', age = 30");
        updateWhereField.setPromptText("e.g., id = 5 (required)");
        Button updateButton = new Button("Execute Update");
        updateButton.setMaxWidth(Double.MAX_VALUE);
        updateButton.setOnAction(e -> handleUpdate());
        VBox updateBox = new VBox(15, title, grid, updateButton);
        updateBox.setPadding(new Insets(15));
        return updateBox;
    }

    private VBox createDeleteTab() {
        Label title = new Label("Delete Rows (by WHERE clause)");
        title.setStyle("-fx-font-weight: bold;");
        deleteWhereField = new TextField();
        deleteWhereField.setPromptText("e.g., id > 100 (Leave empty to delete ALL)");
        Button deleteButton = new Button("Execute Delete");
        deleteButton.setMaxWidth(Double.MAX_VALUE);
        deleteButton.setOnAction(e -> handleDelete());
        Button dropTableButton = new Button("Drop (Delete) Entire Table");
        dropTableButton.setMaxWidth(Double.MAX_VALUE);
        dropTableButton.setStyle("-fx-background-color: #ff6666; -fx-text-fill: white;");
        dropTableButton.setOnAction(e -> handleDropTable());
        VBox deleteBox = new VBox(15, title, new Label("WHERE Clause:"), deleteWhereField, deleteButton, new Separator(), dropTableButton);
        deleteBox.setPadding(new Insets(15));
        return deleteBox;
    }

    private VBox createSqlTab() {
        Label title = new Label("Execute Custom SQL");
        title.setStyle("-fx-font-weight: bold;");
        customSqlArea = new TextArea();
        customSqlArea.setPromptText("Enter any SQL command (SELECT, INSERT, CREATE TABLE, etc.)");
        customSqlArea.setPrefRowCount(10);
        Button executeSqlButton = new Button("Execute");
        executeSqlButton.setMaxWidth(Double.MAX_VALUE);
        executeSqlButton.setOnAction(e -> handleExecuteCustomSql());
        Button executeFromFileButton = new Button("Execute from File...");
        executeFromFileButton.setMaxWidth(Double.MAX_VALUE);
        executeFromFileButton.setOnAction(e -> handleExecuteSqlFromFile());
        VBox sqlBox = new VBox(15, title, customSqlArea, executeSqlButton, executeFromFileButton);
        sqlBox.setPadding(new Insets(15));
        return sqlBox;
    }
    //</editor-fold>

    //<editor-fold desc="Event Handlers and Logic">
    private void refreshTableList() {
        runBackgroundTask(
                () -> {
                    try {
                        return dbHelper.getTableNames();
                    } catch (SQLException e) {
                        throw new RuntimeException("Could not fetch table list: " + e.getMessage(), e);
                    }
                },
                tableNames -> {
                    tableListView.setItems(FXCollections.observableArrayList(tableNames));
                    log("Successfully refreshed table list from the database.");
                }
        );
    }

    private void updateActionPanelForTable(String tableName) {
        currentTableLabel.setText("Table: " + tableName);
        insertForm.getChildren().clear();
        GridPane insertGrid = new GridPane();
        insertGrid.setHgap(10);
        insertGrid.setVgap(10);
        try {
            List<String> columnNames = dbHelper.getColumnNames(tableName);
            dropColumnComboBox.setItems(FXCollections.observableArrayList(columnNames));
            dropColumnComboBox.getSelectionModel().clearSelection();
            for (int i = 0; i < columnNames.size(); i++) {
                Label label = new Label(columnNames.get(i) + ":");
                TextField field = new TextField();
                field.setId("insertField_" + columnNames.get(i));
                insertGrid.add(label, 0, i);
                insertGrid.add(field, 1, i);
            }
            Button insertButton = new Button("Insert New Row");
            insertButton.setMaxWidth(Double.MAX_VALUE);
            insertButton.setOnAction(e -> handleInsert(tableName, columnNames, insertGrid));
            insertForm.getChildren().addAll(insertGrid, insertButton);
        } catch (SQLException e) {
            insertForm.getChildren().add(new Label("Error loading form: " + e.getMessage()));
            log("Error creating insert form for '" + tableName + "': " + e.getMessage());
        }
    }

    private void handleDuplicateRow() {
        String tableName = getSelectedTable();
        if (tableName == null) return;

        ObservableList<ObservableList<String>> selectedItems = dataTableView.getSelectionModel().getSelectedItems();
        if (selectedItems.size() != 1) {
            showError("Selection Error", "Please select exactly one row to duplicate.", "You have selected " + selectedItems.size() + " rows.");
            return;
        }

        ObservableList<String> rowToDuplicate = selectedItems.get(0);
        actionTabPane.getSelectionModel().select(1); // Select Insert tab

        try {
            List<String> columnNames = dbHelper.getColumnNames(tableName);
            for (int i = 0; i < columnNames.size(); i++) {
                if (i == 0) continue; // Skip PK

                String colName = columnNames.get(i);
                TextField field = (TextField) insertForm.lookup("#insertField_" + colName);
                if (field != null) {
                    field.setText(rowToDuplicate.get(i));
                }
            }
            log("Insert form pre-filled. Modify and click Insert.");
        } catch (SQLException e) {
            showError("Error", "Could not get column names to pre-fill form.", e.getMessage());
        }
    }

    private void handleInsert(String tableName, List<String> columnNames, GridPane grid) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String colName : columnNames) {
            TextField field = (TextField) grid.lookup("#insertField_" + colName);
            if (field != null && field.getText() != null && !field.getText().isEmpty()) {
                values.put(colName, field.getText());
            }
        }
        if (values.isEmpty()) {
            showError("Insert Error", "No values provided.", "Please enter data in at least one field.");
            return;
        }

        runBackgroundTask(
                () -> {
                    try {
                        dbHelper.insertRow(tableName, values);
                        return true;
                    } catch (SQLException e) {
                        throw new RuntimeException("Could not insert row: " + e.getMessage(), e);
                    }
                },
                success -> {
                    log("Successfully inserted a new row into '" + tableName + "'.");
                    loadTableData(tableName);
                    for (Node node : grid.getChildren()) {
                        if (node instanceof TextField) {
                            ((TextField) node).clear();
                        }
                    }
                }
        );
    }

    private void handleCreateTable() {
        String tableName = newTableNameField.getText();
        if (tableName == null || tableName.trim().isEmpty()) {
            showError("Validation Error", "Table Name is required.", null);
            return;
        }
        ObservableList<ColumnDefinition> columns = createTableDefView.getItems();
        if (columns.stream().allMatch(c -> c.getName() == null || c.getName().trim().isEmpty())) {
            showError("Validation Error", "At least one column is required.", null);
            return;
        }

        try {
            String sql = generateCreateTableSql(tableName, columns);
            log("Generated SQL:\n" + sql);
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirm SQL Execution");
            confirm.setHeaderText("Execute the following SQL statement?");
            confirm.setContentText(sql);

            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                runBackgroundTask(
                        () -> {
                            try {
                                return dbHelper.executeUpdateOrDelete(sql);
                            } catch (SQLException e) {
                                throw new RuntimeException("Table creation failed: " + e.getMessage(), e);
                            }
                        },
                        rowsAffected -> {
                            log("SUCCESS: Table '" + tableName + "' created successfully.");
                            newTableNameField.clear();
                            createTableDefView.getItems().setAll(
                                new ColumnDefinition("id", "INT", "11", true, true, true, true, false, "", "Primary Key Identifier")
                            );
                            refreshTableList();
                        }
                );
            }
        } catch (Exception e) {
            showError("SQL Generation Error", "Could not create the SQL for the table.", e.getMessage());
        }
    }

    private String generateCreateTableSql(String tableName, List<ColumnDefinition> columns) {
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE `").append(tableName.trim().replace("`", "")).append("` (\n");

        final Set<String> typesWithSize = Set.of("VARCHAR", "CHAR", "INT", "BIGINT", "DECIMAL", "NUMERIC", "FLOAT", "DOUBLE");

        List<String> columnDefs = new ArrayList<>();
        for (ColumnDefinition col : columns) {
            if (col.getName() == null || col.getName().trim().isEmpty()) continue;

            StringBuilder colDef = new StringBuilder();
            colDef.append("  `").append(col.getName()).append("` ").append(col.getDataType());

            if (col.getSize() != null && !col.getSize().trim().isEmpty() && typesWithSize.contains(col.getDataType().toUpperCase())) {
                colDef.append("(").append(col.getSize()).append(")");
            }

            if (col.isUnsigned()) {
                colDef.append(" UNSIGNED");
            }

            if (col.isNotNull()) {
                colDef.append(" NOT NULL");
            }

            if (col.getDefaultValue() != null && !col.getDefaultValue().trim().isEmpty()) {
                String defaultValue = col.getDefaultValue();
                String upperDataType = col.getDataType().toUpperCase();
                if (upperDataType.contains("CHAR") || upperDataType.contains("TEXT") || upperDataType.contains("DATE") || upperDataType.contains("TIME")) {
                    colDef.append(" DEFAULT '").append(defaultValue.replace("'", "''")).append("'");
                } else {
                    colDef.append(" DEFAULT ").append(defaultValue);
                }
            }

            if (col.isAutoIncrement()) {
                colDef.append(" AUTO_INCREMENT");
            }

            if (col.isUnique()) {
                colDef.append(" UNIQUE");
            }

            if (col.getComment() != null && !col.getComment().trim().isEmpty()) {
                colDef.append(" COMMENT '").append(col.getComment().replace("'", "''")).append("'");
            }

            columnDefs.add(colDef.toString());
        }
        sql.append(String.join(",\n", columnDefs));

        List<String> pkColumns = columns.stream()
                .filter(ColumnDefinition::isPrimaryKey)
                .map(c -> "`" + c.getName() + "`")
                .collect(Collectors.toList());
        if (!pkColumns.isEmpty()) {
            sql.append(",\n  PRIMARY KEY (").append(String.join(", ", pkColumns)).append(")");
        }

        sql.append("\n) ENGINE=InnoDB;");
        return sql.toString();
    }

    private void handleAddColumn() {
        String tableName = getSelectedTable();
        if (tableName == null) return;
        String newColName = addColumnNameField.getText();
        String newColType = addColumnTypeComboBox.getValue();
        String newColSize = addColumnSizeField.getText();

        if (newColName == null || newColName.trim().isEmpty() || newColType == null) {
            showError("Validation Error", "Column Name and Type are required.", null);
            return;
        }

        StringBuilder definition = new StringBuilder(newColType);
        if (newColSize != null && !newColSize.trim().isEmpty()) {
            definition.append("(").append(newColSize).append(")");
        }

        String sql = String.format("ALTER TABLE `%s` ADD COLUMN `%s` %s", tableName, newColName, definition);
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm ADD COLUMN");
        confirm.setHeaderText("Execute the following SQL statement?");
        confirm.setContentText(sql);

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            runBackgroundTask(
                    () -> {
                        try {
                            return dbHelper.executeUpdateOrDelete(sql);
                        } catch (SQLException e) {
                            throw new RuntimeException("Could not add column: " + e.getMessage(), e);
                        }
                    },
                    rowsAffected -> {
                        log("Successfully added column '" + newColName + "' to '" + tableName + "'.");
                        addColumnNameField.clear();
                        addColumnSizeField.clear();
                        loadTableData(tableName);
                        updateActionPanelForTable(tableName);
                    }
            );
        }
    }

    private void handleDropColumn() {
        String tableName = getSelectedTable();
        if (tableName == null) return;
        String columnToDrop = dropColumnComboBox.getSelectionModel().getSelectedItem();
        if (columnToDrop == null || columnToDrop.isEmpty()) {
            showError("No Selection", "No column selected to drop.", null);
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm DROP COLUMN");
        confirm.setHeaderText("PERMANENTLY DELETE column '" + columnToDrop + "' from table '" + tableName + "'?");
        confirm.setContentText("This action cannot be undone.");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            String sql = "ALTER TABLE `" + tableName + "` DROP COLUMN `" + columnToDrop + "`";
            runBackgroundTask(
                    () -> {
                        try {
                            return dbHelper.executeUpdateOrDelete(sql);
                        } catch (SQLException e) {
                            throw new RuntimeException("Could not drop column: " + e.getMessage(), e);
                        }
                    },
                    rowsAffected -> {
                        log("Successfully dropped column '" + columnToDrop + "' from '" + tableName + "'.");
                        loadTableData(tableName);
                        updateActionPanelForTable(tableName);
                    }
            );
        }
    }

    private void handleDeleteSelectedRows() {
        String tableName = getSelectedTable();
        if (tableName == null) return;
        List<ObservableList<String>> rowsToDelete = rowSelectionMap.entrySet().stream()
                .filter(entry -> entry.getValue().get())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        if (rowsToDelete.isEmpty()) {
            showError("No Selection", "No rows selected for deletion.", null);
            return;
        }

        try {
            String pkColumnName = dbHelper.getColumnNames(tableName).get(0);
            List<String> pkValues = rowsToDelete.stream().map(row -> row.get(0)).collect(Collectors.toList());
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirm Deletion");
            confirm.setHeaderText("Delete " + pkValues.size() + " row(s) from table '" + tableName + "'?");
            confirm.setContentText("This action cannot be undone.");

            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                runBackgroundTask(
                        () -> {
                            try {
                                dbHelper.deleteMultipleRows(tableName, pkColumnName, pkValues);
                                return pkValues.size();
                            } catch (SQLException e) {
                                throw new RuntimeException("Could not delete rows: " + e.getMessage(), e);
                            }
                        },
                        deletedCount -> {
                            log("Successfully deleted " + deletedCount + " rows from '" + tableName + "'.");
                            loadTableData(tableName);
                        }
                );
            }
        } catch (Exception e) {
            showError("Deletion Error", "Could not prepare the delete operation.", e.getMessage());
        }
    }

    private void handleUpdate() {
        String tableName = getSelectedTable();
        if (tableName == null) return;
        String setClause = updateSetField.getText();
        String whereClause = updateWhereField.getText();
        if (setClause.isEmpty() || whereClause.isEmpty()) {
            showError("Update Error", "Both SET and WHERE clauses are required.", null);
            return;
        }
        String sql = "UPDATE `" + tableName + "` SET " + setClause + " WHERE " + whereClause;
        runBackgroundTask(
                () -> {
                    try {
                        return dbHelper.executeUpdateOrDelete(sql);
                    } catch (SQLException e) {
                        throw new RuntimeException("Update failed: " + e.getMessage(), e);
                    }
                },
                rowsAffected -> {
                    log("Update successful. " + rowsAffected + " row(s) affected in '" + tableName + "'.");
                    loadTableData(tableName);
                }
        );
    }

    private void handleDelete() {
        String tableName = getSelectedTable();
        if (tableName == null) return;
        String whereClause = deleteWhereField.getText();
        if (whereClause.isEmpty()) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirm Full Table Deletion");
            confirm.setHeaderText("You are about to delete ALL rows from table '" + tableName + "'.");
            confirm.setContentText("This action cannot be undone. Are you sure?");
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
        }
        String sql = "DELETE FROM `" + tableName + "`" + (whereClause.isEmpty() ? "" : " WHERE " + whereClause);
        runBackgroundTask(
                () -> {
                    try {
                        return dbHelper.executeUpdateOrDelete(sql);
                    } catch (SQLException e) {
                        throw new RuntimeException("Delete failed: " + e.getMessage(), e);
                    }
                },
                rowsAffected -> {
                    log("Delete successful. " + rowsAffected + " row(s) deleted from '" + tableName + "'.");
                    loadTableData(tableName);
                }
        );
    }

    private void handleDropTable() {
        String tableName = getSelectedTable();
        if (tableName == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm DROP TABLE");
        confirm.setHeaderText("You are about to PERMANENTLY DELETE the entire table '" + tableName + "'.");
        confirm.setContentText("This is a destructive operation and cannot be undone.");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            String sql = "DROP TABLE `" + tableName + "`";
            runBackgroundTask(
                    () -> {
                        try {
                            return dbHelper.executeUpdateOrDelete(sql);
                        } catch (SQLException e) {
                            throw new RuntimeException("Drop table failed: " + e.getMessage(), e);
                        }
                    },
                    rowsAffected -> {
                        log("Table '" + tableName + "' was successfully dropped.");
                        refreshTableList();
                        dataTableView.getColumns().clear();
                        dataTableView.getItems().clear();
                        currentTableLabel.setText("No Table Selected");
                    }
            );
        }
    }

    private void handleExecuteCustomSql() {
        String sql = customSqlArea.getText();
        if (sql.trim().isEmpty()) {
            showError("SQL Error", "No SQL command entered.", null);
            return;
        }

        if (sql.trim().toLowerCase().startsWith("select")) {
            runBackgroundTask(
                    () -> {
                        try {
                            return dbHelper.executeGenericQuery(sql);
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    },
                    resultData -> {
                        displayQueryResult(resultData);
                        log("Executed SELECT query. " + resultData.rows().size() + " rows returned.");
                    }
            );
        } else {
            runBackgroundTask(
                    () -> {
                        try {
                            return dbHelper.executeUpdateOrDelete(sql);
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    },
                    rowsAffected -> {
                        Alert info = new Alert(Alert.AlertType.INFORMATION);
                        info.setTitle("Execution Successful");
                        info.setHeaderText("The command executed successfully.");
                        info.setContentText(rowsAffected + " row(s) were affected.");
                        info.showAndWait();
                        log("Executed non-query command. " + rowsAffected + " row(s) affected.");
                        refreshTableList();
                    }
            );
        }
    }

    private void handleExecuteSqlFromFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open SQL Script File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQL Files", "*.sql", "*.txt"));
        File file = fileChooser.showOpenDialog(primaryStage);
        if (file != null) {
            runBackgroundTask(
                    () -> {
                        try {
                            String content = new String(Files.readAllBytes(file.toPath()));
                            String[] statements = content.split(";(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                            int successCount = 0;
                            int failCount = 0;
                            for (String stmt : statements) {
                                if (stmt.trim().isEmpty()) continue;
                                try {
                                    dbHelper.executeUpdateOrDelete(stmt);
                                    successCount++;
                                } catch (SQLException e) {
                                    failCount++;
                                    log("Error in script '" + file.getName() + "': " + e.getMessage());
                                }
                            }
                            return "Executed script '" + file.getName() + "'. Success: " + successCount + ", Failed: " + failCount + ".";
                        } catch (IOException e) {
                            throw new RuntimeException("Could not read file: " + e.getMessage(), e);
                        }
                    },
                    resultMessage -> {
                        log(resultMessage);
                        refreshTableList();
                    }
            );
        }
    }
    //</editor-fold>

    //<editor-fold desc="Utility Methods">
    private String getSelectedTable() {
        String selected = tableListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("No Table Selected", "You must select a table from the list on the left.", null);
            return null;
        }
        return selected;
    }

    private void displayQueryResult(DatabaseHelper.TableData tableData) {
        dataTableView.getColumns().clear();
        dataTableView.getItems().clear();
        rowSelectionMap.clear();
        currentTableLabel.setText("Custom Query Result");
        actionTabPane.getSelectionModel().select(actionTabPane.getTabs().size() - 1);

        for (int i = 0; i < tableData.headers().size(); i++) {
            final int colIndex = i;
            TableColumn<ObservableList<String>, String> column = new TableColumn<>(tableData.headers().get(i));
            column.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().get(colIndex)));
            column.setEditable(false);
            dataTableView.getColumns().add(column);
        }
        dataTableView.setItems(tableData.rows());
    }

    private void log(String message) {
        logArea.appendText(message + "\n");
    }

    private void showError(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        if (content != null) {
            alert.setContentText(content);
        }
        alert.showAndWait();
    }
    //</editor-fold>

    //<editor-fold desc="Static Nested Class ColumnDefinition">
    public static class ColumnDefinition {
        private final SimpleStringProperty name;
        private final SimpleStringProperty dataType;
        private final SimpleStringProperty size;
        private final SimpleBooleanProperty primaryKey;
        private final SimpleBooleanProperty notNull;
        private final SimpleBooleanProperty autoIncrement;
        // --- New Properties ---
        private final SimpleBooleanProperty unsigned;
        private final SimpleBooleanProperty unique;
        private final SimpleStringProperty defaultValue;
        private final SimpleStringProperty comment;

        public ColumnDefinition(String name, String dataType, String size, boolean pk, boolean nn, boolean ai,
                                  boolean unsigned, boolean unique, String defaultValue, String comment) {
            this.name = new SimpleStringProperty(name);
            this.dataType = new SimpleStringProperty(dataType);
            this.size = new SimpleStringProperty(size);
            this.primaryKey = new SimpleBooleanProperty(pk);
            this.notNull = new SimpleBooleanProperty(nn);
            this.autoIncrement = new SimpleBooleanProperty(ai);
            this.unsigned = new SimpleBooleanProperty(unsigned);
            this.unique = new SimpleBooleanProperty(unique);
            this.defaultValue = new SimpleStringProperty(defaultValue);
            this.comment = new SimpleStringProperty(comment);
        }

        // Getters, Setters, and Property methods for all fields
        public String getName() { return name.get(); }
        public void setName(String name) { this.name.set(name); }
        public SimpleStringProperty nameProperty() { return name; }
        public String getDataType() { return dataType.get(); }
        public void setDataType(String dataType) { this.dataType.set(dataType); }
        public SimpleStringProperty dataTypeProperty() { return dataType; }
        public String getSize() { return size.get(); }
        public void setSize(String size) { this.size.set(size); }
        public SimpleStringProperty sizeProperty() { return size; }
        public boolean isPrimaryKey() { return primaryKey.get(); }
        public SimpleBooleanProperty primaryKeyProperty() { return primaryKey; }
        public boolean isNotNull() { return notNull.get(); }
        public SimpleBooleanProperty notNullProperty() { return notNull; }
        public boolean isAutoIncrement() { return autoIncrement.get(); }
        public SimpleBooleanProperty autoIncrementProperty() { return autoIncrement; }
        public boolean isUnsigned() { return unsigned.get(); }
        public SimpleBooleanProperty unsignedProperty() { return unsigned; }
        public boolean isUnique() { return unique.get(); }
        public SimpleBooleanProperty uniqueProperty() { return unique; }
        public String getDefaultValue() { return defaultValue.get(); }
        public SimpleStringProperty defaultValueProperty() { return defaultValue; }
        public void setDefaultValue(String value) { this.defaultValue.set(value); }
        public String getComment() { return comment.get(); }
        public SimpleStringProperty commentProperty() { return comment; }
        public void setComment(String value) { this.comment.set(value); }
    }
    //</editor-fold>
}