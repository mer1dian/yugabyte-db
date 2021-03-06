// Copyright (c) YugaByte, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.  You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the License
// is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
// or implied.  See the License for the specific language governing permissions and limitations
// under the License.
//

package org.yb.pgsql;

import static org.yb.AssertionWrappers.*;

import java.sql.Statement;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.yb.util.YBTestRunnerNonTsanOnly;

@RunWith(YBTestRunnerNonTsanOnly.class)
public class TestPgUniqueConstraint extends BasePgSQLTest {

  @Test
  public void indexIsManaged_unnamed() throws Exception {
    try (Statement stmt = connection.createStatement()) {
      stmt.execute("CREATE TABLE test("
          + "i1 int UNIQUE, "
          + "i2 int, "
          + "UNIQUE(i1, i2)"
          + ")");

      // Check that indexes has been created
      assertQuery(stmt,
          "SELECT indexname FROM pg_indexes WHERE tablename='test'",
          new Row("test_i1_key"),
          new Row("test_i1_i2_key"));

      // They cannot be dropped manually...
      runInvalidQuery(stmt, "DROP INDEX test_i1_key", "cannot drop index");
      runInvalidQuery(stmt, "DROP INDEX test_i1_i2_key", "cannot drop index");

      // But are dropped automatically when constraint is dropped
      stmt.execute("ALTER TABLE test DROP CONSTRAINT test_i1_key");
      stmt.execute("ALTER TABLE test DROP CONSTRAINT test_i1_i2_key");
      assertNoRows(stmt, "SELECT indexname FROM pg_indexes WHERE tablename='test'");
    }
  }

  @Test
  public void indexIsManaged_named() throws Exception {
    try (Statement stmt = connection.createStatement()) {
      stmt.execute("CREATE TABLE test("
          + "i1 int CONSTRAINT my_constraint_name_1 UNIQUE, "
          + "i2 int, "
          + "CONSTRAINT my_constraint_name_2 UNIQUE(i1, i2)"
          + ")");

      // Check that indexes has been created
      assertQuery(stmt,
          "SELECT indexname FROM pg_indexes WHERE tablename='test'",
          new Row("my_constraint_name_1"),
          new Row("my_constraint_name_2"));

      // They cannot be dropped manually...
      runInvalidQuery(stmt, "DROP INDEX my_constraint_name_1", "cannot drop index");
      runInvalidQuery(stmt, "DROP INDEX my_constraint_name_2", "cannot drop index");

      // But are dropped automatically when constraint is dropped
      stmt.execute("ALTER TABLE test DROP CONSTRAINT my_constraint_name_1");
      stmt.execute("ALTER TABLE test DROP CONSTRAINT my_constraint_name_2");
      assertNoRows(stmt, "SELECT indexname FROM pg_indexes WHERE tablename='test'");
    }
  }

  @Test
  public void indexIsDroppedWithTheTable() throws Exception {
    try (Statement stmt = connection.createStatement()) {
      stmt.execute("CREATE TABLE test("
          + "i1 int UNIQUE, "
          + "i2 int, "
          + "UNIQUE(i1, i2)"
          + ")");

      // Check that indexes has been created
      assertQuery(stmt,
          "SELECT indexname FROM pg_indexes WHERE tablename='test'",
          new Row("test_i1_key"),
          new Row("test_i1_i2_key"));

      // But are dropped automatically when table is dropped
      stmt.execute("DROP TABLE test;");
      assertNoRows(stmt, "SELECT indexname FROM pg_indexes WHERE tablename='test'");
      runInvalidQuery(stmt, "DROP INDEX test_i1_key", "does not exist");
      runInvalidQuery(stmt, "DROP INDEX test_i1_i2_key", "does not exist");
    }
  }

  @Test
  public void guaranteesUniqueness() throws Exception {
    try (Statement stmt = connection.createStatement()) {
      stmt.execute("CREATE TABLE test("
          + "i1 int UNIQUE, "
          + "i2 int, "
          + "i3 int, "
          + "UNIQUE(i2, i3)"
          + ")");

      stmt.execute("INSERT INTO test(i1, i2, i3) VALUES (1, 1, 1)");
      runInvalidQuery(stmt, "INSERT INTO test(i1, i2, i3) VALUES (1, 2, 2)",
          "duplicate key value violates unique constraint \"test_i1_key\"");
      runInvalidQuery(stmt, "INSERT INTO test(i1, i2, i3) VALUES (2, 1, 1)",
          "duplicate key value violates unique constraint \"test_i2_i3_key\"");
      runInvalidQuery(stmt, "INSERT INTO test(i1, i2, i3) VALUES (1, 1, 1)",
          "duplicate key value violates unique constraint \"test_i1_key\"");
      stmt.execute("INSERT INTO test(i1, i2, i3) VALUES (2, 2, 2)");
      assertQuery(stmt,
          "SELECT * FROM test ORDER BY i1, i2, i3",
          new Row(1, 1, 1),
          new Row(2, 2, 2));
    }
  }

  @Ignore // TODO: Enable after #1058
  public void multipleNullsAreAllowed() throws Exception {
    try (Statement stmt = connection.createStatement()) {
      // Singular column UNIQUE constraint
      stmt.execute("CREATE TABLE test1("
          + "i int UNIQUE"
          + ")");
      stmt.execute("INSERT INTO test1 VALUES (NULL)");
      stmt.execute("INSERT INTO test1 VALUES (NULL)");
      assertQuery(stmt,
          "SELECT * FROM test1",
          new Row(null),
          new Row(null));

      // Multi-column UNIQUE constraint
      stmt.execute("CREATE TABLE test2("
          + "i2 int, "
          + "i3 int, "
          + "UNIQUE(i2, i3)"
          + ")");
      stmt.execute("INSERT INTO test2 VALUES (1, 1)");
      stmt.execute("INSERT INTO test2 VALUES (NULL, 1)");
      stmt.execute("INSERT INTO test2 VALUES (NULL, 1)");
      stmt.execute("INSERT INTO test2 VALUES (1, NULL)");
      stmt.execute("INSERT INTO test2 VALUES (1, NULL)");
      runInvalidQuery(stmt, "INSERT INTO test2 VALUES (1, 1)", "duplicate");
      assertQuery(stmt,
          "SELECT * FROM test2 ORDER BY i2, i3",
          new Row(1, 1),
          new Row(1, null),
          new Row(1, null),
          new Row(null, 1),
          new Row(null, 1));
    }
  }

  @Test
  public void constraintDropAllowsDuplicates() throws Exception {
    try (Statement stmt = connection.createStatement()) {
      stmt.execute("CREATE TABLE test("
          + "i1 int UNIQUE, "
          + "i2 int, "
          + "i3 int, "
          + "UNIQUE(i2, i3)"
          + ")");

      stmt.execute("INSERT INTO test(i1, i2, i3) VALUES (1, 1, 1)");

      stmt.execute("ALTER TABLE test DROP CONSTRAINT test_i1_key");
      stmt.execute("ALTER TABLE test DROP CONSTRAINT test_i2_i3_key");

      stmt.execute("INSERT INTO test(i1, i2, i3) VALUES (1, 2, 2)");
      stmt.execute("INSERT INTO test(i1, i2, i3) VALUES (2, 1, 1)");
      stmt.execute("INSERT INTO test(i1, i2, i3) VALUES (1, 1, 1)");
      assertQuery(stmt,
          "SELECT * FROM test ORDER BY i1, i2, i3",
          new Row(1, 1, 1),
          new Row(1, 1, 1),
          new Row(1, 2, 2),
          new Row(2, 1, 1));
    }
  }

  @Ignore // TODO: Enable after #1124
  public void addConstraint() throws Exception {
    try (Statement stmt = connection.createStatement()) {
      stmt.execute("CREATE TABLE test("
          + "i1 int, "
          + "i2 int"
          + ")");
      stmt.execute("ALTER TABLE test ADD CONSTRAINT test_constr UNIQUE (i1, i2)");

      // Index is created
      assertQuery(stmt,
          "SELECT indexname FROM pg_indexes WHERE tablename='test'",
          new Row("test_constr"));

      // Uniqueness should work
      stmt.execute("INSERT INTO test(i1, i2) VALUES (1, 1)");
      stmt.execute("INSERT INTO test(i1, i2) VALUES (2, 2)");
      runInvalidQuery(stmt, "INSERT INTO test(i1, i2) VALUES (1, 1)", "duplicate");
      assertQuery(stmt,
          "SELECT * FROM test ORDER BY i1, i2",
          new Row(1, 1),
          new Row(2, 2));

      // Index should only be dropped when dropping constraint
      runInvalidQuery(stmt, "DROP INDEX test_constr", "cannot drop index");
      stmt.execute("ALTER TABLE test DROP CONSTRAINT my_constraint_name_2");
      assertNoRows(stmt, "SELECT indexname FROM pg_indexes WHERE tablename='test'");

      // Uniqueness is no longer enforced
      stmt.execute("INSERT INTO test(i1, i2) VALUES (1, 1)");
      assertQuery(stmt,
          "SELECT * FROM test ORDER BY i1, i2",
          new Row(1, 1),
          new Row(1, 1),
          new Row(2, 2));
    }
  }

  @Ignore // TODO: Enable after #1124
  public void addConstraintUsingExistingIndex() throws Exception {
    try (Statement stmt = connection.createStatement()) {
      stmt.execute("CREATE TABLE test("
          + "i1 int, "
          + "i2 int"
          + ")");
      stmt.execute("CREATE UNIQUE INDEX test_idx ON test (i1, i2)");
      // This will also rename index "test_idx" to "test_constr"
      stmt.execute("ALTER TABLE test ADD CONSTRAINT test_constr UNIQUE USING INDEX test_idx");

      // Index should be renamed
      assertQuery(stmt,
          "SELECT indexname FROM pg_indexes WHERE tablename='test'",
          new Row("test_constr"));

      // Uniqueness should work
      stmt.execute("INSERT INTO test(i1, i2) VALUES (1, 1)");
      stmt.execute("INSERT INTO test(i1, i2) VALUES (2, 2)");
      runInvalidQuery(stmt, "INSERT INTO test(i1, i2) VALUES (1, 1)", "duplicate");
      assertQuery(stmt,
          "SELECT * FROM test ORDER BY i1, i2",
          new Row(1, 1),
          new Row(2, 2));

      // Index should only be dropped when dropping constraint
      runInvalidQuery(stmt, "DROP INDEX test_constr", "cannot drop index");
      stmt.execute("ALTER TABLE test DROP CONSTRAINT my_constraint_name_2");
      assertNoRows(stmt, "SELECT indexname FROM pg_indexes WHERE tablename='test'");

      // Uniqueness is no longer enforced
      stmt.execute("INSERT INTO test(i1, i2) VALUES (1, 1)");
      assertQuery(stmt,
          "SELECT * FROM test ORDER BY i1, i2",
          new Row(1, 1),
          new Row(1, 1),
          new Row(2, 2));
    }
  }
}
