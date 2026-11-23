-- ============================================================================
-- Online Examination Management System - Sample Users & 100 MCQ Question Bank
-- ============================================================================
-- Adds:
--   - Default Admin account:   admin / admin123
--   - Demo Student accounts:   student1 / pass123, student2 / pass123
--   - 25 unique MCQ questions per subject (Java, DBMS, SQL, Aptitude = 100 total)
--
-- Safe to run multiple times (idempotent: INSERT IGNORE / NOT EXISTS).
-- Allows demonstrating 20-question random selection out of 25 questions/subject.
-- ============================================================================

USE online_exam_db;

-- Default accounts
INSERT IGNORE INTO users (username, password, role) VALUES
    ('admin', 'admin123', 'admin'),
    ('student1', 'pass123', 'student'),
    ('student2', 'pass123', 'student');

-- Ensure default subjects exist
INSERT IGNORE INTO subjects (id, subject_name) VALUES
    (1, 'Java'),
    (2, 'DBMS'),
    (3, 'SQL'),
    (4, 'Aptitude');

-- Temporary helper table for idempotent question seeding
CREATE TEMPORARY TABLE tmp_seed_questions (
    subject_id INT NOT NULL,
    question TEXT NOT NULL,
    option1 VARCHAR(500) NOT NULL,
    option2 VARCHAR(500) NOT NULL,
    option3 VARCHAR(500) NOT NULL,
    option4 VARCHAR(500) NOT NULL,
    correct_option INT NOT NULL
);

-- ==================== SUBJECT 1: JAVA (25 Questions) ====================
INSERT INTO tmp_seed_questions VALUES
(1, 'Which component is responsible for executing Java bytecode?', 'JDK', 'JVM', 'Javadoc', 'JAR', 2),
(1, 'What is the size of an int data type in Java?', '16 bits', '32 bits', '64 bits', 'Platform dependent', 2),
(1, 'Which keyword is used to inherit a class in Java?', 'implements', 'inherits', 'extends', 'super', 3),
(1, 'Which collection class does NOT allow duplicate elements?', 'ArrayList', 'LinkedList', 'HashSet', 'Vector', 3),
(1, 'What is the default value of a boolean instance variable in Java?', 'true', 'false', 'null', '0', 2),
(1, 'Which keyword prevents a method from being overridden in subclasses?', 'static', 'private', 'final', 'abstract', 3),
(1, 'Which exception is thrown when dividing an integer by zero in Java?', 'NullPointerException', 'ArithmeticException', 'NumberFormatException', 'IllegalArgumentException', 2),
(1, 'Which interface must a class implement to be used in a try-with-resources block?', 'Serializable', 'Comparable', 'AutoCloseable', 'Cloneable', 3),
(1, 'Which package contains the Swing GUI classes in Java?', 'java.awt', 'javax.swing', 'java.swing', 'javax.gui', 2),
(1, 'Which method is the entry point of a standalone Java application?', 'start()', 'run()', 'main(String[] args)', 'init()', 3),
(1, 'In Java, which String class is mutable and NOT synchronized?', 'String', 'StringBuffer', 'StringBuilder', 'CharSequence', 3),
(1, 'What does the keyword super refer to in Java?', 'Current class object', 'Immediate parent class object', 'Static class context', 'Root package', 2),
(1, 'Which JDBC interface is preferred to prevent SQL Injection attacks?', 'Statement', 'PreparedStatement', 'DriverManager', 'ResultSet', 2),
(1, 'Which method commits a JDBC transaction manually?', 'connection.save()', 'connection.commit()', 'statement.commit()', 'connection.flush()', 2),
(1, 'What is the return type of the equals() method in java.lang.Object?', 'int', 'Object', 'boolean', 'String', 3),
(1, 'Which Map implementation preserves insertion order in Java?', 'HashMap', 'TreeMap', 'LinkedHashMap', 'Hashtable', 3),
(1, 'Which keyword is used to explicitly throw an exception in Java?', 'throws', 'throw', 'catch', 'raise', 2),
(1, 'Which access modifier makes a member visible only within the same class?', 'default', 'protected', 'private', 'public', 3),
(1, 'What happens when connection.setAutoCommit(false) is called in JDBC?', 'Closes the connection', 'Disables SQL queries', 'Starts manual transaction management', 'Deletes uncommitted rows', 3),
(1, 'Which Swing layout manager divides a container into NORTH, SOUTH, EAST, WEST, and CENTER?', 'FlowLayout', 'GridLayout', 'BorderLayout', 'BoxLayout', 3),
(1, 'Which method starts a new thread of execution in Java?', 'run()', 'start()', 'execute()', 'begin()', 2),
(1, 'What is the superclass of all classes in Java?', 'java.lang.Class', 'java.lang.System', 'java.lang.Object', 'java.lang.Root', 3),
(1, 'Which Java collection stores elements in sorted ascending order automatically?', 'HashSet', 'TreeSet', 'ArrayList', 'LinkedHashSet', 2),
(1, 'Which JDBC method retrieves auto-generated primary keys after an INSERT?', 'getPrimaryKeys()', 'getGeneratedKeys()', 'getLastInsertId()', 'getResultKeys()', 2),
(1, 'Which block in exception handling always executes regardless of whether an exception occurred?', 'try', 'catch', 'finally', 'throw', 3);

-- ==================== SUBJECT 2: DBMS (25 Questions) ====================
INSERT INTO tmp_seed_questions VALUES
(2, 'What does ACID stand for in DBMS transactions?', 'Atomicity, Consistency, Isolation, Durability', 'Accuracy, Concurrency, Integrity, Durability', 'Atomicity, Concurrency, Isolation, Dependency', 'Availability, Consistency, Integrity, Durability', 1),
(2, 'Which normal form eliminates partial dependency on a composite primary key?', '1NF', '2NF', '3NF', 'BCNF', 2),
(2, 'Which normal form eliminates transitive dependency?', '1NF', '2NF', '3NF', '4NF', 3),
(2, 'A key that uniquely identifies every tuple in a relation and cannot contain NULL is called:', 'Foreign Key', 'Primary Key', 'Alternate Key', 'Secondary Key', 2),
(2, 'Which property guarantees that a transaction either completes fully or leaves no partial effect?', 'Consistency', 'Isolation', 'Atomicity', 'Durability', 3),
(2, 'What enforces referential integrity between two relational tables?', 'Check Constraint', 'Foreign Key', 'Unique Key', 'Index', 2),
(2, 'Which MySQL storage engine supports transactions and foreign key constraints?', 'MyISAM', 'MEMORY', 'InnoDB', 'CSV', 3),
(2, 'In an ER diagram, an ellipse represents which concept?', 'Entity', 'Relationship', 'Attribute', 'Weak Entity', 3),
(2, 'Which concurrency problem occurs when a transaction reads uncommitted data from another transaction?', 'Lost Update', 'Dirty Read', 'Phantom Read', 'Deadlock', 2),
(2, 'Which data structure is most commonly used for database indexing in RDBMS?', 'Binary Heap', 'B+ Tree', 'Stack', 'Linked List', 2),
(2, 'What is a minimal superkey called in relational database theory?', 'Foreign Key', 'Candidate Key', 'Composite Key', 'Partial Key', 2),
(2, 'Which level of database abstraction describes how data is physically stored on disk?', 'View Level', 'Logical Level', 'Physical Level', 'External Level', 3),
(2, 'Which anomaly occurs when deleting one fact unintentionally removes another unrelated fact?', 'Insertion Anomaly', 'Update Anomaly', 'Deletion Anomaly', 'Join Anomaly', 3),
(2, 'What ensures that committed transaction results survive a system crash?', 'Atomicity', 'Isolation', 'Durability', 'Serializability', 3),
(2, 'In relational algebra, which operation selects specific columns from a relation?', 'Selection (sigma)', 'Projection (pi)', 'Union', 'Cartesian Product', 2),
(2, 'What is a deadlock in DBMS?', 'A corrupted index', 'Two or more transactions waiting indefinitely for locks held by each other', 'A failed backup', 'A full transaction log', 2),
(2, 'Which normal form requires every non-trivial functional dependency X -> Y to have X as a superkey?', '2NF', '3NF', 'BCNF', '1NF', 3),
(2, 'What is a virtual table defined by a stored query called?', 'Trigger', 'View', 'Cursor', 'Procedure', 2),
(2, 'Which isolation level prevents dirty reads, non-repeatable reads, and phantom reads?', 'READ UNCOMMITTED', 'READ COMMITTED', 'REPEATABLE READ', 'SERIALIZABLE', 4),
(2, 'What does cardinality in an ER model describe?', 'Number of columns', 'Number of entities associated via a relationship', 'Size of a database file', 'Number of indexes', 2),
(2, 'Which protocol uses growing and shrinking lock phases to guarantee conflict serializability?', 'Timestamp Ordering', 'Two-Phase Locking (2PL)', 'Round Robin', 'Hash Partitioning', 2),
(2, 'A weak entity set is identified using its partial key combined with:', 'Another weak entity', 'The primary key of its owner entity', 'A random UUID', 'A foreign index only', 2),
(2, 'What is functional dependency A -> B?', 'A is greater than B', 'Value of A uniquely determines value of B', 'B uniquely determines A', 'A and B are identical tables', 2),
(2, 'Which recovery technique writes log records to disk before modifying actual database pages?', 'Shadow Paging', 'Write-Ahead Logging (WAL)', 'Sharding', 'Replication', 2),
(2, 'What is denormalization used for in databases?', 'Increasing redundancy to improve read performance', 'Removing all primary keys', 'Converting tables to text files', 'Enforcing 5NF', 1);

-- ==================== SUBJECT 3: SQL (25 Questions) ====================
INSERT INTO tmp_seed_questions VALUES
(3, 'Which SQL clause is used to filter groups created by GROUP BY?', 'WHERE', 'HAVING', 'ORDER BY', 'FILTER', 2),
(3, 'Which SQL keyword removes duplicate rows from a SELECT result set?', 'UNIQUE', 'DISTINCT', 'SINGLE', 'NODUP', 2),
(3, 'Which SQL command is a DDL (Data Definition Language) statement?', 'INSERT', 'UPDATE', 'CREATE', 'SELECT', 3),
(3, 'Which JOIN returns all rows from the left table and matched rows from the right table?', 'INNER JOIN', 'LEFT JOIN', 'RIGHT JOIN', 'CROSS JOIN', 2),
(3, 'Which aggregate function returns the number of rows in a query result?', 'SUM()', 'TOTAL()', 'COUNT()', 'LENGTH()', 3),
(3, 'Which SQL operator is used for pattern matching with % and _ wildcards?', 'MATCH', 'LIKE', 'SIMILAR', 'CONTAINS', 2),
(3, 'Which command permanently saves changes made in the current SQL transaction?', 'SAVE', 'APPLY', 'COMMIT', 'STORE', 3),
(3, 'Which command undoes uncommitted changes in the current SQL transaction?', 'UNDO', 'REVERT', 'ROLLBACK', 'CANCEL', 3),
(3, 'What is the difference between DELETE and TRUNCATE in SQL?', 'DELETE is DDL; TRUNCATE is DML', 'DELETE can use WHERE and is row-by-row; TRUNCATE removes all rows quickly', 'TRUNCATE can use WHERE', 'There is no difference', 2),
(3, 'Which SQL clause sorts query results in ascending or descending order?', 'SORT BY', 'GROUP BY', 'ORDER BY', 'ALIGN BY', 3),
(3, 'How do you check whether a column value is NULL in SQL?', 'column = NULL', 'column == NULL', 'column IS NULL', 'NULL(column)', 3),
(3, 'Which SQL constraint ensures all values in a column are different?', 'NOT NULL', 'UNIQUE', 'DEFAULT', 'CHECK', 2),
(3, 'Which SQL function returns the highest value in a numeric column?', 'TOP()', 'HIGH()', 'MAX()', 'GREATEST()', 3),
(3, 'Which SQL statement adds a new column to an existing table?', 'MODIFY TABLE', 'UPDATE TABLE', 'ALTER TABLE ... ADD COLUMN', 'INSERT COLUMN', 3),
(3, 'What does UNION ALL do compared to UNION?', 'Removes duplicates', 'Keeps all duplicate rows and is faster', 'Sorts rows descending', 'Performs a cross join', 2),
(3, 'Which clause restricts the maximum number of rows returned in MySQL?', 'TOP', 'ROWNUM', 'LIMIT', 'FETCH FIRST ONLY', 3),
(3, 'Which SQL function calculates the average value of a numeric column?', 'MEAN()', 'AVG()', 'AVERAGE()', 'MEDIAN()', 2),
(3, 'Which keyword defines an alias for a column or table in a SQL query?', 'ALIAS', 'AS', 'NAME', 'TO', 2),
(3, 'Which SQL command completely removes a table and its structure from the database?', 'DELETE TABLE', 'CLEAR TABLE', 'DROP TABLE', 'REMOVE TABLE', 3),
(3, 'In SQL, what does ON DELETE CASCADE on a foreign key do?', 'Blocks parent deletion', 'Automatically deletes matching child rows when parent row is deleted', 'Sets parent ID to 0', 'Deletes the entire database', 2),
(3, 'Which operator checks if a value matches any value in a list or subquery?', 'BETWEEN', 'IN', 'EXISTS ONLY', 'WITHIN', 2),
(3, 'Which MySQL function returns the current date and time?', 'TODAY()', 'NOW()', 'TIME()', 'DATEONLY()', 2),
(3, 'Which SQL clause executes first logically in a SELECT query?', 'SELECT', 'ORDER BY', 'FROM', 'HAVING', 3),
(3, 'What does BETWEEN 10 AND 20 include in SQL?', 'Only 11 to 19', '10 to 20 inclusive', '10 to 19 only', '11 to 20 only', 2),
(3, 'Which SQL statement grants privileges to a database user?', 'ALLOW', 'PERMIT', 'GRANT', 'ACCESS', 3);

-- ==================== SUBJECT 4: APTITUDE (25 Questions) ====================
INSERT INTO tmp_seed_questions VALUES
(4, 'If a train travels 180 km in 3 hours, what is its average speed?', '50 km/h', '60 km/h', '65 km/h', '75 km/h', 2),
(4, 'What is 25% of 320?', '60', '75', '80', '90', 3),
(4, 'Find the next number in the series: 2, 6, 12, 20, 30, ?', '40', '42', '44', '46', 2),
(4, 'A shopkeeper buys an item for 400 and sells it for 500. What is the profit percentage?', '20%', '25%', '30%', '15%', 2),
(4, 'If 6 workers can finish a task in 10 days, how many days will 12 workers take?', '5 days', '6 days', '8 days', '20 days', 1),
(4, 'What is the simple interest on 5000 at 8% per annum for 2 years?', '600', '750', '800', '1000', 3),
(4, 'The ratio of boys to girls in a class is 3:2. If there are 30 boys, how many girls are there?', '15', '18', '20', '25', 3),
(4, 'What is the average of the first five prime numbers (2, 3, 5, 7, 11)?', '5.2', '5.6', '6.0', '6.4', 2),
(4, 'If x + 7 = 19, what is the value of 2x - 4?', '18', '20', '22', '24', 2),
(4, 'A fair six-sided die is rolled once. What is the probability of getting an even number?', '1/6', '1/3', '1/2', '2/3', 3),
(4, 'Find the LCM of 12 and 18.', '24', '36', '48', '72', 2),
(4, 'Find the HCF (GCD) of 36 and 48.', '6', '8', '12', '16', 3),
(4, 'If the perimeter of a square is 36 cm, what is its area?', '64 sq cm', '72 sq cm', '81 sq cm', '90 sq cm', 3),
(4, 'A car covers 90 km at 45 km/h and another 90 km at 90 km/h. What is the average speed?', '60 km/h', '65 km/h', '67.5 km/h', '70 km/h', 1),
(4, 'What is the next term in the series: 3, 9, 27, 81, ?', '162', '216', '243', '324', 3),
(4, 'If a shirt marked at 1000 is sold at a 15% discount, what is the selling price?', '800', '850', '875', '900', 2),
(4, 'How many minutes are there in 2.5 hours?', '120', '135', '150', '165', 3),
(4, 'In a certain code, CAT is written as DBU. How is DOG written?', 'EPH', 'EPI', 'FPH', 'DPH', 1),
(4, 'Pipe A fills a tank in 6 hours and Pipe B fills it in 3 hours. Together, how long do they take?', '1.5 hours', '2 hours', '2.5 hours', '4.5 hours', 2),
(4, 'What is the square root of 1444?', '34', '36', '38', '42', 3),
(4, 'If 40% of a number is 64, what is the number?', '140', '150', '160', '180', 3),
(4, 'Two numbers are in the ratio 4:5 and their sum is 72. What is the larger number?', '32', '36', '40', '45', 3),
(4, 'A clock shows 3:00. What is the angle between the hour hand and the minute hand?', '60 degrees', '90 degrees', '120 degrees', '180 degrees', 2),
(4, 'Find the odd one out: 16, 25, 36, 48, 64.', '16', '36', '48', '64', 3),
(4, 'If today is Monday, what day of the week will it be after 15 days?', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 2);

-- Insert only questions that do not already exist (idempotent)
INSERT INTO questions (subject_id, question, option1, option2, option3, option4, correct_option)
SELECT t.subject_id, t.question, t.option1, t.option2, t.option3, t.option4, t.correct_option
FROM tmp_seed_questions t
WHERE NOT EXISTS (
    SELECT 1 FROM questions q
    WHERE q.subject_id = t.subject_id
      AND q.question COLLATE utf8mb4_unicode_ci = t.question COLLATE utf8mb4_unicode_ci
);

DROP TEMPORARY TABLE tmp_seed_questions;
